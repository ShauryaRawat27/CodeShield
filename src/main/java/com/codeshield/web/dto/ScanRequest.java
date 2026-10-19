package com.codeshield.web.dto;

import com.codeshield.domain.Exposure;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ScanRequest {

    @NotNull
    private Long assetId;

    @NotBlank
    private String javaVersion;

    @NotNull
    private Exposure databaseExposure;

    @NotBlank
    private String containerUser;

    private boolean dockerSocketMounted;

    @NotBlank
    private String imageDigest;

    @NotBlank
    private String dependencyVersion;

    private boolean internetFacing;
    private boolean knownExploited;
    private String evidenceSource;

    public Long getAssetId() {
        return assetId;
    }

    public void setAssetId(Long assetId) {
        this.assetId = assetId;
    }

    public String getJavaVersion() {
        return javaVersion;
    }

    public void setJavaVersion(String javaVersion) {
        this.javaVersion = javaVersion;
    }

    public Exposure getDatabaseExposure() {
        return databaseExposure;
    }

    public void setDatabaseExposure(Exposure databaseExposure) {
        this.databaseExposure = databaseExposure;
    }

    public String getContainerUser() {
        return containerUser;
    }

    public void setContainerUser(String containerUser) {
        this.containerUser = containerUser;
    }

    public boolean isDockerSocketMounted() {
        return dockerSocketMounted;
    }

    public void setDockerSocketMounted(boolean dockerSocketMounted) {
        this.dockerSocketMounted = dockerSocketMounted;
    }

    public String getImageDigest() {
        return imageDigest;
    }

    public void setImageDigest(String imageDigest) {
        this.imageDigest = imageDigest;
    }

    public String getDependencyVersion() {
        return dependencyVersion;
    }

    public void setDependencyVersion(String dependencyVersion) {
        this.dependencyVersion = dependencyVersion;
    }

    public boolean isInternetFacing() {
        return internetFacing;
    }

    public void setInternetFacing(boolean internetFacing) {
        this.internetFacing = internetFacing;
    }

    public boolean isKnownExploited() {
        return knownExploited;
    }

    public void setKnownExploited(boolean knownExploited) {
        this.knownExploited = knownExploited;
    }

    public String getEvidenceSource() {
        return evidenceSource;
    }

    public void setEvidenceSource(String evidenceSource) {
        this.evidenceSource = evidenceSource;
    }
}
