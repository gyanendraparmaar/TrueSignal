package com.truesignal.server.diagnosis.rules;

import com.truesignal.common.enums.CheckStatus;
import com.truesignal.common.enums.DiagnosisCategory;
import com.truesignal.common.enums.DiagnosisConfidence;
import com.truesignal.server.diagnosis.DiagnosisResult;
import com.truesignal.server.diagnosis.DiagnosisRule;
import com.truesignal.server.entity.CheckResultEntity;
import com.truesignal.server.entity.MonitorEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
public class ResponseTimeRule implements DiagnosisRule {

    private static final double DEGRADATION_MULTIPLIER = 3.0;

    @Override
    public Optional<DiagnosisResult> evaluate(
            MonitorEntity monitor,
            List<CheckResultEntity> failedResults,
            List<CheckResultEntity> recentHistory) {

        List<CheckResultEntity> healthyChecks = recentHistory.stream()
                .filter(r -> r.getStatus() == CheckStatus.UP && r.getResponseTimeMs() > 0)
                .toList();

        if (healthyChecks.size() < 3) {
            return Optional.empty();
        }

        double avgHealthy = healthyChecks.stream()
                .mapToLong(CheckResultEntity::getResponseTimeMs)
                .average()
                .orElse(0);

        if (avgHealthy <= 0) {
            return Optional.empty();
        }

        double avgFailed = failedResults.stream()
                .filter(r -> r.getResponseTimeMs() > 0)
                .mapToLong(CheckResultEntity::getResponseTimeMs)
                .average()
                .orElse(0);

        if (avgFailed <= 0) {
            return Optional.empty();
        }

        if (avgFailed > avgHealthy * DEGRADATION_MULTIPLIER) {
            double ratio = avgFailed / avgHealthy;
            return Optional.of(new DiagnosisResult(
                    String.format(
                            "Severe performance degradation — response time %.0fx above baseline",
                            ratio),
                    DiagnosisCategory.OVERLOAD,
                    DiagnosisConfidence.MEDIUM,
                    String.format(
                            "Response time has increased dramatically from a baseline average of %.0fms to %.0fms (%.1fx increase). "
                                    + "This indicates the server is under heavy load, experiencing resource contention, or a dependent service is slow.",
                            avgHealthy,
                            avgFailed,
                            ratio),
                    "Check server CPU and memory utilization. Look for slow database queries. Review recent traffic patterns for unexpected spikes. Check dependent services for latency.",
                    "RULE_BASED"));
        }

        return Optional.empty();
    }

    @Override
    public int priority() {
        return 20;
    }
}
