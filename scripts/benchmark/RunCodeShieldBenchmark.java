package com.codeshield.benchmark;

import com.codeshield.config.DriftRuleProperties;
import com.codeshield.config.RiskScoringProperties;
import com.codeshield.domain.ApplicationAsset;
import com.codeshield.domain.Criticality;
import com.codeshield.domain.Exposure;
import com.codeshield.service.DriftDetectionService;
import com.codeshield.service.RiskScoringService;
import com.codeshield.service.RuleResult;
import com.codeshield.web.dto.ScanRequest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class RunCodeShieldBenchmark {

    private RunCodeShieldBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: RunCodeShieldBenchmark <benchmark.tsv>");
        }

        DriftDetectionService detector = new DriftDetectionService(driftProperties());
        RiskScoringService scorer = new RiskScoringService(riskProperties());
        List<String> lines = Files.readAllLines(Path.of(args[0]));

        System.out.println("case_id,predicted_rule_ids,predicted_rule_scores,max_risk,highest_severity");
        for (int lineNumber = 1; lineNumber < lines.size(); lineNumber++) {
            String[] value = lines.get(lineNumber).split("\\t", -1);
            ApplicationAsset asset = new ApplicationAsset();
            asset.setCriticality(Criticality.valueOf(value[1]));
            asset.setExpectedJavaVersion(value[2]);
            asset.setExpectedDatabaseExposure(Exposure.valueOf(value[4]));
            asset.setExpectedContainerUser(value[6]);
            asset.setExpectedImageDigest(value[9]);
            asset.setExpectedDependencyVersion(value[11]);

            ScanRequest request = new ScanRequest();
            request.setJavaVersion(value[3]);
            request.setDatabaseExposure(Exposure.valueOf(value[5]));
            request.setContainerUser(value[7]);
            request.setDockerSocketMounted(Boolean.parseBoolean(value[8]));
            request.setImageDigest(value[10]);
            request.setDependencyVersion(value[12]);
            request.setInternetFacing(Boolean.parseBoolean(value[13]));
            request.setKnownExploited(Boolean.parseBoolean(value[14]));
            request.setEvidenceSource("Benchmark case " + value[0]);

            List<RuleResult> results = detector.detect(asset, request);
            List<String> ruleIds = new ArrayList<>();
            List<String> ruleScores = new ArrayList<>();
            int maximumRisk = 0;
            for (RuleResult result : results) {
                int score = scorer.calculate(result.baseRisk(), asset, request);
                ruleIds.add(result.ruleId());
                ruleScores.add(result.ruleId() + "=" + score);
                maximumRisk = Math.max(maximumRisk, score);
            }
            String severity = results.isEmpty() ? "NONE" : scorer.severityFor(maximumRisk).name();
            System.out.printf(
                    "%s,%s,%s,%d,%s%n",
                    value[0], String.join("; ", ruleIds), String.join("; ", ruleScores), maximumRisk, severity
            );
        }
    }

    private static DriftRuleProperties driftProperties() {
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
