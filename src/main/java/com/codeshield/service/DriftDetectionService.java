package com.codeshield.service;

import com.codeshield.config.DriftRuleProperties;
import com.codeshield.domain.ApplicationAsset;
import com.codeshield.domain.Exposure;
import com.codeshield.web.dto.ScanRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class DriftDetectionService {

    private final DriftRuleProperties properties;

    public DriftDetectionService(DriftRuleProperties properties) {
        this.properties = properties;
    }

    public List<RuleResult> detect(ApplicationAsset asset, ScanRequest request) {
        List<RuleResult> results = new ArrayList<>();

        if (!equalsIgnoreCase(asset.getExpectedJavaVersion(), request.getJavaVersion())) {
            results.add(new RuleResult(
                    "CS-RUNTIME-001",
                    "Java runtime version drift",
                    "The deployed Java runtime does not match the version declared for the application.",
                    "Java " + asset.getExpectedJavaVersion(),
                    "Java " + request.getJavaVersion(),
                    evidence(request, "Detected java.version=" + request.getJavaVersion()),
                    "Deploy the approved Java runtime or update the application baseline after compatibility testing.",
                    properties.getRuntimeVersionBaseRisk()
            ));
        }

        if (asset.getExpectedDatabaseExposure() == Exposure.INTERNAL
                && request.getDatabaseExposure() == Exposure.PUBLIC) {
            results.add(new RuleResult(
                    "CS-NETWORK-001",
                    "Internal database is publicly exposed",
                    "A database intended for an internal trust boundary is reachable from a public network.",
                    "Database exposure: INTERNAL",
                    "Database exposure: PUBLIC",
                    evidence(request, "The database listener is exposed through a public port mapping."),
                    "Remove the public port mapping and restrict the database to the application network.",
                    properties.getPublicDatabaseBaseRisk()
            ));
        }

        if (!equalsIgnoreCase(asset.getExpectedContainerUser(), request.getContainerUser())) {
            results.add(new RuleResult(
                    "CS-CONTAINER-001",
                    "Container is running with an unexpected user",
                    "The runtime user differs from the non-root user defined by the application baseline.",
                    "Container user: " + asset.getExpectedContainerUser(),
                    "Container user: " + request.getContainerUser(),
                    evidence(request, "Detected container user=" + request.getContainerUser()),
                    "Set a non-root USER in the image and enforce it in the deployment configuration.",
                    "root".equalsIgnoreCase(request.getContainerUser())
                            ? properties.getUnexpectedRootUserBaseRisk()
                            : properties.getUnexpectedUserBaseRisk()
            ));
        }

        if (request.isDockerSocketMounted()) {
            results.add(new RuleResult(
                    "CS-CONTAINER-002",
                    "Docker socket mounted inside application container",
                    "The container can communicate with the host Docker daemon, creating a potential host-control path.",
                    "No Docker socket mount",
                    "/var/run/docker.sock mounted",
                    evidence(request, "A compose volume includes /var/run/docker.sock."),
                    "Remove the socket mount or place a restricted read-only proxy between the workload and Docker API.",
                    properties.getDockerSocketBaseRisk()
            ));
        }

        if (!Objects.equals(asset.getExpectedImageDigest(), request.getImageDigest())) {
            results.add(new RuleResult(
                    "CS-SUPPLY-001",
                    "Running image does not match the approved digest",
                    "The deployed container image differs from the approved immutable image reference.",
                    asset.getExpectedImageDigest(),
                    request.getImageDigest(),
                    evidence(request, "Detected image reference " + request.getImageDigest()),
                    "Redeploy the approved image or formally approve and record the new digest.",
                    properties.getImageDigestBaseRisk()
            ));
        }

        if (!equalsIgnoreCase(asset.getExpectedDependencyVersion(), request.getDependencyVersion())) {
            results.add(new RuleResult(
                    "CS-DEPENDENCY-001",
                    "Dependency version differs from the application baseline",
                    "The observed framework or dependency version does not match the approved build record.",
                    "Version " + asset.getExpectedDependencyVersion(),
                    "Version " + request.getDependencyVersion(),
                    evidence(request, "Detected framework or dependency version " + request.getDependencyVersion()),
                    "Rebuild from the approved lock file or update the baseline after vulnerability review.",
                    request.isKnownExploited()
                            ? properties.getDependencyKnownExploitedBaseRisk()
                            : properties.getDependencyOtherBaseRisk()
            ));
        }

        return results;
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private String evidence(ScanRequest request, String detail) {
        String source = request.getEvidenceSource();
        if (source == null || source.isBlank()) {
            source = "Manual observation";
        }
        return source + ". " + detail;
    }
}
