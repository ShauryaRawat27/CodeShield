package com.codeshield.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
public class ApplicationAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String name;

    @NotBlank
    private String owner;

    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    private Criticality criticality;

    @NotBlank
    private String expectedJavaVersion;

    @NotBlank
    private String expectedImageDigest;

    @NotNull
    @Enumerated(EnumType.STRING)
    private Exposure expectedDatabaseExposure;

    @NotBlank
    private String expectedContainerUser;

    @NotBlank
    private String expectedDependencyVersion;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Criticality getCriticality() {
        return criticality;
    }

    public void setCriticality(Criticality criticality) {
        this.criticality = criticality;
    }

    public String getExpectedJavaVersion() {
        return expectedJavaVersion;
    }

    public void setExpectedJavaVersion(String expectedJavaVersion) {
        this.expectedJavaVersion = expectedJavaVersion;
    }

    public String getExpectedImageDigest() {
        return expectedImageDigest;
    }

    public void setExpectedImageDigest(String expectedImageDigest) {
        this.expectedImageDigest = expectedImageDigest;
    }

    public Exposure getExpectedDatabaseExposure() {
        return expectedDatabaseExposure;
    }

    public void setExpectedDatabaseExposure(Exposure expectedDatabaseExposure) {
        this.expectedDatabaseExposure = expectedDatabaseExposure;
    }

    public String getExpectedContainerUser() {
        return expectedContainerUser;
    }

    public void setExpectedContainerUser(String expectedContainerUser) {
        this.expectedContainerUser = expectedContainerUser;
    }

    public String getExpectedDependencyVersion() {
        return expectedDependencyVersion;
    }

    public void setExpectedDependencyVersion(String expectedDependencyVersion) {
        this.expectedDependencyVersion = expectedDependencyVersion;
    }
}
