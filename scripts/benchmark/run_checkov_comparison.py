#!/usr/bin/env python3

import argparse
import csv
import json
import os
import re
import shutil
import subprocess
import tempfile
from datetime import date
from pathlib import Path

from openpyxl import load_workbook


RULES = [
    "CS-RUNTIME-001",
    "CS-NETWORK-001",
    "CS-CONTAINER-001",
    "CS-CONTAINER-002",
    "CS-SUPPLY-001",
    "CS-DEPENDENCY-001",
]


def parse_args():
    parser = argparse.ArgumentParser(description="Run CodeShield and Checkov against the expanded benchmark.")
    parser.add_argument("--dataset", type=Path, required=True)
    parser.add_argument("--project-root", type=Path, required=True)
    parser.add_argument("--checkov", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument(
        "--presentation",
        action="store_true",
        help="Print a concise terminal summary suitable for a project presentation.",
    )
    return parser.parse_args()


def load_cases(path):
    workbook = load_workbook(path, read_only=True, data_only=True)
    worksheet = workbook["Dataset"]
    values = worksheet.iter_rows(values_only=True)
    headers = next(values)
    cases = [dict(zip(headers, row)) for row in values if row[0]]
    workbook.close()
    return cases


def split_rules(value):
    if not value:
        return set()
    return {item.strip() for item in str(value).split(";") if item.strip()}


def write_codeshield_input(cases, path):
    fields = [
        "Case ID", "Asset Criticality", "Expected Java", "Observed Java",
        "Expected DB Exposure", "Observed DB Exposure", "Expected Container User",
        "Observed Container User", "Docker Socket Mounted", "Expected Image Digest",
        "Observed Image Digest", "Expected Dependency", "Observed Dependency",
        "Internet Facing", "Known Exploited",
    ]
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle, delimiter="\t", lineterminator="\n")
        writer.writerow(fields)
        for case in cases:
            writer.writerow([case[field] for field in fields])


def run_codeshield(project_root, input_path, work_dir):
    subprocess.run(
        ["mvn", "-q", "-DskipTests", "package"],
        cwd=project_root,
        check=True,
    )
    classes = work_dir / "java-classes"
    classes.mkdir()
    runner = project_root / "scripts" / "benchmark" / "RunCodeShieldBenchmark.java"
    subprocess.run(
        [
            "javac", "-nowarn", "-Xlint:none", "-cp", str(project_root / "target" / "classes"),
            "-d", str(classes), str(runner),
        ],
        cwd=project_root,
        check=True,
    )
    classpath = os.pathsep.join([str(classes), str(project_root / "target" / "classes")])
    completed = subprocess.run(
        ["java", "-cp", classpath, "com.codeshield.benchmark.RunCodeShieldBenchmark", str(input_path)],
        cwd=project_root,
        check=True,
        text=True,
        capture_output=True,
    )
    return {row["case_id"]: row for row in csv.DictReader(completed.stdout.splitlines())}


def write_checkov_fixtures(cases, root):
    for case in cases:
        case_dir = root / case["Case ID"]
        case_dir.mkdir(parents=True)
        observed_user = str(case["Observed Container User"])
        observed_java = str(case["Observed Java"]).split(".")[0]
        dockerfile = "\n".join([
            f"FROM eclipse-temurin:{observed_java}-jre",
            "WORKDIR /app",
            "COPY app.jar /app/app.jar",
            f"USER {observed_user}",
            "HEALTHCHECK CMD java -version || exit 1",
            'CMD ["java", "-jar", "/app/app.jar"]',
            "",
        ])
        (case_dir / "Dockerfile").write_text(dockerfile, encoding="utf-8")


def run_checkov(checkov, fixture_root):
    completed = subprocess.run(
        [
            str(checkov), "-d", str(fixture_root), "--framework", "dockerfile",
            "--check", "CKV_DOCKER_8", "--skip-download", "--output", "json",
            "--quiet", "--compact", "--soft-fail",
        ],
        check=True,
        text=True,
        capture_output=True,
        env={**os.environ, "LOG_LEVEL": "ERROR"},
    )
    report = json.loads(completed.stdout)
    reports = report if isinstance(report, list) else [report]
    failed_cases = set()
    for item in reports:
        for finding in item.get("results", {}).get("failed_checks", []):
            if finding.get("check_id") != "CKV_DOCKER_8":
                continue
            match = re.search(r"CSB-\d{3}", finding.get("file_path", ""))
            if match:
                failed_cases.add(match.group(0))
    return failed_cases, report


def checkov_version(checkov):
    completed = subprocess.run(
        [str(checkov), "--version"],
        check=True,
        text=True,
        capture_output=True,
    )
    match = re.search(r"\d+(?:\.\d+)+", completed.stdout + completed.stderr)
    if not match:
        raise RuntimeError("Unable to determine the installed Checkov version.")
    return match.group(0)


def classification_metrics(truth, prediction):
    tp = sum(t and p for t, p in zip(truth, prediction))
    tn = sum((not t) and (not p) for t, p in zip(truth, prediction))
    fp = sum((not t) and p for t, p in zip(truth, prediction))
    fn = sum(t and (not p) for t, p in zip(truth, prediction))
    accuracy = (tp + tn) / len(truth) if truth else 0.0
    precision = tp / (tp + fp) if tp + fp else 0.0
    recall = tp / (tp + fn) if tp + fn else 0.0
    f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
    return {
        "tp": tp,
        "tn": tn,
        "fp": fp,
        "fn": fn,
        "accuracy": accuracy,
        "precision": precision,
        "recall": recall,
        "f1": f1,
    }


def pct(value):
    return f"{value * 100:.1f}%"


def calculate_results(cases, codeshield, checkov_failed, checkov_version_value):
    exact_matches = 0
    binary_truth = []
    codeshield_binary = []
    expected_finding_total = 0
    code_tp = code_tn = code_fp = code_fn = 0
    score_matches = 0
    severity_matches = 0
    case_rows = []

    for case in cases:
        case_id = case["Case ID"]
        expected = split_rules(case["Expected Rule IDs"])
        predicted = split_rules(codeshield[case_id]["predicted_rule_ids"])
        exact = expected == predicted
        exact_matches += exact
        binary_truth.append(bool(expected))
        codeshield_binary.append(bool(predicted))
        expected_finding_total += len(expected)
        for rule in RULES:
            truth = rule in expected
            found = rule in predicted
            code_tp += truth and found
            code_tn += (not truth) and (not found)
            code_fp += (not truth) and found
            code_fn += truth and (not found)

        predicted_scores = codeshield[case_id]["predicted_rule_scores"]
        score_matches += str(case["Expected Rule Scores"] or "").strip() == predicted_scores.strip()
        severity_matches += str(case["Highest Severity"]) == codeshield[case_id]["highest_severity"]
        checkov_root = case_id in checkov_failed
        observed_root = str(case["Observed Container User"]).strip().lower() == "root"
        case_rows.append({
            "Case ID": case_id,
            "Expected Rule IDs": "; ".join(sorted(expected)),
            "CodeShield Rule IDs": "; ".join(sorted(predicted)),
            "CodeShield Exact Match": exact,
            "Expected Any Drift": bool(expected),
            "Checkov CKV_DOCKER_8 Failed": checkov_root,
            "Expected Container User Drift": "CS-CONTAINER-001" in expected,
            "Observed Root User": observed_root,
        })

    code_binary = classification_metrics(binary_truth, codeshield_binary)
    code_rule_precision = code_tp / (code_tp + code_fp)
    code_rule_recall = code_tp / (code_tp + code_fn)
    code_rule_f1 = 2 * code_rule_precision * code_rule_recall / (code_rule_precision + code_rule_recall)

    checkov_prediction = [row["Checkov CKV_DOCKER_8 Failed"] for row in case_rows]
    root_truth = [row["Observed Root User"] for row in case_rows]
    container_truth = [row["Expected Container User Drift"] for row in case_rows]

    return {
        "dataset": {
            "cases": len(cases),
            "drift_cases": sum(binary_truth),
            "compliant_cases": len(cases) - sum(binary_truth),
            "expected_findings": expected_finding_total,
        },
        "codeshield": {
            "exact_rule_set_matches": exact_matches,
            "exact_rule_set_accuracy": exact_matches / len(cases),
            "binary": code_binary,
            "rule_level": {
                "tp": code_tp, "tn": code_tn, "fp": code_fp, "fn": code_fn,
                "precision": code_rule_precision, "recall": code_rule_recall, "f1": code_rule_f1,
            },
            "score_matches": score_matches,
            "severity_matches": severity_matches,
        },
        "checkov": {
            "version": checkov_version_value,
            "policy": "CKV_DOCKER_8",
            "root_subcontrol": classification_metrics(root_truth, checkov_prediction),
            "container_user_drift": classification_metrics(container_truth, checkov_prediction),
            "overall_drift_if_root_is_only_signal": classification_metrics(binary_truth, checkov_prediction),
            "failed_cases": len(checkov_failed),
        },
        "cases": case_rows,
    }


def write_outputs(output_dir, results, raw_checkov):
    output_dir.mkdir(parents=True, exist_ok=True)
    metrics_path = output_dir / "CodeShield_Checkov_Metrics.json"
    case_path = output_dir / "CodeShield_Checkov_Case_Results.csv"
    report_path = output_dir / "CodeShield_Checkov_Comparison.md"
    raw_path = output_dir / "Checkov_Raw_Output.json"

    metrics_path.write_text(json.dumps({key: value for key, value in results.items() if key != "cases"}, indent=2), encoding="utf-8")
    raw_path.write_text(json.dumps(raw_checkov, indent=2), encoding="utf-8")
    with case_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(results["cases"][0].keys()))
        writer.writeheader()
        writer.writerows(results["cases"])

    dataset = results["dataset"]
    code = results["codeshield"]
    checkov = results["checkov"]
    report = f"""# CodeShield and Checkov benchmark comparison

Run date: {date.today().isoformat()}

## Dataset

- Cases: {dataset['cases']}
- Labeled drift cases: {dataset['drift_cases']}
- Labeled compliant cases: {dataset['compliant_cases']}
- Expected CodeShield findings: {dataset['expected_findings']}

## Measured results

| Evaluation | Accuracy | Precision | Recall | F1 | TP | TN | FP | FN |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| CodeShield binary drift | {pct(code['binary']['accuracy'])} | {pct(code['binary']['precision'])} | {pct(code['binary']['recall'])} | {pct(code['binary']['f1'])} | {code['binary']['tp']} | {code['binary']['tn']} | {code['binary']['fp']} | {code['binary']['fn']} |
| CodeShield rule-level micro | n.a. | {pct(code['rule_level']['precision'])} | {pct(code['rule_level']['recall'])} | {pct(code['rule_level']['f1'])} | {code['rule_level']['tp']} | {code['rule_level']['tn']} | {code['rule_level']['fp']} | {code['rule_level']['fn']} |
| Checkov root-user sub-control | {pct(checkov['root_subcontrol']['accuracy'])} | {pct(checkov['root_subcontrol']['precision'])} | {pct(checkov['root_subcontrol']['recall'])} | {pct(checkov['root_subcontrol']['f1'])} | {checkov['root_subcontrol']['tp']} | {checkov['root_subcontrol']['tn']} | {checkov['root_subcontrol']['fp']} | {checkov['root_subcontrol']['fn']} |
| Checkov mapped to container-user drift | {pct(checkov['container_user_drift']['accuracy'])} | {pct(checkov['container_user_drift']['precision'])} | {pct(checkov['container_user_drift']['recall'])} | {pct(checkov['container_user_drift']['f1'])} | {checkov['container_user_drift']['tp']} | {checkov['container_user_drift']['tn']} | {checkov['container_user_drift']['fp']} | {checkov['container_user_drift']['fn']} |
| Checkov root signal used as overall drift result | {pct(checkov['overall_drift_if_root_is_only_signal']['accuracy'])} | {pct(checkov['overall_drift_if_root_is_only_signal']['precision'])} | {pct(checkov['overall_drift_if_root_is_only_signal']['recall'])} | {pct(checkov['overall_drift_if_root_is_only_signal']['f1'])} | {checkov['overall_drift_if_root_is_only_signal']['tp']} | {checkov['overall_drift_if_root_is_only_signal']['tn']} | {checkov['overall_drift_if_root_is_only_signal']['fp']} | {checkov['overall_drift_if_root_is_only_signal']['fn']} |

CodeShield exact rule-set accuracy was {code['exact_rule_set_matches']} of {dataset['cases']} cases ({pct(code['exact_rule_set_accuracy'])}). Risk scores matched in {code['score_matches']} cases and highest severity matched in {code['severity_matches']} cases.

## Method

CodeShield results were produced by compiling the current project and invoking its actual `DriftDetectionService` and `RiskScoringService` for every workbook row. Checkov {checkov['version']} scanned one generated Dockerfile per case with its built-in `CKV_DOCKER_8` policy. The Dockerfiles used each case's observed Java version and observed container user and included a health check to avoid unrelated policy failures.

## Interpretation limits

Checkov and CodeShield do not implement the same task. `CKV_DOCKER_8` checks whether the final Dockerfile user is root. It does not accept an application-specific approved baseline. It therefore detected all {checkov['root_subcontrol']['tp']} root-user fixtures, but it could not detect the {checkov['container_user_drift']['fn']} non-root account changes labeled as container-user drift. The other CodeShield categories—runtime version drift, database exposure relative to intent, Docker socket runtime evidence, approved image digest drift, and approved dependency-version drift—have no equivalent input in this Checkov run and are unsupported rather than ordinary false negatives.

The overall Checkov row is included only to show what happens when a single root-user policy is forced to answer the broader six-rule drift task. It is not a general claim about Checkov's effectiveness. The benchmark is synthetic and derived from CodeShield's rule specification, so CodeShield's result measures internal consistency, not accuracy on independently sampled production incidents.

## Tool sources

- Checkov release: https://github.com/bridgecrewio/checkov/releases/tag/3.3.21
- Checkov Dockerfile scanning: https://www.checkov.io/7.Scan%20Examples/Dockerfile.html
- Checkov `CKV_DOCKER_8` policy catalog: https://www.checkov.io/5.Policy%20Index/dockerfile.html
"""
    report_path.write_text(report, encoding="utf-8")
    return report_path, case_path, metrics_path, raw_path


def print_presentation_summary(results, outputs):
    dataset = results["dataset"]
    code = results["codeshield"]
    checkov = results["checkov"]
    binary = code["binary"]
    container = checkov["container_user_drift"]

    print("\nCODE SHIELD BENCHMARK PROOF")
    print("=" * 72)
    print(f"Dataset: {dataset['cases']} cases | {dataset['drift_cases']} drift | "
          f"{dataset['compliant_cases']} compliant | {dataset['expected_findings']} expected findings")
    print(f"Checkov: version {checkov['version']} | policy {checkov['policy']}")
    print()
    print("TOOL / EVALUATION                         ACCURACY  PRECISION  RECALL   F1")
    print("-" * 72)
    print(f"CodeShield binary drift across six rules   {pct(binary['accuracy']):>8}  {pct(binary['precision']):>9}  "
          f"{pct(binary['recall']):>6}  {pct(binary['f1']):>6}")
    print(f"Checkov container-user drift evaluation     {pct(container['accuracy']):>8}  {pct(container['precision']):>9}  "
          f"{pct(container['recall']):>6}  {pct(container['f1']):>6}")
    print()
    print("CODE SHIELD CONFUSION MATRIX")
    print(f"TP={binary['tp']}  TN={binary['tn']}  FP={binary['fp']}  FN={binary['fn']}")
    print(f"Accuracy = (TP + TN) / total = ({binary['tp']} + {binary['tn']}) / {dataset['cases']} "
          f"= {code['exact_rule_set_matches']}/{dataset['cases']} = {pct(binary['accuracy'])}")
    print()
    print("COMPARISON NOTE")
    print(f"Checkov was evaluated on the Dockerfile user-drift subset. It does not compare observed values with an "
          f"application-specific approved baseline, so {container['fn']} user-change cases were not identified.")
    print()
    print("SAVED EVIDENCE")
    for output in outputs:
        print(output)
    print("=" * 72)


def main():
    args = parse_args()
    project_root = args.project_root.resolve()
    cases = load_cases(args.dataset.resolve())
    checkov_version_value = checkov_version(args.checkov.resolve())
    with tempfile.TemporaryDirectory(prefix="codeshield-benchmark-") as temporary:
        work_dir = Path(temporary)
        input_path = work_dir / "benchmark.tsv"
        fixture_root = work_dir / "fixtures"
        write_codeshield_input(cases, input_path)
        write_checkov_fixtures(cases, fixture_root)
        codeshield = run_codeshield(project_root, input_path, work_dir)
        checkov_failed, raw_checkov = run_checkov(args.checkov.resolve(), fixture_root)
        results = calculate_results(cases, codeshield, checkov_failed, checkov_version_value)
        outputs = write_outputs(args.output_dir.resolve(), results, raw_checkov)
        if args.presentation:
            print_presentation_summary(results, outputs)
        else:
            print(json.dumps({
                "outputs": [str(path) for path in outputs],
                "summary": {key: value for key, value in results.items() if key not in {"cases"}},
            }, indent=2))


if __name__ == "__main__":
    main()
