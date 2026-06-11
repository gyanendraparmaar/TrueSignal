package com.truesignal.server.diagnosis.rules;

import com.truesignal.common.enums.DiagnosisCategory;
import com.truesignal.common.enums.DiagnosisConfidence;
import com.truesignal.server.diagnosis.DiagnosisResult;
import com.truesignal.server.diagnosis.DiagnosisRule;
import com.truesignal.server.entity.CheckResultEntity;
import com.truesignal.server.entity.MonitorEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class HttpStatusRule implements DiagnosisRule {

    private record StatusMapping(
            DiagnosisCategory category,
            DiagnosisConfidence confidence,
            String diagnosis,
            String explanation,
            String suggestion) {}

    private static final Map<Integer, StatusMapping> STATUS_MAP = Map.ofEntries(
            Map.entry(
                    401,
                    new StatusMapping(
                            DiagnosisCategory.AUTHENTICATION,
                            DiagnosisConfidence.HIGH,
                            "Authentication failure — invalid or expired credentials",
                            "The server returned HTTP 401 Unauthorized. This means the request lacks valid authentication credentials. An API key, token, or session may have expired or been revoked.",
                            "Check API keys, OAuth tokens, or session cookies. Verify that credentials are still valid and not rotated.")),
            Map.entry(
                    403,
                    new StatusMapping(
                            DiagnosisCategory.AUTHENTICATION,
                            DiagnosisConfidence.HIGH,
                            "Access forbidden — insufficient permissions",
                            "The server returned HTTP 403 Forbidden. The server understood the request but refuses to authorize it. The authenticated identity does not have the required permissions.",
                            "Review access control policies, IP allowlists, and role-based permissions for the monitoring endpoint.")),
            Map.entry(
                    404,
                    new StatusMapping(
                            DiagnosisCategory.DEPLOYMENT,
                            DiagnosisConfidence.HIGH,
                            "Endpoint not found — URL may have changed after deployment",
                            "The server returned HTTP 404 Not Found. The monitored endpoint no longer exists at this URL. This often happens after a deployment that changes route paths.",
                            "Verify the monitored URL is correct. Check if routes were changed in a recent deployment.")),
            Map.entry(
                    429,
                    new StatusMapping(
                            DiagnosisCategory.RATE_LIMIT,
                            DiagnosisConfidence.HIGH,
                            "Rate limited — too many requests to the target",
                            "The server returned HTTP 429 Too Many Requests. The monitoring requests or other traffic has exceeded the server's rate limit threshold.",
                            "Increase the monitor check interval or contact the service to raise rate limits. Check for other clients sending excessive traffic.")),
            Map.entry(
                    500,
                    new StatusMapping(
                            DiagnosisCategory.UNKNOWN,
                            DiagnosisConfidence.MEDIUM,
                            "Internal server error — application crash or unhandled exception",
                            "The server returned HTTP 500 Internal Server Error. The application encountered an unexpected condition. This could be a bug, database issue, or misconfiguration.",
                            "Check application logs for stack traces. Look at recent deployments for regressions.")),
            Map.entry(
                    502,
                    new StatusMapping(
                            DiagnosisCategory.GATEWAY,
                            DiagnosisConfidence.HIGH,
                            "Bad gateway — reverse proxy cannot reach the backend",
                            "The server returned HTTP 502 Bad Gateway. A load balancer or reverse proxy (e.g., Nginx, AWS ALB) received an invalid response from the upstream application server.",
                            "Check if the backend application is running. Verify proxy/load balancer configuration and health checks.")),
            Map.entry(
                    503,
                    new StatusMapping(
                            DiagnosisCategory.OVERLOAD,
                            DiagnosisConfidence.HIGH,
                            "Service unavailable — server overloaded or in maintenance",
                            "The server returned HTTP 503 Service Unavailable. The server is temporarily unable to handle requests, likely due to overload, maintenance, or a failing health check.",
                            "Check server resource utilization (CPU, memory). Verify if maintenance mode is enabled. Check auto-scaling policies.")),
            Map.entry(
                    504,
                    new StatusMapping(
                            DiagnosisCategory.TIMEOUT,
                            DiagnosisConfidence.HIGH,
                            "Gateway timeout — backend is too slow to respond",
                            "The server returned HTTP 504 Gateway Timeout. A reverse proxy waited too long for the upstream server to respond. The application may be hanging on a slow database query or external API call.",
                            "Check for slow database queries, external API latency, or deadlocks. Consider increasing timeout thresholds if the endpoint is legitimately slow.")));

    @Override
    public Optional<DiagnosisResult> evaluate(
            MonitorEntity monitor,
            List<CheckResultEntity> failedResults,
            List<CheckResultEntity> recentHistory) {
        if (failedResults.isEmpty()) {
            return Optional.empty();
        }

        Map<Integer, Long> statusCounts = failedResults.stream()
                .filter(r -> r.getStatusCode() > 0)
                .collect(java.util.stream.Collectors.groupingBy(CheckResultEntity::getStatusCode, java.util.stream.Collectors.counting()));

        if (statusCounts.isEmpty()) {
            return Optional.empty();
        }

        int dominantStatus = statusCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(0);

        StatusMapping mapping = STATUS_MAP.get(dominantStatus);
        if (mapping == null) {
            return Optional.empty();
        }

        return Optional.of(new DiagnosisResult(
                mapping.diagnosis(),
                mapping.category(),
                mapping.confidence(),
                mapping.explanation(),
                mapping.suggestion(),
                "RULE_BASED"));
    }

    @Override
    public int priority() {
        return 10;
    }
}
