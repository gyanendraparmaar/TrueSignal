package com.truesignal.server.diagnosis.rules;

import com.truesignal.common.enums.DiagnosisCategory;
import com.truesignal.common.enums.DiagnosisConfidence;
import com.truesignal.server.diagnosis.DiagnosisResult;
import com.truesignal.server.diagnosis.DiagnosisRule;
import com.truesignal.server.entity.CheckResultEntity;
import com.truesignal.server.entity.MonitorEntity;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(5)
public class ConnectionErrorRule implements DiagnosisRule {

    private record ErrorPattern(
            Pattern pattern,
            DiagnosisCategory category,
            DiagnosisConfidence confidence,
            String diagnosis,
            String explanation,
            String suggestion) {}

    private static final List<ErrorPattern> PATTERNS = List.of(
            new ErrorPattern(
                    Pattern.compile("(?i)(connection\\s*refused|ECONNREFUSED|ConnectException)"),
                    DiagnosisCategory.NETWORK,
                    DiagnosisConfidence.HIGH,
                    "Connection refused — server is not accepting connections on this port",
                    "The target server actively refused the TCP connection. The application process is likely not running, crashed, or is not listening on the expected port.",
                    "Verify the application process is running. Check if it's bound to the correct port and interface. Review process manager logs (systemd, Docker, etc.)."),
            new ErrorPattern(
                    Pattern.compile("(?i)(connection\\s*(timed?\\s*out|timeout)|connect\\s+timed?\\s*out|ETIMEDOUT)"),
                    DiagnosisCategory.NETWORK,
                    DiagnosisConfidence.HIGH,
                    "Connection timeout — server is unreachable or blocked by firewall",
                    "The TCP connection could not be established within the timeout period. The server may be behind a firewall that drops packets, or network routing may be broken.",
                    "Check firewall rules, security groups, and network ACLs. Verify the server's IP address is correct and reachable. Test connectivity with traceroute."),
            new ErrorPattern(
                    Pattern.compile("(?i)(connection\\s*reset|ECONNRESET|RST)"),
                    DiagnosisCategory.NETWORK,
                    DiagnosisConfidence.MEDIUM,
                    "Connection reset — server or intermediary dropped the connection",
                    "The TCP connection was forcibly closed by the remote host or a network device (firewall, load balancer). This can indicate server crashes, WAF blocks, or idle connection timeouts.",
                    "Check if a WAF or DDoS protection service is blocking requests. Review server error logs for crash signals."),
            new ErrorPattern(
                    Pattern.compile("(?i)(read\\s*timed?\\s*out|SocketTimeoutException|response\\s*timeout)"),
                    DiagnosisCategory.TIMEOUT,
                    DiagnosisConfidence.HIGH,
                    "Read timeout — server accepted connection but is too slow to respond",
                    "The TCP connection was established but the server did not send a response in time. The application is likely stuck processing the request — possibly waiting on a slow database query, external API, or experiencing a deadlock.",
                    "Check for slow queries in the database, hanging external calls, or thread pool exhaustion. Increase the monitor timeout if the endpoint is expected to be slow."),
            new ErrorPattern(
                    Pattern.compile("(?i)(JDBC|database|connection\\s*pool|pool\\s*exhausted|too\\s*many\\s*connections|DataSource)"),
                    DiagnosisCategory.DATABASE,
                    DiagnosisConfidence.HIGH,
                    "Database connection failure — connection pool exhausted or DB unreachable",
                    "The error message indicates a database connectivity issue. The connection pool may be exhausted, the database server may be down, or connections are leaking and not being returned to the pool.",
                    "Check database server status and connectivity. Review connection pool settings (max size, idle timeout). Look for connection leaks in recent code changes."),
            new ErrorPattern(
                    Pattern.compile("(?i)(out\\s*of\\s*memory|OOM|heap\\s*space|GC\\s*overhead)"),
                    DiagnosisCategory.OVERLOAD,
                    DiagnosisConfidence.HIGH,
                    "Memory exhaustion — application ran out of heap space",
                    "The application ran out of available memory (JVM heap space, system RAM). This causes request processing to fail or the process to be killed by the OS.",
                    "Increase memory allocation. Profile the application for memory leaks. Check for unbounded caches or large result sets being loaded into memory."),
            new ErrorPattern(
                    Pattern.compile("(?i)(no\\s*route|unreachable|network\\s*is\\s*unreachable|ENETUNREACH)"),
                    DiagnosisCategory.NETWORK,
                    DiagnosisConfidence.HIGH,
                    "Network unreachable — no route to the target host",
                    "There is no network path from the monitoring node to the target. This indicates a routing issue, a misconfigured VPN, or the target network segment being offline.",
                    "Check network routing tables, VPN tunnels, and cloud VPC peering configurations. Verify the target IP/hostname resolves correctly."));

    @Override
    public Optional<DiagnosisResult> evaluate(
            MonitorEntity monitor,
            List<CheckResultEntity> failedResults,
            List<CheckResultEntity> recentHistory) {

        String combinedErrors = failedResults.stream()
                .map(CheckResultEntity::getError)
                .filter(e -> e != null && !e.isBlank())
                .collect(java.util.stream.Collectors.joining(" | "));

        if (combinedErrors.isBlank()) {
            return Optional.empty();
        }

        for (ErrorPattern ep : PATTERNS) {
            if (ep.pattern().matcher(combinedErrors).find()) {
                return Optional.of(new DiagnosisResult(
                        ep.diagnosis(),
                        ep.category(),
                        ep.confidence(),
                        ep.explanation(),
                        ep.suggestion(),
                        "RULE_BASED"));
            }
        }

        return Optional.empty();
    }

    @Override
    public int priority() {
        return 5;
    }
}
