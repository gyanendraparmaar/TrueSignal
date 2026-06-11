package com.truesignal.monitor.checker;

import com.truesignal.common.enums.CheckStatus;
import com.truesignal.common.enums.MonitorType;
import com.truesignal.common.protocol.MonitorAssignment;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class HttpChecker implements HealthChecker {

    private static final Logger log = LoggerFactory.getLogger(HttpChecker.class);
    private static final int MAX_BODY_BYTES = 64 * 1024;

    @Override
    public MonitorType supportedType() {
        return MonitorType.HTTP;
    }

    @Override
    public CheckResult check(MonitorAssignment.MonitorTask task) {
        String rawUrl = task.url() == null ? "" : task.url().trim();
        if (rawUrl.isEmpty()) {
            return new CheckResult(CheckStatus.DOWN, 0, 0, "URL is empty");
        }
        String resolved;
        try {
            resolved = rawUrl.contains("://") ? rawUrl : "http://" + rawUrl;
            URI.create(resolved);
        } catch (Exception e) {
            return new CheckResult(CheckStatus.DOWN, 0, 0, "Invalid URL: " + e.getMessage());
        }

        HttpURLConnection connection = null;
        long startNs = System.nanoTime();
        try {
            connection = (HttpURLConnection) URI.create(resolved).toURL().openConnection();
            int timeout = Math.max(1, task.timeoutMs());
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(true);

            int statusCode = connection.getResponseCode();
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;

            boolean keywordRequired =
                    task.keyword() != null && !task.keyword().isBlank();
            String bodySample = "";
            if (keywordRequired) {
                bodySample = readBodySample(connection, statusCode);
            }

            boolean in2xx = statusCode >= 200 && statusCode < 300;
            boolean codeMatches =
                    task.expectedStatusCode() <= 0 ? in2xx : statusCode == task.expectedStatusCode();
            boolean keywordMatches = !keywordRequired
                    || containsIgnoreCase(bodySample, task.keyword().trim());

            if (codeMatches && keywordMatches) {
                return new CheckResult(CheckStatus.UP, elapsedMs, statusCode, null);
            }
            if (in2xx) {
                String err = !codeMatches ? "Unexpected status code " + statusCode
                        : "Response body does not contain keyword";
                return new CheckResult(CheckStatus.DEGRADED, elapsedMs, statusCode, err);
            }
            String err = "HTTP " + statusCode;
            return new CheckResult(CheckStatus.DOWN, elapsedMs, statusCode, err);
        } catch (Exception e) {
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
            log.trace("HTTP check failed", e);
            return new CheckResult(CheckStatus.DOWN, elapsedMs, 0, e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readBodySample(HttpURLConnection connection, int statusCode) {
        InputStream stream = null;
        try {
            stream = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (stream == null) {
                return "";
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int total = 0;
            int read;
            while (total < MAX_BODY_BYTES && (read = stream.read(chunk)) != -1) {
                int take = Math.min(read, MAX_BODY_BYTES - total);
                buffer.write(chunk, 0, take);
                total += take;
            }
            return buffer.toString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        return haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }
}
