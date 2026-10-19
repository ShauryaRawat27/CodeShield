# CodeShield Context Aware SecOps Platform

CodeShield detects security drift between an application's approved baseline and the configuration found in its repository or deployed environment. Each mismatch becomes an explainable finding with an expected state, observed state, supporting evidence, context-adjusted risk score, and remediation guidance.

## Capabilities

- Register and persist application security baselines
- Scan public HTTPS Git URLs or existing local repositories without modifying the source
- Parse Maven, Gradle, Dockerfile, and Docker Compose configuration
- Accept runtime evidence through the web interface or REST API
- Detect six types of version, exposure, privilege, mount, image, and dependency drift
- Analyze pasted or uploaded logs and aggregate repeated events into six explainable security signals
- Adjust risk using application criticality, Internet exposure, and exploit evidence
- Track findings as open, resolved, or accepted
- Preserve scan history and evidence provenance
- Run with an embedded H2 database or Docker Compose
- Verify the drift engine with automated tests

## Technology

- Java 21
- Spring Boot 3.3
- Spring MVC and Thymeleaf
- Spring Data JPA
- H2 Database
- Maven and JUnit 5

## Run locally

Requirements: Java 21 or newer and Maven 3.9 or newer.

```bash
mvn spring-boot:run
```

Open [http://localhost:8080](http://localhost:8080). The database is created under `data/`. A new installation starts empty: register the approved baseline for an application before running a scan. CodeShield does not insert sample applications or findings.

## Scan a real repository

1. Register the repository's approved values on the **Applications** page.
2. Open **Repository Scan**.
3. Select the baseline.
4. Paste a public HTTPS clone URL or enter an absolute local path.
5. Run the scan and review the resulting evidence and findings.

Remote example:

```text
https://github.com/owner/repository.git
```

For example, an existing local checkout can be scanned with an absolute path:

```text
/path/to/repository
```

Remote repositories are shallow-cloned into temporary storage and deleted after analysis. Private repositories requiring credentials are not accessed. The scanner searches up to six directory levels, ignores generated directories such as `.git`, `.m2`, `node_modules`, `target`, and `build`, and never writes to the source repository.

### Repository evidence currently supported

| File | Evidence extracted |
|---|---|
| `pom.xml` | Java version and Spring Boot parent version |
| `build.gradle` or `build.gradle.kts` | Java and Spring Boot versions |
| `Dockerfile` | Java base-image version and final container user |
| `compose.yaml` or Docker Compose variants | Public port mappings, database exposure, Docker socket mounts, and image digest pinning |

A repository scan analyzes declared source and deployment configuration. It does not claim to observe a running server. Actual runtime values can be submitted through **New Scan** or `POST /api/scans`. A production deployment could connect the same scan engine to authenticated host and container collectors.

## Analyze real logs

1. Register the application on the **Applications** page.
2. Open **Log Analysis**.
3. Select the application and indicate whether it is Internet-facing.
4. Paste log text or upload a `.log`, `.txt`, `.csv`, `.json`, or `.jsonl` text file.
5. Review the grouped signals on **Findings** and the completed run on **History**.

The MVP accepts up to 2 MB and 20,000 lines per submission. It checks for repeated authentication failures, a success in the same window as repeated failures, privileged activity, Docker daemon access, sensitive endpoint requests, and server-error spikes. Thresholds are configurable under `codeshield.logs` in `application.yml`.

The analyzer does not store the uploaded raw file. Each finding retains an aggregate count and one short example with common password, token, authorization, and API-key values redacted. The rules identify events that deserve investigation; they do not claim that every match is a confirmed attack.

## Run the tests

```bash
mvn test
```

## Build the application

```bash
mvn clean package
java -jar target/codeshield-1.0.0.jar
```

## Run with Docker

```bash
docker compose up --build
```

The included Compose configuration mounts this source repository read-only at `/repos/codeshield`, so that path can be used for a repository scan inside the container. To analyze a different repository, add another read-only volume mapping.

```yaml
volumes:
  - /absolute/path/to/another-repository:/repos/another-repository:ro
```

## Detection rules

| Rule | Detects |
|---|---|
| `CS-RUNTIME-001` | Java version differs from the approved baseline |
| `CS-NETWORK-001` | An internal database is publicly exposed |
| `CS-CONTAINER-001` | A container uses an unexpected account, especially root |
| `CS-CONTAINER-002` | The Docker socket is mounted inside an application container |
| `CS-SUPPLY-001` | An image is unpinned or differs from the approved digest |
| `CS-DEPENDENCY-001` | A framework or dependency version differs from the approved version |
| `CS-LOG-001` | Repeated authentication failures |
| `CS-LOG-002` | Authentication success in a window containing repeated failures |
| `CS-LOG-003` | Root, UID 0, sudo, or equivalent privileged activity |
| `CS-LOG-004` | Docker socket or daemon access |
| `CS-LOG-005` | Repeated requests to sensitive administration or diagnostic paths |
| `CS-LOG-006` | A spike in HTTP 5xx responses, exceptions, or fatal errors |

## Risk model

Each rule supplies a base risk value. CodeShield adds application criticality, Internet exposure, and known exploit evidence, then caps the score at 100.

```text
risk = base rule risk
     + application criticality
     + Internet exposure
     + known exploit evidence
```

Scores below 40 are Low, 40–64 are Medium, 65–84 are High, and 85–100 are Critical.

Repository limits, rule base risks, context weights, and severity thresholds are declared under `codeshield` in `src/main/resources/application.yml`. Every numeric setting can be overridden through its corresponding `CODESHIELD_*` environment variable. Stable rule identifiers, comparison conditions, evidence messages, and remediation text remain part of the application code because they define the detector's behavior rather than supplying repository-specific data.

## REST API

```text
GET  /api/assets
GET  /api/findings
GET  /api/scans
POST /api/scans
```

Example runtime scan request:

```json
{
  "assetId": 1,
  "javaVersion": "17",
  "databaseExposure": "PUBLIC",
  "containerUser": "root",
  "dockerSocketMounted": true,
  "imageDigest": "sha256:unapproved-image",
  "dependencyVersion": "2.7.18",
  "internetFacing": true,
  "knownExploited": true,
  "evidenceSource": "Container inventory agent"
}
```

## Project structure

```text
src/main/java/com/codeshield/
├── config       configurable repository, log, rule, and risk limits
├── domain       database entities and enums
├── repository   Spring Data repositories
├── service      repository inspection, log triage, drift detection, risk, and orchestration
└── web           MVC pages, JSON API, and request models

src/main/resources/
├── templates     Thymeleaf pages
└── static/css    responsive interface styling
```

## Supporting material

The system architecture, research workbook, project report, and benchmark dataset are stored in [`outputs/codeshield-deliverables`](outputs/codeshield-deliverables/).

### Benchmark dataset

`CodeShield_Security_Drift_Benchmark_Dataset_Expanded.xlsx` contains 150 deterministic, labeled scan cases and 232 rule-level evaluation rows. It includes compliant configurations, isolated examples of all six drift rules, and multi-control drift scenarios. Each case records the approved baseline, observed state, expected rule IDs, per-rule risk scores, finding count, highest severity, and overall label.

The workbook is synthetic because public vulnerability datasets generally label vulnerable code or CVEs, while CodeShield evaluates drift from a project-specific approved baseline. The included train, validation, and test partitions can be used for demonstrations, regression tests, and evaluation without claiming that the rows are real incidents.
