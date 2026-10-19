# CodeShield and Checkov benchmark comparison

Run date: 2026-10-19

## Dataset

- Cases: 150
- Labeled drift cases: 120
- Labeled compliant cases: 30
- Expected CodeShield findings: 232

## Measured results

| Evaluation | Accuracy | Precision | Recall | F1 | TP | TN | FP | FN |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| CodeShield binary drift | 98.7% | 98.4% | 100.0% | 99.2% | 120 | 28 | 2 | 0 |
| CodeShield rule-level micro | n.a. | 99.1% | 100.0% | 99.6% | 232 | 666 | 2 | 0 |
| Checkov root-user sub-control | 100.0% | 100.0% | 100.0% | 100.0% | 19 | 131 | 0 | 0 |
| Checkov mapped to container-user drift | 88.0% | 100.0% | 51.4% | 67.9% | 19 | 113 | 0 | 18 |
| Checkov root signal used as overall drift result | 32.7% | 100.0% | 15.8% | 27.3% | 19 | 30 | 0 | 101 |

CodeShield exact rule-set accuracy was 148 of 150 cases (98.7%). Risk scores matched in 148 cases and highest severity matched in 148 cases.

## Method

CodeShield results were produced by compiling the current project and invoking its actual `DriftDetectionService` and `RiskScoringService` for every workbook row. Checkov 3.3.21 scanned one generated Dockerfile per case with its built-in `CKV_DOCKER_8` policy. The Dockerfiles used each case's observed Java version and observed container user and included a health check to avoid unrelated policy failures.

## Interpretation limits

Checkov and CodeShield do not implement the same task. `CKV_DOCKER_8` checks whether the final Dockerfile user is root. It does not accept an application-specific approved baseline. It therefore detected all 19 root-user fixtures, but it could not detect the 18 non-root account changes labeled as container-user drift. The other CodeShield categories—runtime version drift, database exposure relative to intent, Docker socket runtime evidence, approved image digest drift, and approved dependency-version drift—have no equivalent input in this Checkov run and are unsupported rather than ordinary false negatives.

The overall Checkov row is included only to show what happens when a single root-user policy is forced to answer the broader six-rule drift task. It is not a general claim about Checkov's effectiveness. The benchmark is synthetic and derived from CodeShield's rule specification, so CodeShield's result measures internal consistency, not accuracy on independently sampled production incidents.

## Tool sources

- Checkov release: https://github.com/bridgecrewio/checkov/releases/tag/3.3.21
- Checkov Dockerfile scanning: https://www.checkov.io/7.Scan%20Examples/Dockerfile.html
- Checkov `CKV_DOCKER_8` policy catalog: https://www.checkov.io/5.Policy%20Index/dockerfile.html
