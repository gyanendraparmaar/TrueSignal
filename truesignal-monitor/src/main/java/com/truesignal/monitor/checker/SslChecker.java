package com.truesignal.monitor.checker;

import com.truesignal.common.enums.CheckStatus;
import com.truesignal.common.enums.MonitorType;
import com.truesignal.common.protocol.MonitorAssignment;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.springframework.stereotype.Component;

@Component
public class SslChecker implements HealthChecker {

    @Override
    public MonitorType supportedType() {
        return MonitorType.SSL;
    }

    @Override
    public CheckResult check(MonitorAssignment.MonitorTask task) {
        HostPort target;
        try {
            target = parseTarget(task.url());
        } catch (Exception e) {
            return new CheckResult(CheckStatus.DOWN, 0, 0, e.getMessage());
        }

        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        int timeout = Math.max(1, task.timeoutMs());
        long startNs = System.nanoTime();
        try (SSLSocket socket = (SSLSocket) factory.createSocket(target.host(), target.port())) {
            socket.setSoTimeout(timeout);
            long handshakeStart = System.nanoTime();
            socket.startHandshake();
            long elapsedMs = (System.nanoTime() - handshakeStart) / 1_000_000L;

            var chain = socket.getSession().getPeerCertificates();
            if (chain.length == 0) {
                return new CheckResult(CheckStatus.DOWN, elapsedMs, 0, "No peer certificates");
            }
            if (!(chain[0] instanceof X509Certificate cert)) {
                return new CheckResult(CheckStatus.DOWN, elapsedMs, 0, "Peer certificate is not X509");
            }

            Instant notAfter = cert.getNotAfter().toInstant();
            long days = Duration.between(Instant.now(), notAfter).toDays();
            if (days < 0) {
                return new CheckResult(
                        CheckStatus.DOWN,
                        elapsedMs,
                        0,
                        "SSL certificate expired " + Math.abs(days) + " days ago");
            }
            if (days > 30) {
                return new CheckResult(CheckStatus.UP, elapsedMs, 0, null);
            }
            String msg = "SSL expires in " + days + " days";
            if (days > 7) {
                return new CheckResult(CheckStatus.DEGRADED, elapsedMs, 0, msg);
            }
            return new CheckResult(CheckStatus.DOWN, elapsedMs, 0, msg);
        } catch (Exception e) {
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
            return new CheckResult(CheckStatus.DOWN, elapsedMs, 0, e.getMessage());
        }
    }

    private static HostPort parseTarget(String urlSpec) {
        String raw = urlSpec == null ? "" : urlSpec.trim();
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("Target is empty");
        }
        if (raw.contains("://")) {
            URI uri = URI.create(raw.replaceFirst("(?i)^ssl://", "https://"));
            String host = uri.getHost();
            if (host == null || host.isEmpty()) {
                throw new IllegalArgumentException("Could not parse host from URL");
            }
            int port = uri.getPort() > 0 ? uri.getPort() : 443;
            return new HostPort(host, port);
        }
        int colon = raw.lastIndexOf(':');
        if (colon > 0 && colon < raw.length() - 1) {
            String host = raw.substring(0, colon).trim();
            String portStr = raw.substring(colon + 1).trim();
            if (!host.isEmpty()) {
                try {
                    int p = Integer.parseInt(portStr);
                    if (p >= 1 && p <= 65535) {
                        return new HostPort(host, p);
                    }
                    throw new IllegalArgumentException("Port out of range: " + p);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid port in SSL target: " + portStr);
                }
            }
        }
        return new HostPort(raw, 443);
    }

    private record HostPort(String host, int port) {}
}
