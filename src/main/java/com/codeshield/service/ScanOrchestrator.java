package com.codeshield.service;

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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ScanOrchestrator {

    private final ApplicationAssetRepository assetRepository;
    private final ScanRunRepository scanRunRepository;
    private final FindingRepository findingRepository;
    private final DriftDetectionService driftDetectionService;
    private final RiskScoringService riskScoringService;

    public ScanOrchestrator(
            ApplicationAssetRepository assetRepository,
            ScanRunRepository scanRunRepository,
            FindingRepository findingRepository,
            DriftDetectionService driftDetectionService,
            RiskScoringService riskScoringService
    ) {
        this.assetRepository = assetRepository;
        this.scanRunRepository = scanRunRepository;
        this.findingRepository = findingRepository;
        this.driftDetectionService = driftDetectionService;
        this.riskScoringService = riskScoringService;
    }

    @Transactional
    public ScanRun run(ScanRequest request) {
        ApplicationAsset asset = assetRepository.findById(request.getAssetId())
                .orElseThrow(() -> new EntityNotFoundException("Application asset not found"));

        ScanRun scanRun = new ScanRun();
        scanRun.setAsset(asset);
        scanRun.setStartedAt(LocalDateTime.now());
        scanRun.setStatus("RUNNING");
        scanRun.setSourceReference(request.getEvidenceSource());
        scanRun = scanRunRepository.save(scanRun);

        List<Finding> findings = new ArrayList<>();
        int maximumRisk = 0;
        for (RuleResult result : driftDetectionService.detect(asset, request)) {
            int score = riskScoringService.calculate(result.baseRisk(), asset, request);
            maximumRisk = Math.max(maximumRisk, score);

            Finding finding = new Finding();
            finding.setAsset(asset);
            finding.setScanRun(scanRun);
            finding.setRuleId(result.ruleId());
            finding.setTitle(result.title());
            finding.setDescription(result.description());
            finding.setExpectedState(result.expectedState());
            finding.setObservedState(result.observedState());
            finding.setEvidence(result.evidence());
            finding.setRemediation(result.remediation());
            finding.setRiskScore(score);
            finding.setSeverity(riskScoringService.severityFor(score));
            finding.setStatus(FindingStatus.OPEN);
            finding.setCreatedAt(LocalDateTime.now());
            findings.add(finding);
        }
        findingRepository.saveAll(findings);

        scanRun.setCompletedAt(LocalDateTime.now());
        scanRun.setStatus("COMPLETED");
        scanRun.setFindingsCount(findings.size());
        scanRun.setMaximumRiskScore(maximumRisk);
        return scanRunRepository.save(scanRun);
    }

}
