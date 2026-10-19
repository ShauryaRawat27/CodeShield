package com.codeshield.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("codeshield.rules")
public class DriftRuleProperties {

    private int runtimeVersionBaseRisk;
    private int publicDatabaseBaseRisk;
    private int unexpectedRootUserBaseRisk;
    private int unexpectedUserBaseRisk;
    private int dockerSocketBaseRisk;
    private int imageDigestBaseRisk;
    private int dependencyKnownExploitedBaseRisk;
    private int dependencyOtherBaseRisk;

    public int getRuntimeVersionBaseRisk() {
        return runtimeVersionBaseRisk;
    }

    public void setRuntimeVersionBaseRisk(int runtimeVersionBaseRisk) {
        this.runtimeVersionBaseRisk = runtimeVersionBaseRisk;
    }

    public int getPublicDatabaseBaseRisk() {
        return publicDatabaseBaseRisk;
    }

    public void setPublicDatabaseBaseRisk(int publicDatabaseBaseRisk) {
        this.publicDatabaseBaseRisk = publicDatabaseBaseRisk;
    }

    public int getUnexpectedRootUserBaseRisk() {
        return unexpectedRootUserBaseRisk;
    }

    public void setUnexpectedRootUserBaseRisk(int unexpectedRootUserBaseRisk) {
        this.unexpectedRootUserBaseRisk = unexpectedRootUserBaseRisk;
    }

    public int getUnexpectedUserBaseRisk() {
        return unexpectedUserBaseRisk;
    }

    public void setUnexpectedUserBaseRisk(int unexpectedUserBaseRisk) {
        this.unexpectedUserBaseRisk = unexpectedUserBaseRisk;
    }

    public int getDockerSocketBaseRisk() {
        return dockerSocketBaseRisk;
    }

    public void setDockerSocketBaseRisk(int dockerSocketBaseRisk) {
        this.dockerSocketBaseRisk = dockerSocketBaseRisk;
    }

    public int getImageDigestBaseRisk() {
        return imageDigestBaseRisk;
    }

    public void setImageDigestBaseRisk(int imageDigestBaseRisk) {
        this.imageDigestBaseRisk = imageDigestBaseRisk;
    }

    public int getDependencyKnownExploitedBaseRisk() {
        return dependencyKnownExploitedBaseRisk;
    }

    public void setDependencyKnownExploitedBaseRisk(int dependencyKnownExploitedBaseRisk) {
        this.dependencyKnownExploitedBaseRisk = dependencyKnownExploitedBaseRisk;
    }

    public int getDependencyOtherBaseRisk() {
        return dependencyOtherBaseRisk;
    }

    public void setDependencyOtherBaseRisk(int dependencyOtherBaseRisk) {
        this.dependencyOtherBaseRisk = dependencyOtherBaseRisk;
    }
}
