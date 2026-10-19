import fs from "node:fs/promises";
import crypto from "node:crypto";
import { SpreadsheetFile, Workbook } from "@oai/artifact-tool";

const OUTPUT_DIR = "/Users/shauryarawat/Documents/ChatGPT/fog/outputs/codeshield-deliverables";
const OUTPUT_FILE = `${OUTPUT_DIR}/CodeShield_Security_Drift_Benchmark_Dataset_Expanded.xlsx`;
const PREVIEW_DIR = "/private/tmp/codeshield_dataset_expanded_previews";
const FONT = "Arial";
const VERSION = "CSB-2026-002";
const CASE_COUNT = 150;
const ORIGINAL_CASE_COUNT = 120;

const rules = [
  {
    id: "CS-RUNTIME-001",
    name: "Java runtime version drift",
    trigger: "Normalized observed Java major version differs from the approved Java major version.",
    baseRisk: "30",
    baseRiskLogic: () => 30,
    remediation: "Deploy the approved Java runtime or approve the new baseline after compatibility testing.",
  },
  {
    id: "CS-NETWORK-001",
    name: "Internal database is publicly exposed",
    trigger: "Expected database exposure is INTERNAL and observed exposure is PUBLIC.",
    baseRisk: "52",
    baseRiskLogic: () => 52,
    remediation: "Remove the public port mapping and restrict the database to the application network.",
  },
  {
    id: "CS-CONTAINER-001",
    name: "Unexpected container user",
    trigger: "Observed container user differs from the approved user after trimming surrounding whitespace.",
    baseRisk: "42 for root; 25 otherwise",
    baseRiskLogic: (row) => row.observedContainerUser.toLowerCase() === "root" ? 42 : 25,
    remediation: "Set the approved non-root USER in the image and deployment configuration.",
  },
  {
    id: "CS-CONTAINER-002",
    name: "Docker socket mounted",
    trigger: "The application container mounts /var/run/docker.sock.",
    baseRisk: "58",
    baseRiskLogic: () => 58,
    remediation: "Remove the socket mount or use a restricted read-only proxy.",
  },
  {
    id: "CS-SUPPLY-001",
    name: "Image digest mismatch",
    trigger: "Observed image digest differs from the approved immutable digest.",
    baseRisk: "38",
    baseRiskLogic: () => 38,
    remediation: "Redeploy the approved image or formally approve and record the new digest.",
  },
  {
    id: "CS-DEPENDENCY-001",
    name: "Dependency version drift",
    trigger: "Observed framework or dependency version differs from the approved version.",
    baseRisk: "48 if known exploited; 24 otherwise",
    baseRiskLogic: (row) => row.knownExploited ? 48 : 24,
    remediation: "Rebuild from the approved lock file or update the baseline after vulnerability review.",
  },
];

const criticalityWeight = { LOW: 0, MEDIUM: 8, HIGH: 15, CRITICAL: 22 };

function digest(label) {
  return `sha256:${crypto.createHash("sha256").update(label).digest("hex")}`;
}

function riskScore(baseRisk, row) {
  return Math.min(
    100,
    baseRisk + criticalityWeight[row.criticality] + (row.internetFacing ? 15 : 0) + (row.knownExploited ? 18 : 0),
  );
}

function severity(score) {
  if (score >= 85) return "CRITICAL";
  if (score >= 65) return "HIGH";
  if (score >= 40) return "MEDIUM";
  return "LOW";
}

function selectedRuleIndexes(caseIndex, driftIndex) {
  if (caseIndex % 5 === 0) return [];
  if (driftIndex < 18) return [Math.floor(driftIndex / 3)];
  const first = (caseIndex * 7 + 1) % rules.length;
  const second = (caseIndex * 11 + 3) % rules.length;
  const third = (caseIndex * 13 + 5) % rules.length;
  const chosen = new Set([first, second]);
  if (caseIndex % 3 === 0) chosen.add(third);
  if (caseIndex % 11 === 0) chosen.add((caseIndex + 4) % rules.length);
  return [...chosen].sort((a, b) => a - b);
}

function generateCases() {
  const rows = [];
  let driftIndex = 0;
  const javaVersions = ["17", "21"];
  const dependencyVersions = ["3.3.5", "3.4.10", "3.5.6"];
  const sourceFormats = ["Maven", "Gradle", "Mixed"];
  const criticalities = ["LOW", "MEDIUM", "HIGH", "CRITICAL"];

  for (let i = 0; i < CASE_COUNT; i += 1) {
    const selected = selectedRuleIndexes(i, driftIndex);
    if (selected.length > 0) driftIndex += 1;
    const expectedJava = javaVersions[i % javaVersions.length];
    const expectedDatabaseExposure = i % 7 === 0 && !selected.includes(1) ? "PUBLIC" : "INTERNAL";
    const expectedContainerUser = i % 2 === 0 ? "appuser" : "svcuser";
    const expectedDependency = dependencyVersions[i % dependencyVersions.length];
    const caseNumber = i + 1;
    const row = {
      caseId: `CSB-${String(caseNumber).padStart(3, "0")}`,
      split: caseNumber <= 84 ? "TRAIN" : caseNumber <= 102 ? "VALIDATION" : "TEST",
      sourceFormat: sourceFormats[i % sourceFormats.length],
      criticality: criticalities[(i * 3) % criticalities.length],
      expectedJava,
      observedJava: selected.includes(0) ? (expectedJava === "17" ? "21" : "17") : expectedJava,
      expectedDatabaseExposure,
      observedDatabaseExposure: selected.includes(1) ? "PUBLIC" : expectedDatabaseExposure,
      expectedContainerUser,
      observedContainerUser: selected.includes(2) ? (i % 3 === 0 ? "admin" : "root") : expectedContainerUser,
      dockerSocketMounted: selected.includes(3),
      expectedImageDigest: digest(`approved-image-${i % 12}`),
      observedImageDigest: "",
      expectedDependency,
      observedDependency: selected.includes(5) ? (expectedDependency === "3.2.2" ? "3.1.0" : "3.2.2") : expectedDependency,
      internetFacing: i % 3 !== 0,
      knownExploited: i % 4 === 1 || i % 9 === 0,
    };
    row.observedImageDigest = selected.includes(4) ? digest(`unapproved-image-${caseNumber}`) : row.expectedImageDigest;

    // The expanded evaluation set includes two semantically equivalent values
    // written in alternate forms. Their labels follow normalized policy intent.
    if (caseNumber === 121) {
      row.observedJava = `${row.expectedJava}.0.2`;
    }
    if (caseNumber === 126) {
      row.observedContainerUser = ` ${row.expectedContainerUser} `;
    }

    const selectedRules = selected.map((index) => rules[index]);
    const scores = selectedRules.map((rule) => ({ id: rule.id, score: riskScore(rule.baseRiskLogic(row), row) }));
    const maxRisk = scores.length ? Math.max(...scores.map((entry) => entry.score)) : 0;
    const scenario = caseNumber === 121
      ? "Normalization boundary: equivalent Java major version"
      : caseNumber === 126
        ? "Normalization boundary: equivalent container user after trimming"
        : selected.length === 0
          ? "Compliant baseline"
          : selected.length === 1
            ? `Single-rule drift: ${selectedRules[0].name}`
            : `Multi-control drift: ${selected.length} findings`;

    rows.push([
      row.caseId,
      row.split,
      scenario,
      row.sourceFormat,
      row.criticality,
      row.expectedJava,
      row.observedJava,
      row.expectedDatabaseExposure,
      row.observedDatabaseExposure,
      row.expectedContainerUser,
      row.observedContainerUser,
      row.dockerSocketMounted,
      row.expectedImageDigest,
      row.observedImageDigest,
      row.expectedDependency,
      row.observedDependency,
      row.internetFacing,
      row.knownExploited,
      selectedRules.map((rule) => rule.id).join("; "),
      scores.map((entry) => `${entry.id}=${entry.score}`).join("; "),
      selectedRules.length,
      maxRisk,
      maxRisk === 0 ? "NONE" : severity(maxRisk),
      selectedRules.length === 0 ? "COMPLIANT" : "DRIFT",
      `Synthetic benchmark fixture ${VERSION}`,
      caseNumber === 121
        ? "Equivalent Java major version expressed with a full release string; no finding expected after normalization."
        : caseNumber === 126
          ? "Equivalent container user with surrounding whitespace; no finding expected after normalization."
          : selectedRules.length === 0
            ? "Baseline and observation match; no findings expected."
            : `${selectedRules.length} labeled finding${selectedRules.length === 1 ? "" : "s"} expected from the CodeShield rule engine.`,
    ]);
  }
  return rows;
}

const headers = [
  "Case ID", "Split", "Scenario", "Source Format", "Asset Criticality",
  "Expected Java", "Observed Java", "Expected DB Exposure", "Observed DB Exposure",
  "Expected Container User", "Observed Container User", "Docker Socket Mounted",
  "Expected Image Digest", "Observed Image Digest", "Expected Dependency", "Observed Dependency",
  "Internet Facing", "Known Exploited", "Expected Rule IDs", "Expected Rule Scores",
  "Finding Count", "Max Risk Score", "Highest Severity", "Overall Label", "Evidence Source", "Notes",
];

const dataRows = generateCases();
const ruleCounts = rules.map((rule) => dataRows.filter((row) => row[18].includes(rule.id)).length);
const canonicalData = JSON.stringify({ version: VERSION, headers, rows: dataRows });
const checksum = crypto.createHash("sha256").update(canonicalData).digest("hex");

const workbook = Workbook.create();
const summary = workbook.worksheets.add("Summary");
const dataset = workbook.worksheets.add("Dataset");
const ruleCatalog = workbook.worksheets.add("Rule Catalog");
const dataDictionary = workbook.worksheets.add("Data Dictionary");
const methodology = workbook.worksheets.add("Methodology");

for (const sheet of [summary, dataset, ruleCatalog, dataDictionary, methodology]) {
  sheet.showGridLines = false;
  sheet.getRange("A1:Z170").format.font = { name: FONT, size: 10, color: "#172B4D" };
  sheet.getRange("A1:Z170").format.verticalAlignment = "center";
}

// Summary
summary.tabColor = "#143B5D";
summary.getRange("A2").values = [["CodeShield Security Drift Benchmark Dataset"]];
summary.getRange("A2").format.font = { name: FONT, size: 16, bold: true, color: "#143B5D" };
summary.getRange("A3").values = [["Deterministic labeled cases for validating baseline-to-observation security drift detection"]];
summary.getRange("A3").format.font = { name: FONT, size: 10, italic: true, color: "#52616B" };
summary.getRange("A4:I4").format.borders = { bottom: { style: "medium", color: "#2F80A0" } };

summary.getRange("A6:B6").values = [["Dataset metric", "Value"]];
summary.getRange("A7:A13").values = [
  ["Total labeled cases"], ["Compliant cases"], ["Drift cases"], ["Total expected findings"],
  ["Average findings per case"], ["Maximum risk score"], ["Dataset version"],
];
summary.getRange("B7:B12").formulas = [
  [`=COUNTA(Dataset!$A$2:$A$${CASE_COUNT + 1})`],
  [`=COUNTIF(Dataset!$X$2:$X$${CASE_COUNT + 1},\"COMPLIANT\")`],
  [`=COUNTIF(Dataset!$X$2:$X$${CASE_COUNT + 1},\"DRIFT\")`],
  [`=SUM(Dataset!$U$2:$U$${CASE_COUNT + 1})`],
  [`=AVERAGE(Dataset!$U$2:$U$${CASE_COUNT + 1})`],
  [`=MAX(Dataset!$V$2:$V$${CASE_COUNT + 1})`],
];
summary.getRange("B13").values = [[VERSION]];

summary.getRange("D6:F6").values = [["Rule ID", "Rule", "Labeled findings"]];
summary.getRange("D7:E12").values = rules.map((rule) => [rule.id, rule.name]);
summary.getRange("F7:F12").values = ruleCounts.map((count) => [count]);

summary.getRange("H6:I6").values = [["Highest severity", "Cases"]];
summary.getRange("H7:H11").values = [["NONE"], ["LOW"], ["MEDIUM"], ["HIGH"], ["CRITICAL"]];
summary.getRange("I7:I11").formulas = ["NONE", "LOW", "MEDIUM", "HIGH", "CRITICAL"].map((level) => [`=COUNTIF(Dataset!$W$2:$W$${CASE_COUNT + 1},\"${level}\")`]);

summary.getRange("A16:B16").values = [["Dataset split", "Cases"]];
summary.getRange("A17:A19").values = [["TRAIN"], ["VALIDATION"], ["TEST"]];
summary.getRange("B17:B19").formulas = ["TRAIN", "VALIDATION", "TEST"].map((split) => [`=COUNTIF(Dataset!$B$2:$B$${CASE_COUNT + 1},\"${split}\")`]);
summary.getRange("D16:I16").values = [["How to use", "", "", "", "", ""]];
summary.mergeCells("D17:I19");
summary.getRange("D17").values = [["Use each row as one scan fixture. Supply the approved values as the asset baseline and the observed values as scan input. Compare the application output with Expected Rule IDs, Finding Count, Max Risk Score, Highest Severity, and Overall Label."]];
summary.getRange("D17:I19").format.wrapText = true;
summary.getRange("D17:I19").format.verticalAlignment = "top";

for (const address of ["A6:B6", "D6:F6", "H6:I6", "A16:B16", "D16:I16"]) {
  summary.getRange(address).format = {
    fill: "#143B5D",
    font: { name: FONT, size: 10, bold: true, color: "#FFFFFF" },
    horizontalAlignment: "center",
    verticalAlignment: "center",
  };
}
for (const address of ["A7:B13", "D7:F12", "H7:I11", "A17:B19", "D17:I19"]) {
  summary.getRange(address).format.borders = { preset: "inside", style: "thin", color: "#D9E2E8" };
}
summary.getRange("B7:B10").format.numberFormat = "#,##0";
summary.getRange("B11").format.numberFormat = "0.00";
summary.getRange("B12").format.numberFormat = "0";
summary.getRange("F7:F12").format.numberFormat = "#,##0";
summary.getRange("I7:I11").format.numberFormat = "#,##0";
summary.getRange("B17:B19").format.numberFormat = "#,##0";
summary.getRange("A1:I22").format.rowHeight = 20;
summary.getRange("A2:I2").format.rowHeight = 28;
summary.getRange("A3:I3").format.rowHeight = 22;
summary.getRange("D17:I19").format.rowHeight = 25;
summary.getRange("A:A").format.columnWidth = 24;
summary.getRange("B:B").format.columnWidth = 15;
summary.getRange("C:C").format.columnWidth = 3;
summary.getRange("D:D").format.columnWidth = 22;
summary.getRange("E:E").format.columnWidth = 34;
summary.getRange("F:F").format.columnWidth = 17;
summary.getRange("G:G").format.columnWidth = 3;
summary.getRange("H:H").format.columnWidth = 18;
summary.getRange("I:I").format.columnWidth = 14;

// Dataset
dataset.tabColor = "#2F80A0";
dataset.getRange("A1:Z1").values = [headers];
dataset.getRange(`A2:Z${CASE_COUNT + 1}`).values = dataRows;
dataset.getRange("A1:Z1").format = {
  fill: "#143B5D",
  font: { name: FONT, size: 10, bold: true, color: "#FFFFFF" },
  horizontalAlignment: "center",
  verticalAlignment: "center",
  wrapText: true,
};
dataset.getRange(`A2:Z${CASE_COUNT + 1}`).format.borders = {
  insideHorizontal: { style: "thin", color: "#E8EEF2" },
};
dataset.getRange(`A2:Z${CASE_COUNT + 1}`).format.rowHeight = 34;
dataset.getRange("A1:Z1").format.rowHeight = 42;
dataset.getRange(`A2:Z${CASE_COUNT + 1}`).format.verticalAlignment = "top";
dataset.getRange(`C2:C${CASE_COUNT + 1}`).format.wrapText = true;
dataset.getRange(`S2:T${CASE_COUNT + 1}`).format.wrapText = true;
dataset.getRange(`Y2:Z${CASE_COUNT + 1}`).format.wrapText = true;
dataset.getRange(`L2:L${CASE_COUNT + 1}`).format.horizontalAlignment = "center";
dataset.getRange(`Q2:R${CASE_COUNT + 1}`).format.horizontalAlignment = "center";
dataset.getRange(`U2:V${CASE_COUNT + 1}`).format.numberFormat = "0";
dataset.freezePanes.freezeRows(1);
dataset.freezePanes.freezeColumns(3);
const casesTable = dataset.tables.add(`A1:Z${CASE_COUNT + 1}`, true, "CodeShieldCasesTable");
casesTable.style = "TableStyleMedium2";
casesTable.showBandedColumns = false;
casesTable.showFilterButton = true;
dataset.getRange(`X2:X${CASE_COUNT + 1}`).conditionalFormats.add("containsText", {
  text: "DRIFT",
  format: { fill: "#FDECEC", font: { color: "#B42318", bold: true } },
});
dataset.getRange(`X2:X${CASE_COUNT + 1}`).conditionalFormats.add("containsText", {
  text: "COMPLIANT",
  format: { fill: "#E8F5E9", font: { color: "#226B3A", bold: true } },
});
dataset.getRange(`W2:W${CASE_COUNT + 1}`).conditionalFormats.add("containsText", {
  text: "CRITICAL",
  format: { fill: "#7A1F1F", font: { color: "#FFFFFF", bold: true } },
});
dataset.getRange(`W2:W${CASE_COUNT + 1}`).conditionalFormats.add("containsText", {
  text: "HIGH",
  format: { fill: "#F8D7DA", font: { color: "#8A1C1C", bold: true } },
});
dataset.getRange(`W2:W${CASE_COUNT + 1}`).conditionalFormats.add("containsText", {
  text: "MEDIUM",
  format: { fill: "#FFF3CD", font: { color: "#7A5A00", bold: true } },
});

const widths = [13, 13, 34, 13, 16, 13, 13, 19, 19, 20, 20, 17, 28, 28, 20, 20, 15, 16, 29, 34, 13, 14, 16, 15, 29, 42];
for (let col = 0; col < widths.length; col += 1) {
  dataset.getRangeByIndexes(0, col, CASE_COUNT + 1, 1).format.columnWidth = widths[col];
}

// Rule Catalog
ruleCatalog.getRange("A2").values = [["CodeShield Rule Catalog"]];
ruleCatalog.getRange("A2").format.font = { name: FONT, size: 15, bold: true, color: "#143B5D" };
ruleCatalog.getRange("A4:F4").values = [["Rule ID", "Rule Name", "Trigger", "Base Risk", "Scoring Context", "Recommended Remediation"]];
ruleCatalog.getRange("A5:F10").values = rules.map((rule) => [
  rule.id,
  rule.name,
  rule.trigger,
  rule.baseRisk,
  "Base risk + criticality weight + 15 if internet-facing + 18 if known exploited; capped at 100.",
  rule.remediation,
]);
ruleCatalog.getRange("A4:F4").format = {
  fill: "#143B5D",
  font: { name: FONT, size: 10, bold: true, color: "#FFFFFF" },
  horizontalAlignment: "center",
  wrapText: true,
};
ruleCatalog.getRange("A5:F10").format.wrapText = true;
ruleCatalog.getRange("A5:F10").format.verticalAlignment = "top";
ruleCatalog.getRange("A5:F10").format.rowHeight = 64;
ruleCatalog.getRange("A4:F4").format.rowHeight = 34;
ruleCatalog.getRange("A4:F10").format.borders = { insideHorizontal: { style: "thin", color: "#D9E2E8" } };
ruleCatalog.getRange("A:A").format.columnWidth = 22;
ruleCatalog.getRange("B:B").format.columnWidth = 33;
ruleCatalog.getRange("C:C").format.columnWidth = 45;
ruleCatalog.getRange("D:D").format.columnWidth = 24;
ruleCatalog.getRange("E:E").format.columnWidth = 44;
ruleCatalog.getRange("F:F").format.columnWidth = 48;
ruleCatalog.freezePanes.freezeRows(4);

// Data Dictionary
const dictionaryRows = [
  ["Case ID", "Text", "CSB-001", "Stable unique identifier for a benchmark case."],
  ["Split", "Category", "TRAIN / VALIDATION / TEST", "Deterministic partition for development and final evaluation."],
  ["Scenario", "Text", "Compliant, single-rule, or multi-control drift", "Human-readable description of the case."],
  ["Source Format", "Category", "Maven / Gradle / Mixed", "Repository configuration style represented by the fixture."],
  ["Asset Criticality", "Category", "LOW / MEDIUM / HIGH / CRITICAL", "Business criticality used by the risk scorer."],
  ["Expected Java", "Text", "17 or 21", "Approved Java major version."],
  ["Observed Java", "Text", "17 / 21 / full release string", "Java version discovered during the scan; evaluation compares the normalized major version."],
  ["Expected DB Exposure", "Category", "INTERNAL / PUBLIC", "Approved database network exposure."],
  ["Observed DB Exposure", "Category", "INTERNAL / PUBLIC", "Database exposure discovered during the scan."],
  ["Expected Container User", "Text", "appuser / svcuser", "Approved runtime account."],
  ["Observed Container User", "Text", "appuser / svcuser / root / admin", "Runtime account discovered during the scan; surrounding whitespace is ignored by the benchmark label."],
  ["Docker Socket Mounted", "Boolean", "TRUE / FALSE", "Whether /var/run/docker.sock is mounted into the application container."],
  ["Expected Image Digest", "Text", "sha256:<64 hex characters>", "Approved immutable image digest."],
  ["Observed Image Digest", "Text", "sha256:<64 hex characters>", "Image digest discovered during the scan."],
  ["Expected Dependency", "Text", "Semantic version", "Approved framework or dependency version."],
  ["Observed Dependency", "Text", "Semantic version", "Framework or dependency version discovered during the scan."],
  ["Internet Facing", "Boolean", "TRUE / FALSE", "Risk context indicating public reachability."],
  ["Known Exploited", "Boolean", "TRUE / FALSE", "Risk context indicating known exploitation."],
  ["Expected Rule IDs", "Text", "Semicolon-separated rule IDs", "Exact findings the application should emit."],
  ["Expected Rule Scores", "Text", "RULE-ID=score", "Expected risk score for each emitted finding."],
  ["Finding Count", "Integer", "0 to 6", "Number of expected findings."],
  ["Max Risk Score", "Integer", "0 to 100", "Highest expected finding score, or 0 for a compliant case."],
  ["Highest Severity", "Category", "NONE / LOW / MEDIUM / HIGH / CRITICAL", "Severity mapped from Max Risk Score."],
  ["Overall Label", "Category", "COMPLIANT / DRIFT", "Final case label. DRIFT means one or more expected findings."],
  ["Evidence Source", "Text", `Synthetic benchmark fixture ${VERSION}`, "Provenance label for generated and independently specified boundary cases."],
  ["Notes", "Text", "Case-specific explanation", "Short interpretation of the expected result."],
];
dataDictionary.getRange("A2").values = [["Data Dictionary"]];
dataDictionary.getRange("A2").format.font = { name: FONT, size: 15, bold: true, color: "#143B5D" };
dataDictionary.getRange("A4:D4").values = [["Field", "Type", "Allowed Values / Example", "Definition"]];
dataDictionary.getRange(`A5:D${4 + dictionaryRows.length}`).values = dictionaryRows;
dataDictionary.getRange("A4:D4").format = {
  fill: "#143B5D",
  font: { name: FONT, size: 10, bold: true, color: "#FFFFFF" },
  horizontalAlignment: "center",
  wrapText: true,
};
dataDictionary.getRange(`A5:D${4 + dictionaryRows.length}`).format.wrapText = true;
dataDictionary.getRange(`A5:D${4 + dictionaryRows.length}`).format.verticalAlignment = "top";
dataDictionary.getRange(`A5:D${4 + dictionaryRows.length}`).format.rowHeight = 38;
dataDictionary.getRange(`A4:D${4 + dictionaryRows.length}`).format.borders = { insideHorizontal: { style: "thin", color: "#D9E2E8" } };
dataDictionary.getRange("A:A").format.columnWidth = 29;
dataDictionary.getRange("B:B").format.columnWidth = 17;
dataDictionary.getRange("C:C").format.columnWidth = 34;
dataDictionary.getRange("D:D").format.columnWidth = 62;
dataDictionary.freezePanes.freezeRows(4);

// Methodology
methodology.tabColor = "#7A8793";
methodology.getRange("A2").values = [["Dataset Methodology and Limitations"]];
methodology.getRange("A2").format.font = { name: FONT, size: 15, bold: true, color: "#143B5D" };
methodology.getRange("A4:B4").values = [["Item", "Detail"]];
methodology.getRange("A5:B13").values = [
  ["Purpose", "Functional evaluation of CodeShield's approved-baseline versus observed-state drift detection and risk scoring."],
  ["Dataset type", "Synthetic, deterministic, fully labeled benchmark data. It contains no people, credentials, secrets, or production telemetry."],
  ["Source of truth", "CodeShield rule engine version 1.0: six drift rules and the current risk-scoring thresholds."],
  ["Generation", `${CASE_COUNT} cases in version ${VERSION}. The original ${ORIGINAL_CASE_COUNT} cases are retained and 30 evaluation cases add mixed-rule and normalization boundaries. Every fifth case is compliant.`],
  ["Split", "84 TRAIN cases, 18 VALIDATION cases, and 48 TEST cases. The 30-case expansion is held in the TEST partition."],
  ["Risk formula", "Risk = base rule risk + criticality weight (0/8/15/22) + 15 if internet-facing + 18 if known exploited, capped at 100."],
  ["Severity thresholds", "LOW: below 40; MEDIUM: 40–64; HIGH: 65–84; CRITICAL: 85–100. Compliant cases use NONE."],
  ["Intended use", "Academic submission, regression testing, rule validation, and boundary evaluation. It is not a record of real attacks or a vulnerability-prevalence study."],
  ["Integrity checksum", `SHA-256 of the canonical generated content: ${checksum}`],
];
methodology.getRange("A4:B4").format = {
  fill: "#143B5D",
  font: { name: FONT, size: 10, bold: true, color: "#FFFFFF" },
  horizontalAlignment: "center",
};
methodology.getRange("A5:B13").format.wrapText = true;
methodology.getRange("A5:B13").format.verticalAlignment = "top";
methodology.getRange("A5:B13").format.rowHeight = 48;
methodology.getRange("A4:B13").format.borders = { insideHorizontal: { style: "thin", color: "#D9E2E8" } };
methodology.getRange("A:A").format.columnWidth = 26;
methodology.getRange("B:B").format.columnWidth = 105;

workbook.recalculate();

const summaryCheck = await workbook.inspect({
  kind: "table",
  range: "Summary!A2:I19",
  include: "values,formulas",
  tableMaxRows: 22,
  tableMaxCols: 12,
});
console.log("SUMMARY_CHECK");
console.log(summaryCheck.ndjson);

const datasetCheck = await workbook.inspect({
  kind: "table",
  range: "Dataset!A1:Z8",
  include: "values,formulas",
  tableMaxRows: 8,
  tableMaxCols: 26,
});
console.log("DATASET_CHECK");
console.log(datasetCheck.ndjson);

const errorCheck = await workbook.inspect({
  kind: "match",
  searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A|#NUM!|#NULL!|#SPILL!|#CALC!",
  options: { useRegex: true, maxResults: 300 },
  summary: "final formula error scan",
});
console.log("FORMULA_ERROR_SCAN");
console.log(errorCheck.ndjson);

await fs.mkdir(PREVIEW_DIR, { recursive: true });
for (const [sheetName, range] of [
  ["Summary", "A1:I22"],
  ["Dataset", "A1:Z18"],
  ["Rule Catalog", "A1:F11"],
  ["Data Dictionary", "A1:D30"],
  ["Methodology", "A1:B14"],
]) {
  const preview = await workbook.render({ sheetName, range, scale: 1, format: "png" });
  await fs.writeFile(`${PREVIEW_DIR}/${sheetName.replaceAll(" ", "_")}.png`, new Uint8Array(await preview.arrayBuffer()));
}

await fs.mkdir(OUTPUT_DIR, { recursive: true });
const output = await SpreadsheetFile.exportXlsx(workbook);
await output.save(OUTPUT_FILE);

console.log(JSON.stringify({ output: OUTPUT_FILE, previews: PREVIEW_DIR, checksum, cases: dataRows.length }));
