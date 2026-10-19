package com.codeshield.service;

import com.codeshield.config.LogAnalysisProperties;
import com.codeshield.domain.ApplicationAsset;
import com.codeshield.domain.Finding;
import com.codeshield.domain.FindingStatus;
import com.codeshield.domain.ScanRun;
import com.codeshield.repository.ApplicationAssetRepository;
import com.codeshield.repository.FindingRepository;
import com.codeshield.repository.ScanRunRepository;
import com.codeshield.web.dto.ScanRequest;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class LogAnalysisService {

    private static final Pattern AUTHENTICATION_FAILURE = Pattern.compile(
            "(?i)(failed password|authentication failed|login failed|invalid (?:user|credentials)|\\bunauthorized\\b|\\bstatus\\s*[=:]?\\s*401\\b|\"\\s*401\\s+)"
    );
    private static final Pattern AUTHENTICATION_SUCCESS = Pattern.compile(
            "(?i)(accepted password|authentication succeeded|login successful|successful login|\\b(?:login|auth)[^\\r\\n]{0,50}status\\s*[=:]?\\s*200\\b)"
    );
    private static final Pattern PRIVILEGED_ACTIVITY = Pattern.compile(
            "(?i)(\\buser\\s*[=:]\\s*root\\b|\\buid\\s*[=:]?\\s*0\\b|\\bsudo(?:\\[|:|\\s)|privilege escalation|run as root)"
    );
    private static final Pattern DOCKER_ACCESS = Pattern.compile(
            "(?i)(/var/run/docker\\.sock|\\bdocker daemon\\b|\\bdocker api\\b)"
    );
    private static final Pattern SENSITIVE_PATH = Pattern.compile(
            "(?i)(/(?:admin|actuator|wp-admin|server-status|phpmyadmin)\\b|/\\.env\\b)"
    );
    private static final Pattern SERVER_ERROR = Pattern.compile(
            "(?i)(\\bstatus\\s*[=:]?\\s*5\\d\\d\\b|HTTP/\\d(?:\\.\\d)?\"?\\s+5\\d\\d\\b|\\b\\w*exception\\b|\\b(?:stack ?trace|fatal error)\\b)"
    );
    private static final Pattern AUTHORIZATION_VALUE = Pattern.compile(
            "(?i)\\bauthorization\\s*[=:]\\s*[^,;]+"
    );
    private static final Pattern SECRET_VALUE = Pattern.compile(
            "(?i)\\b(password|passwd|token|api[_-]?key)\\s*[=:]\\s*([^\\s,;]+)"
    );

    private final ApplicationAssetRepository assetRepository;
    private final ScanRunRepository scanRunRepository;
    private final FindingRepository findingRepository;
    private final RiskScoringService riskScoringService;
    private final LogAnalysisProperties properties;

    public LogAnalysisService(
            ApplicationAssetRepository assetRepository,
            ScanRunRepository scanRunRepository,
            FindingRepository findingRepository,
            RiskScoringService riskScoringService,
            LogAnalysisProperties properties
    ) {
        this.assetRepository = assetRepository;
        this.scanRunRepository = scanRunRepository;
        this.findingRepository = findingRepository;
        this.riskScoringService = riskScoringService;
        this.properties = properties;
    }

    @Transactional
    public LogAnalysisResult analyze(Long assetId, String content, boolean internetFacing, String sourceReference) {
        validateContent(content);
        ApplicationAsset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new EntityNotFoundException("Application asset not found"));

        List<String> lines = content.lines().toList();
        if (lines.size() > properties.getMaximumLines()) {
            throw new IllegalArgumentException(
                    "The log contains more than " + properties.getMaximumLines() + " lines. Upload a smaller time window."
            );
        }

        ScanRun scanRun = new ScanRun();
        scanRun.setAsset(asset);
        scanRun.setStartedAt(LocalDateTime.now());
        scanRun.setStatus("RUNNING");
        scanRun.setSourceReference(normalizeSourceReference(sourceReference));
        scanRun = scanRunRepository.save(scanRun);

        LogCounters counters = inspect(lines);
        ScanRequest riskContext = new ScanRequest();
        riskContext.setInternetFacing(internetFacing);
        riskContext.setKnownExploited(false);

        List<Finding> findings = new ArrayList<>();
        if (counters.authenticationFailures >= properties.getAuthenticationFailureThreshold()) {
            findings.add(createFinding(
                    asset, scanRun, riskContext, 40, "CS-LOG-001", "Repeated authentication failures",
                    "A concentrated set of failed authentication events can indicate password guessing, credential stuffing, or a misconfigured client.",
                    "Authentication failures should remain below the configured review threshold of "
                            + properties.getAuthenticationFailureThreshold() + " events per uploaded log window.",
                    counters.authenticationFailures + " authentication failure event(s) were observed.",
                    evidence(counters.authenticationFailures, counters.authenticationFailureSample),
                    "Check the source addresses and affected accounts, apply rate limiting or lockout controls, and verify whether the traffic is expected."
            ));
        }
        if (counters.authenticationFailures >= properties.getAuthenticationFailureThreshold()
                && counters.authenticationSuccesses > 0) {
            findings.add(createFinding(
                    asset, scanRun, riskContext, 55, "CS-LOG-002", "Authentication success observed with repeated failures",
                    "Successful authentication in the same log window as repeated failures needs review because it can indicate a guessed credential or a legitimate user recovering after mistakes.",
                    "Repeated authentication failures should not be followed by an unexplained successful login in the same review window.",
                    counters.authenticationFailures + " failure event(s) and " + counters.authenticationSuccesses
                            + " success event(s) occurred in the uploaded window.",
                    "Failure evidence: " + sanitize(counters.authenticationFailureSample)
                            + " | Success evidence: " + sanitize(counters.authenticationSuccessSample),
                    "Correlate the events by account, source address, and timestamp; reset exposed credentials and terminate sessions if the success is not legitimate."
            ));
        }
        if (counters.privilegedActivity > 0) {
            findings.add(createFinding(
                    asset, scanRun, riskContext, 50, "CS-LOG-003", "Privileged account activity detected",
                    "Root, UID 0, sudo, or equivalent privileged activity was present in the supplied logs.",
                    "Privileged actions should be rare, authorized, and traceable to an approved operator or service.",
                    counters.privilegedActivity + " privileged activity event(s) were observed.",
                    evidence(counters.privilegedActivity, counters.privilegedActivitySample),
                    "Verify the actor and command against change records, restrict unnecessary privilege, and investigate any activity without a valid owner."
            ));
        }
        if (counters.dockerAccess > 0) {
            findings.add(createFinding(
                    asset, scanRun, riskContext, 58, "CS-LOG-004", "Docker daemon access detected",
                    "Access to the Docker socket or daemon can provide control over containers and, in many deployments, the host.",
                    "Application processes should not access the Docker daemon unless that capability is explicitly approved.",
                    counters.dockerAccess + " Docker daemon or socket event(s) were observed.",
                    evidence(counters.dockerAccess, counters.dockerAccessSample),
                    "Confirm the requesting identity, remove unnecessary socket mounts, and restrict daemon access with least-privilege controls."
            ));
        }
        if (counters.sensitivePathRequests >= properties.getSensitivePathThreshold()) {
            findings.add(createFinding(
                    asset, scanRun, riskContext, 35, "CS-LOG-005", "Repeated sensitive endpoint requests",
                    "Repeated requests to administration, diagnostic, or commonly probed paths can indicate reconnaissance.",
                    "Sensitive endpoints should be inaccessible to untrusted clients and should not receive repeated unexpected requests.",
                    counters.sensitivePathRequests + " sensitive path request(s) were observed.",
                    evidence(counters.sensitivePathRequests, counters.sensitivePathSample),
                    "Review the client and requested paths, restrict management routes, and add rate limiting or web application firewall rules where appropriate."
            ));
        }
        if (counters.serverErrors >= properties.getServerErrorThreshold()) {
            findings.add(createFinding(
                    asset, scanRun, riskContext, 24, "CS-LOG-006", "Server error spike",
                    "A burst of HTTP 5xx responses, exceptions, or fatal errors can indicate an availability problem, an unsafe input path, or exploitation attempts.",
                    "Server errors should remain below the configured review threshold of "
                            + properties.getServerErrorThreshold() + " events per uploaded log window.",
                    counters.serverErrors + " server error event(s) were observed.",
                    evidence(counters.serverErrors, counters.serverErrorSample),
                    "Group the errors by route and exception, reproduce the most frequent cause, and inspect nearby requests for malicious input or abnormal traffic."
            ));
        }

        findingRepository.saveAll(findings);
        scanRun.setCompletedAt(LocalDateTime.now());
        scanRun.setStatus("COMPLETED");
        scanRun.setFindingsCount(findings.size());
        scanRun.setMaximumRiskScore(findings.stream().mapToInt(Finding::getRiskScore).max().orElse(0));
        scanRun = scanRunRepository.save(scanRun);
        return new LogAnalysisResult(scanRun, lines.size());
    }

    private void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Paste log text or choose a log file to analyze.");
        }
        if (content.getBytes(StandardCharsets.UTF_8).length > properties.getMaximumContentBytes()) {
            throw new IllegalArgumentException(
                    "The combined log input is larger than " + properties.getMaximumContentBytes() + " bytes. Upload a smaller time window."
            );
        }
    }

    private LogCounters inspect(List<String> lines) {
        LogCounters counters = new LogCounters();
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            if (AUTHENTICATION_FAILURE.matcher(line).find()) {
                counters.authenticationFailures++;
                counters.authenticationFailureSample = first(counters.authenticationFailureSample, line);
            }
            if (AUTHENTICATION_SUCCESS.matcher(line).find()) {
                counters.authenticationSuccesses++;
                counters.authenticationSuccessSample = first(counters.authenticationSuccessSample, line);
            }
            if (PRIVILEGED_ACTIVITY.matcher(line).find()) {
                counters.privilegedActivity++;
                counters.privilegedActivitySample = first(counters.privilegedActivitySample, line);
            }
            if (DOCKER_ACCESS.matcher(line).find()) {
                counters.dockerAccess++;
                counters.dockerAccessSample = first(counters.dockerAccessSample, line);
            }
            if (SENSITIVE_PATH.matcher(line).find()) {
                counters.sensitivePathRequests++;
                counters.sensitivePathSample = first(counters.sensitivePathSample, line);
            }
            if (SERVER_ERROR.matcher(line).find()) {
                counters.serverErrors++;
                counters.serverErrorSample = first(counters.serverErrorSample, line);
            }
        }
        return counters;
    }

    private Finding createFinding(
            ApplicationAsset asset,
            ScanRun scanRun,
            ScanRequest riskContext,
            int baseRisk,
            String ruleId,
            String title,
            String description,
            String expectedState,
            String observedState,
            String evidence,
            String remediation
    ) {
        int score = riskScoringService.calculate(baseRisk, asset, riskContext);
        Finding finding = new Finding();
        finding.setAsset(asset);
        finding.setScanRun(scanRun);
        finding.setRuleId(ruleId);
        finding.setTitle(title);
        finding.setDescription(description);
        finding.setExpectedState(expectedState);
        finding.setObservedState(observedState);
        finding.setEvidence(evidence);
        finding.setRemediation(remediation);
        finding.setRiskScore(score);
        finding.setSeverity(riskScoringService.severityFor(score));
        finding.setStatus(FindingStatus.OPEN);
        finding.setCreatedAt(LocalDateTime.now());
        return finding;
    }

    private String evidence(int count, String sample) {
        return "Matched events: " + count + " | Redacted example: " + sanitize(sample);
    }

    private String first(String current, String candidate) {
        return current == null ? candidate : current;
    }

    private String sanitize(String line) {
        if (line == null || line.isBlank()) {
            return "No example retained";
        }
        String singleLine = line.replaceAll("[\\r\\n\\t]+", " ").trim();
        String redacted = AUTHORIZATION_VALUE.matcher(singleLine).replaceAll("Authorization=[REDACTED]");
        redacted = SECRET_VALUE.matcher(redacted).replaceAll("$1=[REDACTED]");
        return redacted.length() > 220 ? redacted.substring(0, 220) + "…" : redacted;
    }

    private String normalizeSourceReference(String sourceReference) {
        String value = sourceReference == null || sourceReference.isBlank() ? "pasted log text" : sourceReference.trim();
        value = value.replaceAll("[\\r\\n\\t]+", " ");
        if (value.length() > 180) {
            value = value.substring(0, 180);
        }
        return "Log analysis: " + value;
    }

    private static final class LogCounters {
        private int authenticationFailures;
        private int authenticationSuccesses;
        private int privilegedActivity;
        private int dockerAccess;
        private int sensitivePathRequests;
        private int serverErrors;
        private String authenticationFailureSample;
        private String authenticationSuccessSample;
        private String privilegedActivitySample;
        private String dockerAccessSample;
        private String sensitivePathSample;
        private String serverErrorSample;
    }
}
