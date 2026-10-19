package com.codeshield.service;

public record RuleResult(
        String ruleId,
        String title,
        String description,
        String expectedState,
        String observedState,
        String evidence,
        String remediation,
        int baseRisk
) {
}
