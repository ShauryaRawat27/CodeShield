package com.codeshield.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("codeshield.risk")
public class RiskScoringProperties {

    private int lowCriticalityWeight;
    private int mediumCriticalityWeight;
    private int highCriticalityWeight;
    private int criticalCriticalityWeight;
    private int internetFacingWeight;
    private int knownExploitedWeight;
    private int maximumScore;
    private int mediumThreshold;
    private int highThreshold;
    private int criticalThreshold;

    public int getLowCriticalityWeight() {
        return lowCriticalityWeight;
    }

    public void setLowCriticalityWeight(int lowCriticalityWeight) {
        this.lowCriticalityWeight = lowCriticalityWeight;
    }

    public int getMediumCriticalityWeight() {
        return mediumCriticalityWeight;
    }

    public void setMediumCriticalityWeight(int mediumCriticalityWeight) {
        this.mediumCriticalityWeight = mediumCriticalityWeight;
    }

    public int getHighCriticalityWeight() {
        return highCriticalityWeight;
    }

    public void setHighCriticalityWeight(int highCriticalityWeight) {
        this.highCriticalityWeight = highCriticalityWeight;
    }

    public int getCriticalCriticalityWeight() {
        return criticalCriticalityWeight;
    }

    public void setCriticalCriticalityWeight(int criticalCriticalityWeight) {
        this.criticalCriticalityWeight = criticalCriticalityWeight;
    }

    public int getInternetFacingWeight() {
        return internetFacingWeight;
    }

    public void setInternetFacingWeight(int internetFacingWeight) {
        this.internetFacingWeight = internetFacingWeight;
    }

    public int getKnownExploitedWeight() {
        return knownExploitedWeight;
    }

    public void setKnownExploitedWeight(int knownExploitedWeight) {
        this.knownExploitedWeight = knownExploitedWeight;
    }

    public int getMaximumScore() {
        return maximumScore;
    }

    public void setMaximumScore(int maximumScore) {
        this.maximumScore = maximumScore;
    }

    public int getMediumThreshold() {
        return mediumThreshold;
    }

    public void setMediumThreshold(int mediumThreshold) {
        this.mediumThreshold = mediumThreshold;
    }

    public int getHighThreshold() {
        return highThreshold;
    }

    public void setHighThreshold(int highThreshold) {
        this.highThreshold = highThreshold;
    }

    public int getCriticalThreshold() {
        return criticalThreshold;
    }

    public void setCriticalThreshold(int criticalThreshold) {
        this.criticalThreshold = criticalThreshold;
    }
}
