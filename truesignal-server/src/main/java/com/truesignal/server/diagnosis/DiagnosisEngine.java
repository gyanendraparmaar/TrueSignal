package com.truesignal.server.diagnosis;

import com.truesignal.common.enums.DiagnosisCategory;
import com.truesignal.common.enums.DiagnosisConfidence;
import com.truesignal.server.entity.CheckResultEntity;
import com.truesignal.server.entity.MonitorEntity;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Hybrid diagnosis engine: tries rule-based analysis first, escalates to AI
 * for ambiguous or complex failures.
 *
 * <pre>
 * Check fails → Quorum confirms DOWN
 *     │
 *     ▼
 * Rule-Based Analyzers (fast, free, always works)
 *     │
 *     ├── HIGH confidence match → use directly
 *     │
 *     └── MEDIUM/LOW or no match
 *             │
 *             ▼
 *         AI Diagnosis (Gemini) if enabled
 *             │
 *             ├── success → use AI result
 *             └── failure → fall back to rule-based or generic
 * </pre>
 */
@Service
public class DiagnosisEngine {

    private static final Logger log = LoggerFactory.getLogger(DiagnosisEngine.class);

    private final List<DiagnosisRule> rules;
    private final AiDiagnosisService aiDiagnosisService;

    public DiagnosisEngine(List<DiagnosisRule> rules, AiDiagnosisService aiDiagnosisService) {
        this.rules = rules.stream()
                .sorted(Comparator.comparingInt(DiagnosisRule::priority))
                .toList();
        this.aiDiagnosisService = aiDiagnosisService;
    }

    public DiagnosisResult diagnose(
            MonitorEntity monitor,
            List<CheckResultEntity> failedResults,
            List<CheckResultEntity> recentHistory) {

        Optional<DiagnosisResult> ruleResult = runRules(monitor, failedResults, recentHistory);

        if (ruleResult.isPresent() && ruleResult.get().confidence() == DiagnosisConfidence.HIGH) {
            log.info("Diagnosis for monitor {} [{}]: {} (rule-based, HIGH confidence)",
                    monitor.getName(), monitor.getUrl(), ruleResult.get().diagnosis());
            return ruleResult.get();
        }

        if (aiDiagnosisService.isEnabled()) {
            log.info("Rule-based confidence is {} for monitor {} — escalating to AI",
                    ruleResult.map(r -> r.confidence().name()).orElse("NONE"),
                    monitor.getName());

            Optional<DiagnosisResult> aiResult =
                    aiDiagnosisService.diagnose(monitor, failedResults, recentHistory);

            if (aiResult.isPresent()) {
                log.info("AI diagnosis for monitor {} [{}]: {}",
                        monitor.getName(), monitor.getUrl(), aiResult.get().diagnosis());
                return aiResult.get();
            }
        }

        if (ruleResult.isPresent()) {
            log.info("Diagnosis for monitor {} [{}]: {} (rule-based, {} confidence)",
                    monitor.getName(), monitor.getUrl(),
                    ruleResult.get().diagnosis(), ruleResult.get().confidence());
            return ruleResult.get();
        }

        log.info("No specific diagnosis found for monitor {} — returning generic", monitor.getName());
        return buildGenericDiagnosis(monitor, failedResults);
    }

    private Optional<DiagnosisResult> runRules(
            MonitorEntity monitor,
            List<CheckResultEntity> failedResults,
            List<CheckResultEntity> recentHistory) {

        for (DiagnosisRule rule : rules) {
            try {
                Optional<DiagnosisResult> result = rule.evaluate(monitor, failedResults, recentHistory);
                if (result.isPresent()) {
                    return result;
                }
            } catch (Exception e) {
                log.warn("Diagnosis rule {} failed: {}", rule.getClass().getSimpleName(), e.getMessage());
            }
        }
        return Optional.empty();
    }

    private DiagnosisResult buildGenericDiagnosis(
            MonitorEntity monitor, List<CheckResultEntity> failedResults) {

        String errors = failedResults.stream()
                .map(CheckResultEntity::getError)
                .filter(e -> e != null && !e.isBlank())
                .findFirst()
                .orElse("No error details available");

        int statusCode = failedResults.stream()
                .mapToInt(CheckResultEntity::getStatusCode)
                .filter(c -> c > 0)
                .findFirst()
                .orElse(0);

        String diagnosis = statusCode > 0
                ? "Service returning HTTP " + statusCode
                : "Service unreachable";

        return new DiagnosisResult(
                diagnosis,
                DiagnosisCategory.UNKNOWN,
                DiagnosisConfidence.LOW,
                "The monitor detected a failure but the specific root cause could not be determined from the available signals. "
                        + "Error details: " + truncate(errors, 500),
                "Check application logs and server health directly. Review recent deployment history for changes.",
                "RULE_BASED");
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
