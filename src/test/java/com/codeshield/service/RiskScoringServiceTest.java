package com.codeshield.service;

import com.codeshield.config.RiskScoringProperties;
import com.codeshield.domain.ApplicationAsset;
import com.codeshield.domain.Criticality;
import com.codeshield.domain.Severity;
import com.codeshield.web.dto.ScanRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RiskScoringServiceTest {

    private final RiskScoringService service = new RiskScoringService(properties());

    private static RiskScoringProperties properties() {
        RiskScoringProperties properties = new RiskScoringProperties();
        properties.setLowCriticalityWeight(0);
        properties.setMediumCriticalityWeight(8);
        properties.setHighCriticalityWeight(15);
        properties.setCriticalCriticalityWeight(22);
        properties.setInternetFacingWeight(15);
        properties.setKnownExploitedWeight(18);
        properties.setMaximumScore(100);
        properties.setMediumThreshold(40);
        properties.setHighThreshold(65);
        properties.setCriticalThreshold(85);
        return properties;
    }

    @Test
    void contextRaisesTheScoreAndCapsItAtOneHundred() {
        ApplicationAsset asset = new ApplicationAsset();
        asset.setCriticality(Criticality.CRITICAL);
        ScanRequest request = new ScanRequest();
        request.setInternetFacing(true);
        request.setKnownExploited(true);

        int score = service.calculate(58, asset, request);

        assertThat(score).isEqualTo(100);
        assertThat(service.severityFor(score)).isEqualTo(Severity.CRITICAL);
    }

    @Test
    void mapsRiskBandsToSeverity() {
        assertThat(service.severityFor(20)).isEqualTo(Severity.LOW);
        assertThat(service.severityFor(40)).isEqualTo(Severity.MEDIUM);
        assertThat(service.severityFor(65)).isEqualTo(Severity.HIGH);
        assertThat(service.severityFor(85)).isEqualTo(Severity.CRITICAL);
    }
}
