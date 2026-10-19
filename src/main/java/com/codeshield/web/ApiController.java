package com.codeshield.web;

import com.codeshield.domain.ApplicationAsset;
import com.codeshield.domain.Finding;
import com.codeshield.domain.ScanRun;
import com.codeshield.repository.ApplicationAssetRepository;
import com.codeshield.repository.FindingRepository;
import com.codeshield.repository.ScanRunRepository;
import com.codeshield.service.ScanOrchestrator;
import com.codeshield.web.dto.ScanRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final ApplicationAssetRepository assetRepository;
    private final FindingRepository findingRepository;
    private final ScanRunRepository scanRunRepository;
    private final ScanOrchestrator scanOrchestrator;

    public ApiController(
            ApplicationAssetRepository assetRepository,
            FindingRepository findingRepository,
            ScanRunRepository scanRunRepository,
            ScanOrchestrator scanOrchestrator
    ) {
        this.assetRepository = assetRepository;
        this.findingRepository = findingRepository;
        this.scanRunRepository = scanRunRepository;
        this.scanOrchestrator = scanOrchestrator;
    }

    @GetMapping("/assets")
    public List<AssetResponse> assets() {
        return assetRepository.findAll().stream().map(AssetResponse::from).toList();
    }

    @GetMapping("/findings")
    public List<FindingResponse> findings() {
        return findingRepository.findAllByOrderByRiskScoreDescCreatedAtDesc().stream()
                .map(FindingResponse::from)
                .toList();
    }

    @GetMapping("/scans")
    public List<ScanResponse> scans() {
        return scanRunRepository.findTop10ByOrderByStartedAtDesc().stream().map(ScanResponse::from).toList();
    }

    @PostMapping("/scans")
    public ScanResponse runScan(@Valid @RequestBody ScanRequest request) {
        return ScanResponse.from(scanOrchestrator.run(request));
    }

    public record AssetResponse(Long id, String name, String owner, String criticality) {
        static AssetResponse from(ApplicationAsset asset) {
            return new AssetResponse(asset.getId(), asset.getName(), asset.getOwner(), asset.getCriticality().name());
        }
    }

    public record FindingResponse(
            Long id,
            String ruleId,
            String asset,
            String title,
            String severity,
            int riskScore,
            String status,
            String expectedState,
            String observedState,
            LocalDateTime createdAt
    ) {
        static FindingResponse from(Finding finding) {
            return new FindingResponse(
                    finding.getId(),
                    finding.getRuleId(),
                    finding.getAsset().getName(),
                    finding.getTitle(),
                    finding.getSeverity().name(),
                    finding.getRiskScore(),
                    finding.getStatus().name(),
                    finding.getExpectedState(),
                    finding.getObservedState(),
                    finding.getCreatedAt()
            );
        }
    }

    public record ScanResponse(
            Long id,
            String asset,
            String status,
            String sourceReference,
            int findingsCount,
            int maximumRiskScore,
            LocalDateTime completedAt
    ) {
        static ScanResponse from(ScanRun scan) {
            return new ScanResponse(
                    scan.getId(),
                    scan.getAsset().getName(),
                    scan.getStatus(),
                    scan.getSourceReference(),
                    scan.getFindingsCount(),
                    scan.getMaximumRiskScore(),
                    scan.getCompletedAt()
            );
        }
    }
}
