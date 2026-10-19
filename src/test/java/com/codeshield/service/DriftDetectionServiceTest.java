package com.codeshield.service;

import com.codeshield.config.DriftRuleProperties;
import com.codeshield.domain.ApplicationAsset;
import com.codeshield.domain.Criticality;
import com.codeshield.domain.Exposure;
import com.codeshield.web.dto.ScanRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DriftDetectionServiceTest {

    private DriftDetectionService service;
    private ApplicationAsset asset;

    @BeforeEach
    void setUp() {
        service = new DriftDetectionService(properties());
        asset = new ApplicationAsset();
        asset.setName("Test Service");
        asset.setOwner("Security Team");
        asset.setCriticality(Criticality.HIGH);
        asset.setExpectedJavaVersion("21");
        asset.setExpectedDatabaseExposure(Exposure.INTERNAL);
        asset.setExpectedContainerUser("appuser");
        asset.setExpectedImageDigest("sha256:approved");
        asset.setExpectedDependencyVersion("3.3.6");
    }

    private DriftRuleProperties properties() {
        DriftRuleProperties properties = new DriftRuleProperties();
        properties.setRuntimeVersionBaseRisk(30);
        properties.setPublicDatabaseBaseRisk(52);
        properties.setUnexpectedRootUserBaseRisk(42);
        properties.setUnexpectedUserBaseRisk(25);
        properties.setDockerSocketBaseRisk(58);
        properties.setImageDigestBaseRisk(38);
        properties.setDependencyKnownExploitedBaseRisk(48);
        properties.setDependencyOtherBaseRisk(24);
        return properties;
    }

    @Test
    void detectsAllSupportedDriftTypes() {
        ScanRequest request = new ScanRequest();
        request.setAssetId(1L);
        request.setJavaVersion("17");
        request.setDatabaseExposure(Exposure.PUBLIC);
        request.setContainerUser("root");
        request.setDockerSocketMounted(true);
        request.setImageDigest("sha256:unknown");
        request.setDependencyVersion("2.7.18");
        request.setKnownExploited(true);

        List<RuleResult> results = service.detect(asset, request);

        assertThat(results).hasSize(6);
        assertThat(results).extracting(RuleResult::ruleId).containsExactlyInAnyOrder(
                "CS-RUNTIME-001",
                "CS-NETWORK-001",
                "CS-CONTAINER-001",
                "CS-CONTAINER-002",
                "CS-SUPPLY-001",
                "CS-DEPENDENCY-001"
        );
    }

    @Test
    void producesNoFindingsWhenRuntimeMatchesIntent() {
        ScanRequest request = new ScanRequest();
        request.setAssetId(1L);
        request.setJavaVersion("21");
        request.setDatabaseExposure(Exposure.INTERNAL);
        request.setContainerUser("appuser");
        request.setDockerSocketMounted(false);
        request.setImageDigest("sha256:approved");
        request.setDependencyVersion("3.3.6");

        assertThat(service.detect(asset, request)).isEmpty();
    }
}
