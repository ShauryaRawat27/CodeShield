package com.codeshield.service;

import com.codeshield.domain.FindingStatus;
import com.codeshield.domain.Severity;
import com.codeshield.repository.ApplicationAssetRepository;
import com.codeshield.repository.FindingRepository;
import com.codeshield.repository.ScanRunRepository;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private final ApplicationAssetRepository assetRepository;
    private final FindingRepository findingRepository;
    private final ScanRunRepository scanRunRepository;

    public DashboardService(
            ApplicationAssetRepository assetRepository,
            FindingRepository findingRepository,
            ScanRunRepository scanRunRepository
    ) {
        this.assetRepository = assetRepository;
        this.findingRepository = findingRepository;
        this.scanRunRepository = scanRunRepository;
    }

    public DashboardStats stats() {
        return new DashboardStats(
                assetRepository.count(),
                scanRunRepository.count(),
                findingRepository.countByStatus(FindingStatus.OPEN),
                findingRepository.countBySeverityAndStatus(Severity.CRITICAL, FindingStatus.OPEN)
        );
    }

    public record DashboardStats(long assets, long scans, long openFindings, long criticalFindings) {
    }
}
