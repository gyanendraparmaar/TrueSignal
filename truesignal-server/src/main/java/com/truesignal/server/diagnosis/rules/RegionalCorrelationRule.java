package com.truesignal.server.diagnosis.rules;

import com.truesignal.common.enums.CheckStatus;
import com.truesignal.common.enums.DiagnosisCategory;
import com.truesignal.common.enums.DiagnosisConfidence;
import com.truesignal.server.diagnosis.DiagnosisResult;
import com.truesignal.server.diagnosis.DiagnosisRule;
import com.truesignal.server.entity.CheckResultEntity;
import com.truesignal.server.entity.MonitorEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(15)
public class RegionalCorrelationRule implements DiagnosisRule {

    @Override
    public Optional<DiagnosisResult> evaluate(
            MonitorEntity monitor,
            List<CheckResultEntity> failedResults,
            List<CheckResultEntity> recentHistory) {

        if (failedResults.size() < 2) {
            return Optional.empty();
        }

        Map<String, List<CheckResultEntity>> byRegion = failedResults.stream()
                .collect(Collectors.groupingBy(CheckResultEntity::getNodeRegion));

        long totalRegions = recentHistory.stream()
                .map(CheckResultEntity::getNodeRegion)
                .distinct()
                .count();

        long failedRegions = byRegion.size();

        if (totalRegions <= 1) {
            return Optional.empty();
        }

        if (failedRegions == 1 && totalRegions > 1) {
            String affectedRegion = byRegion.keySet().iterator().next();
            return Optional.of(new DiagnosisResult(
                    "Regional issue — failure only detected from " + affectedRegion,
                    DiagnosisCategory.REGIONAL,
                    DiagnosisConfidence.MEDIUM,
                    String.format(
                            "Only the %s monitoring region is reporting failures while other regions show the service as healthy. "
                                    + "This suggests a regional network issue, CDN edge server problem, or geo-specific routing failure rather than a target server outage.",
                            affectedRegion),
                    "Check if the target uses geo-routing or CDN. Investigate regional network connectivity. This may be a transient ISP or cloud region issue.",
                    "RULE_BASED"));
        }

        if (failedRegions == totalRegions) {
            return Optional.of(new DiagnosisResult(
                    "Global outage — all monitoring regions report failure",
                    DiagnosisCategory.NETWORK,
                    DiagnosisConfidence.HIGH,
                    String.format(
                            "All %d monitoring regions are reporting the target as down. "
                                    + "This confirms a server-side outage rather than a network or monitoring issue.",
                            failedRegions),
                    "The target service itself is experiencing an outage. Check the target server directly — review application logs, server health, and infrastructure status.",
                    "RULE_BASED"));
        }

        return Optional.empty();
    }

    @Override
    public int priority() {
        return 15;
    }
}
