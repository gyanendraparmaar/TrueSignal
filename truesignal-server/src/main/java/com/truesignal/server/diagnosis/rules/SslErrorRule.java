package com.truesignal.server.diagnosis.rules;

import com.truesignal.common.enums.DiagnosisCategory;
import com.truesignal.common.enums.DiagnosisConfidence;
import com.truesignal.common.enums.MonitorType;
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
@Order(3)
public class SslErrorRule implements DiagnosisRule {

    private static final Pattern SSL_EXPIRED =
            Pattern.compile("(?i)(certificate.*expir|expir.*certificate|NotAfter|cert.*not.*valid)");
    private static final Pattern SSL_HANDSHAKE =
            Pattern.compile("(?i)(ssl.*handshake|handshake.*fail|SSLHandshakeException|tls.*error)");
    private static final Pattern SSL_SELF_SIGNED =
            Pattern.compile("(?i)(self.signed|PKIX|unable.*find.*cert.*path|trust.*anchor)");
    private static final Pattern SSL_MISMATCH =
            Pattern.compile("(?i)(hostname.*mismatch|host.*verify|certificate.*match|SNI)");

    @Override
    public Optional<DiagnosisResult> evaluate(
            MonitorEntity monitor,
            List<CheckResultEntity> failedResults,
            List<CheckResultEntity> recentHistory) {

        boolean isSslMonitor = monitor.getType() == MonitorType.SSL;

        String combinedErrors = failedResults.stream()
                .map(CheckResultEntity::getError)
                .filter(e -> e != null && !e.isBlank())
                .collect(java.util.stream.Collectors.joining(" | "));

        if (combinedErrors.isBlank() && !isSslMonitor) {
            return Optional.empty();
        }

        if (SSL_EXPIRED.matcher(combinedErrors).find()) {
            return Optional.of(new DiagnosisResult(
                    "SSL certificate expired — HTTPS connections will fail",
                    DiagnosisCategory.SSL_TLS,
                    DiagnosisConfidence.HIGH,
                    "The SSL/TLS certificate for this domain has expired. Browsers and HTTP clients will reject connections, showing security warnings to users.",
                    "Renew the SSL certificate immediately. If using Let's Encrypt, check that the auto-renewal cron job is running. Verify with: openssl s_client -connect host:443",
                    "RULE_BASED"));
        }

        if (SSL_SELF_SIGNED.matcher(combinedErrors).find()) {
            return Optional.of(new DiagnosisResult(
                    "SSL certificate not trusted — self-signed or missing chain",
                    DiagnosisCategory.SSL_TLS,
                    DiagnosisConfidence.HIGH,
                    "The SSL certificate is not trusted by the certificate authority chain. It may be self-signed, or the intermediate CA certificates may not be properly configured on the server.",
                    "Install the full certificate chain on the server (leaf + intermediate CAs). If intentionally self-signed, configure clients to trust it explicitly.",
                    "RULE_BASED"));
        }

        if (SSL_MISMATCH.matcher(combinedErrors).find()) {
            return Optional.of(new DiagnosisResult(
                    "SSL hostname mismatch — certificate does not match the domain",
                    DiagnosisCategory.SSL_TLS,
                    DiagnosisConfidence.HIGH,
                    "The Common Name (CN) or Subject Alternative Name (SAN) in the SSL certificate does not match the requested hostname. This usually happens when a server hosts multiple domains but the certificate only covers some of them.",
                    "Reissue the certificate to include the correct domain in the SAN field. Verify Nginx/Apache virtual host and SNI configuration.",
                    "RULE_BASED"));
        }

        if (SSL_HANDSHAKE.matcher(combinedErrors).find()) {
            return Optional.of(new DiagnosisResult(
                    "SSL/TLS handshake failure — protocol or cipher mismatch",
                    DiagnosisCategory.SSL_TLS,
                    DiagnosisConfidence.MEDIUM,
                    "The TLS handshake could not complete. This may be due to incompatible TLS versions (e.g., server requires TLS 1.3 but client only supports 1.2), unsupported cipher suites, or a misconfigured certificate.",
                    "Check the server's supported TLS versions and cipher suites. Ensure compatibility with modern clients. Test with: openssl s_client -connect host:443 -tls1_2",
                    "RULE_BASED"));
        }

        if (isSslMonitor && combinedErrors.toLowerCase().contains("ssl")) {
            return Optional.of(new DiagnosisResult(
                    "SSL/TLS issue detected on certificate monitor",
                    DiagnosisCategory.SSL_TLS,
                    DiagnosisConfidence.MEDIUM,
                    "The SSL certificate monitor detected an issue with the target's TLS configuration. The specific cause could not be narrowed down from the error message alone.",
                    "Run a detailed SSL check using ssllabs.com or 'openssl s_client -connect host:443' to identify the exact issue.",
                    "RULE_BASED"));
        }

        return Optional.empty();
    }

    @Override
    public int priority() {
        return 3;
    }
}
