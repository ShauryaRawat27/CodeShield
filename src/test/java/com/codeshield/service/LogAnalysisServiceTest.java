package com.codeshield.service;

import com.codeshield.config.LogAnalysisProperties;
import com.codeshield.config.RiskScoringProperties;
import com.codeshield.domain.ApplicationAsset;
import com.codeshield.domain.Criticality;
import com.codeshield.domain.Finding;
import com.codeshield.domain.ScanRun;
import com.codeshield.repository.ApplicationAssetRepository;
import com.codeshield.repository.FindingRepository;
import com.codeshield.repository.ScanRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class LogAnalysisServiceTest {

    @Mock
    private ApplicationAssetRepository assetRepository;

    @Mock
    private ScanRunRepository scanRunRepository;

    @Mock
    private FindingRepository findingRepository;

    private LogAnalysisService service;
    private List<Finding> savedFindings;

    @BeforeEach
    void setUp() {
        ApplicationAsset asset = new ApplicationAsset();
        asset.setId(1L);
        asset.setCriticality(Criticality.HIGH);
        lenient().when(assetRepository.findById(1L)).thenReturn(Optional.of(asset));
        lenient().when(scanRunRepository.save(any(ScanRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

        savedFindings = new ArrayList<>();
        lenient().when(findingRepository.saveAll(any())).thenAnswer(invocation -> {
            Iterable<Finding> findings = invocation.getArgument(0);
            findings.forEach(savedFindings::add);
            return savedFindings;
        });

        service = new LogAnalysisService(
                assetRepository,
                scanRunRepository,
                findingRepository,
                new RiskScoringService(riskProperties()),
                logProperties()
        );
    }

    @Test
    void aggregatesRealLogLinesIntoSixExplainableSignals() {
        String log = String.join("\n",
                "WARN unauthorized Authorization: Bearer secret-one",
                "WARN Authentication failed user=alice",
                "WARN login failed user=alice",
                "WARN invalid credentials user=alice",
                "WARN status=401 path=/login",
                "INFO authentication succeeded user=alice",
                "AUDIT user=root command=id",
                "WARN request to /var/run/docker.sock",
                "GET /admin 403",
                "GET /admin/settings 403",
                "GET /.env 404",
                "ERROR IllegalStateException request failed",
                "ERROR NullPointerException request failed",
                "ERROR DatabaseException request failed",
                "ERROR SecurityException request failed",
                "ERROR TimeoutException request failed",
                "ERROR IOException request failed",
                "ERROR RuntimeException request failed",
                "ERROR AuthenticationException request failed",
                "ERROR ConfigurationException request failed",
                "ERROR fatal error while serving request"
        );

        LogAnalysisResult result = service.analyze(1L, log, true, "production.log");

        assertThat(result.processedLines()).isEqualTo(21);
        assertThat(result.scanRun().getFindingsCount()).isEqualTo(6);
        assertThat(result.scanRun().getMaximumRiskScore()).isEqualTo(88);
        assertThat(result.scanRun().getSourceReference()).isEqualTo("Log analysis: production.log");
        assertThat(savedFindings).extracting(Finding::getRuleId)
                .containsExactly("CS-LOG-001", "CS-LOG-002", "CS-LOG-003", "CS-LOG-004", "CS-LOG-005", "CS-LOG-006");
        assertThat(savedFindings.getFirst().getEvidence())
                .contains("Matched events: 5")
                .contains("Authorization=[REDACTED]")
                .doesNotContain("secret-one");
    }

    @Test
    void completesAQuietLogWindowWithoutCreatingFindings() {
        LogAnalysisResult result = service.analyze(
                1L,
                "INFO service started\nINFO health check passed\nINFO request completed status=200",
                false,
                "app.log"
        );

        assertThat(result.processedLines()).isEqualTo(3);
        assertThat(result.scanRun().getFindingsCount()).isZero();
        assertThat(result.scanRun().getMaximumRiskScore()).isZero();
        assertThat(savedFindings).isEmpty();
    }

    @Test
    void rejectsInputBeyondTheConfiguredLineLimit() {
        LogAnalysisProperties properties = logProperties();
        properties.setMaximumLines(2);
        service = new LogAnalysisService(
                assetRepository,
                scanRunRepository,
                findingRepository,
                new RiskScoringService(riskProperties()),
                properties
        );

        assertThatThrownBy(() -> service.analyze(1L, "one\ntwo\nthree", false, "large.log"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("more than 2 lines");
    }

    private static LogAnalysisProperties logProperties() {
        LogAnalysisProperties properties = new LogAnalysisProperties();
        properties.setMaximumContentBytes(2_000_000);
        properties.setMaximumLines(20_000);
        properties.setAuthenticationFailureThreshold(5);
        properties.setSensitivePathThreshold(3);
        properties.setServerErrorThreshold(10);
        return properties;
    }

    private static RiskScoringProperties riskProperties() {
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
}
