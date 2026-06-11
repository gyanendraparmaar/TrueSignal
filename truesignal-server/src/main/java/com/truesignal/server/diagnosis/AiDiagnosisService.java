package com.truesignal.server.diagnosis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.truesignal.common.enums.CheckStatus;
import com.truesignal.common.enums.DiagnosisCategory;
import com.truesignal.common.enums.DiagnosisConfidence;
import com.truesignal.server.config.TrueSignalProperties;
import com.truesignal.server.entity.CheckResultEntity;
import com.truesignal.server.entity.MonitorEntity;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class AiDiagnosisService {

    private static final Logger log = LoggerFactory.getLogger(AiDiagnosisService.class);

    private final TrueSignalProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AiDiagnosisService(TrueSignalProperties properties, RestTemplateBuilder restTemplateBuilder) {
        this.properties = properties;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(15))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public boolean isEnabled() {
        TrueSignalProperties.Ai ai = properties.getAi();
        return ai.isEnabled() && ai.getApiKey() != null && !ai.getApiKey().isBlank();
    }

    public Optional<DiagnosisResult> diagnose(
            MonitorEntity monitor,
            List<CheckResultEntity> failedResults,
            List<CheckResultEntity> recentHistory) {

        if (!isEnabled()) {
            return Optional.empty();
        }

        try {
            String prompt = buildPrompt(monitor, failedResults, recentHistory);
            String response = callGemini(prompt);
            return parseResponse(response);
        } catch (Exception e) {
            log.warn("AI diagnosis failed, falling back to rule-based: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String buildPrompt(
            MonitorEntity monitor,
            List<CheckResultEntity> failedResults,
            List<CheckResultEntity> recentHistory) {

        String failedSummary = failedResults.stream()
                .map(r -> String.format(
                        "  - Region: %s, Status: %s, HTTP Code: %d, Response Time: %dms, Error: %s",
                        r.getNodeRegion(),
                        r.getStatus(),
                        r.getStatusCode(),
                        r.getResponseTimeMs(),
                        r.getError() != null ? r.getError() : "none"))
                .collect(Collectors.joining("\n"));

        long healthyCount = recentHistory.stream()
                .filter(r -> r.getStatus() == CheckStatus.UP)
                .count();
        long totalRecent = recentHistory.size();

        double avgResponseTime = recentHistory.stream()
                .filter(r -> r.getStatus() == CheckStatus.UP && r.getResponseTimeMs() > 0)
                .mapToLong(CheckResultEntity::getResponseTimeMs)
                .average()
                .orElse(0);

        long distinctRegions = failedResults.stream()
                .map(CheckResultEntity::getNodeRegion)
                .distinct()
                .count();

        return String.format(
                """
                You are a DevOps diagnostician for TrueSignal, an uptime monitoring system.
                Analyze this API health check failure and provide a root cause diagnosis.

                MONITOR DETAILS:
                  Name: %s
                  URL: %s
                  Type: %s
                  Expected Status Code: %d

                FAILED CHECK RESULTS (from monitoring nodes):
                %s

                CONTEXT:
                  Regions reporting failure: %d
                  Recent check history: %d healthy out of %d total
                  Average healthy response time: %.0fms

                Respond with ONLY a JSON object (no markdown, no backticks):
                {
                  "diagnosis": "one-line root cause summary (max 100 chars)",
                  "category": "one of: DATABASE, NETWORK, AUTHENTICATION, SSL_TLS, DNS, OVERLOAD, RATE_LIMIT, GATEWAY, DEPLOYMENT, TIMEOUT, REGIONAL, UNKNOWN",
                  "confidence": "one of: HIGH, MEDIUM, LOW",
                  "explanation": "2-3 sentence technical explanation of what is happening and why",
                  "suggestion": "1-2 sentence actionable suggestion of what the user should check first"
                }
                """,
                monitor.getName(),
                monitor.getUrl(),
                monitor.getType(),
                monitor.getExpectedStatusCode(),
                failedSummary,
                distinctRegions,
                healthyCount,
                totalRecent,
                avgResponseTime);
    }

    @SuppressWarnings("unchecked")
    private String callGemini(String prompt) {
        TrueSignalProperties.Ai ai = properties.getAi();
        String url = String.format("%s/v1beta/models/%s:generateContent?key=%s",
                ai.getBaseUrl(), ai.getModel(), ai.getApiKey());

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "temperature", 0.2,
                        "maxOutputTokens", 500,
                        "responseMimeType", "application/json"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        String responseJson = restTemplate.postForObject(url, entity, String.class);

        try {
            JsonNode root = objectMapper.readTree(responseJson);
            return root.at("/candidates/0/content/parts/0/text").asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Gemini response", e);
        }
    }

    private Optional<DiagnosisResult> parseResponse(String jsonResponse) {
        try {
            JsonNode node = objectMapper.readTree(jsonResponse);

            String diagnosis = node.path("diagnosis").asText("Unknown failure");
            String categoryStr = node.path("category").asText("UNKNOWN");
            String confidenceStr = node.path("confidence").asText("LOW");
            String explanation = node.path("explanation").asText("");
            String suggestion = node.path("suggestion").asText("");

            DiagnosisCategory category;
            try {
                category = DiagnosisCategory.valueOf(categoryStr.toUpperCase().replace(" ", "_"));
            } catch (IllegalArgumentException e) {
                category = DiagnosisCategory.UNKNOWN;
            }

            DiagnosisConfidence confidence;
            try {
                confidence = DiagnosisConfidence.valueOf(confidenceStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                confidence = DiagnosisConfidence.LOW;
            }

            return Optional.of(new DiagnosisResult(
                    truncate(diagnosis, 500),
                    category,
                    confidence,
                    truncate(explanation, 2000),
                    truncate(suggestion, 1000),
                    "AI"));
        } catch (Exception e) {
            log.warn("Failed to parse AI diagnosis JSON: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
