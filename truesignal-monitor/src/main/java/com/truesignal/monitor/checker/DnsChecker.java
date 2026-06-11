package com.truesignal.monitor.checker;

import com.truesignal.common.enums.CheckStatus;
import com.truesignal.common.enums.MonitorType;
import com.truesignal.common.protocol.MonitorAssignment;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import org.springframework.stereotype.Component;

@Component
public class DnsChecker implements HealthChecker {

    @Override
    public MonitorType supportedType() {
        return MonitorType.DNS;
    }

    @Override
    public CheckResult check(MonitorAssignment.MonitorTask task) {
        String hostname = extractHostname(task.url());
        if (hostname == null || hostname.isEmpty()) {
            return new CheckResult(CheckStatus.DOWN, 0, 0, "Hostname is empty");
        }

        long startNs = System.nanoTime();
        try {
            InetAddress.getByName(hostname);
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
            return new CheckResult(CheckStatus.UP, elapsedMs, 0, null);
        } catch (UnknownHostException e) {
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
            return new CheckResult(CheckStatus.DOWN, elapsedMs, 0, "DNS resolution failed");
        } catch (Exception e) {
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
            return new CheckResult(CheckStatus.DOWN, elapsedMs, 0, e.getMessage());
        }
    }

    private static String extractHostname(String rawUrl) {
        if (rawUrl == null) {
            return "";
        }
        String s = rawUrl.trim();
        if (s.isEmpty()) {
            return "";
        }
        try {
            if (s.contains("://")) {
                URI uri = URI.create(s);
                String host = uri.getHost();
                if (host != null && !host.isEmpty()) {
                    return host;
                }
            }
        } catch (Exception ignored) {
        }
        int slash = s.indexOf('/');
        if (slash >= 0) {
            s = s.substring(0, slash);
        }
        int colon = s.lastIndexOf(':');
        if (colon > 0) {
            String maybePort = s.substring(colon + 1);
            if (maybePort.chars().allMatch(Character::isDigit)) {
                s = s.substring(0, colon);
            }
        }
        return s.trim();
    }
}
