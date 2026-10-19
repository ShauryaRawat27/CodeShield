package com.codeshield.service;

import com.codeshield.config.RiskScoringProperties;
import com.codeshield.domain.ApplicationAsset;
import com.codeshield.domain.Severity;
import com.codeshield.web.dto.ScanRequest;
import org.springframework.stereotype.Service;

@Service
public class RiskScoringService {

    private final RiskScoringProperties properties;

    public RiskScoringService(RiskScoringProperties properties) {
        this.properties = properties;
    }

    public int calculate(int baseRisk, ApplicationAsset asset, ScanRequest request) {
        int score = baseRisk;
        score += switch (asset.getCriticality()) {
            case LOW -> properties.getLowCriticalityWeight();
            case MEDIUM -> properties.getMediumCriticalityWeight();
            case HIGH -> properties.getHighCriticalityWeight();
            case CRITICAL -> properties.getCriticalCriticalityWeight();
        };
        if (request.isInternetFacing()) {
            score += properties.getInternetFacingWeight();
        }
        if (request.isKnownExploited()) {
            score += properties.getKnownExploitedWeight();
        }
        return Math.min(score, properties.getMaximumScore());
    }

    public Severity severityFor(int score) {
        if (score >= properties.getCriticalThreshold()) {
            return Severity.CRITICAL;
        }
        if (score >= properties.getHighThreshold()) {
            return Severity.HIGH;
        }
        if (score >= properties.getMediumThreshold()) {
            return Severity.MEDIUM;
        }
        return Severity.LOW;
    }
}
