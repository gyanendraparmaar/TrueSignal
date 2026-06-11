package com.truesignal.monitor.checker;

import com.truesignal.common.enums.CheckStatus;
import com.truesignal.common.enums.MonitorType;
import com.truesignal.common.protocol.MonitorAssignment;
import java.net.InetSocketAddress;
import java.net.Socket;
import org.springframework.stereotype.Component;

@Component
public class TcpChecker implements HealthChecker {

    @Override
    public MonitorType supportedType() {
        return MonitorType.TCP;
    }

    @Override
    public CheckResult check(MonitorAssignment.MonitorTask task) {
        String raw = task.url() == null ? "" : task.url().trim();
        if (raw.isEmpty()) {
            return new CheckResult(CheckStatus.DOWN, 0, 0, "Target is empty");
        }

        int colon = raw.lastIndexOf(':');
        if (colon <= 0 || colon == raw.length() - 1) {
            return new CheckResult(CheckStatus.DOWN, 0, 0, "Expected host:port");
        }

        String host = raw.substring(0, colon).trim();
        String portPart = raw.substring(colon + 1).trim();
        if (host.isEmpty()) {
            return new CheckResult(CheckStatus.DOWN, 0, 0, "Host is empty");
        }

        int port;
        try {
            port = Integer.parseInt(portPart);
        } catch (NumberFormatException e) {
            return new CheckResult(CheckStatus.DOWN, 0, 0, "Invalid port: " + portPart);
        }
        if (port < 1 || port > 65535) {
            return new CheckResult(CheckStatus.DOWN, 0, 0, "Port out of range: " + port);
        }

        int timeout = Math.max(1, task.timeoutMs());
        long startNs = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeout);
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
            return new CheckResult(CheckStatus.UP, elapsedMs, 0, null);
        } catch (Exception e) {
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
            return new CheckResult(CheckStatus.DOWN, elapsedMs, 0, e.getMessage());
        }
    }
}
