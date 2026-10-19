package com.codeshield.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;

import java.time.LocalDateTime;

@Entity
public class Finding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private ApplicationAsset asset;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private ScanRun scanRun;

    private String ruleId;
    private String title;

    @Lob
    private String description;

    @Lob
    private String expectedState;

    @Lob
    private String observedState;

    @Lob
    private String evidence;

    @Lob
    private String remediation;

    @Enumerated(EnumType.STRING)
    private Severity severity;

    private int riskScore;

    @Enumerated(EnumType.STRING)
    private FindingStatus status = FindingStatus.OPEN;

    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ApplicationAsset getAsset() {
        return asset;
    }

    public void setAsset(ApplicationAsset asset) {
        this.asset = asset;
    }

    public ScanRun getScanRun() {
        return scanRun;
    }

    public void setScanRun(ScanRun scanRun) {
        this.scanRun = scanRun;
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getExpectedState() {
        return expectedState;
    }

    public void setExpectedState(String expectedState) {
        this.expectedState = expectedState;
    }

    public String getObservedState() {
        return observedState;
    }

    public void setObservedState(String observedState) {
        this.observedState = observedState;
    }

    public String getEvidence() {
        return evidence;
    }

    public void setEvidence(String evidence) {
        this.evidence = evidence;
    }

    public String getRemediation() {
        return remediation;
    }

    public void setRemediation(String remediation) {
        this.remediation = remediation;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public int getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(int riskScore) {
        this.riskScore = riskScore;
    }

    public FindingStatus getStatus() {
        return status;
    }

    public void setStatus(FindingStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
