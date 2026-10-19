package com.codeshield.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;

@ConfigurationProperties("codeshield.repository")
public class RepositoryInspectionProperties {

    private long maximumFileSizeBytes;
    private int maximumSearchDepth;
    private int cloneDepth;
    private int cloneTimeoutSeconds;
    private Set<String> ignoredDirectories = new LinkedHashSet<>();
    private Set<String> databasePorts = new LinkedHashSet<>();

    public long getMaximumFileSizeBytes() {
        return maximumFileSizeBytes;
    }

    public void setMaximumFileSizeBytes(long maximumFileSizeBytes) {
        this.maximumFileSizeBytes = maximumFileSizeBytes;
    }

    public int getMaximumSearchDepth() {
        return maximumSearchDepth;
    }

    public void setMaximumSearchDepth(int maximumSearchDepth) {
        this.maximumSearchDepth = maximumSearchDepth;
    }

    public int getCloneDepth() {
        return cloneDepth;
    }

    public void setCloneDepth(int cloneDepth) {
        this.cloneDepth = cloneDepth;
    }

    public int getCloneTimeoutSeconds() {
        return cloneTimeoutSeconds;
    }

    public void setCloneTimeoutSeconds(int cloneTimeoutSeconds) {
        this.cloneTimeoutSeconds = cloneTimeoutSeconds;
    }

    public Set<String> getIgnoredDirectories() {
        return ignoredDirectories;
    }

    public void setIgnoredDirectories(Set<String> ignoredDirectories) {
        this.ignoredDirectories = ignoredDirectories;
    }

    public Set<String> getDatabasePorts() {
        return databasePorts;
    }

    public void setDatabasePorts(Set<String> databasePorts) {
        this.databasePorts = databasePorts;
    }
}
