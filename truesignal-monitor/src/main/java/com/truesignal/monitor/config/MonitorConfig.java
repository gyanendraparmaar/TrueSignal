package com.truesignal.monitor.config;

import com.truesignal.common.enums.MonitorType;
import com.truesignal.monitor.checker.HealthChecker;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Configuration
@Component
@ConfigurationProperties(prefix = "truesignal.monitor")
public class MonitorConfig {

    private String serverUrl = "http://localhost:8080";
    private String nodeId = UUID.randomUUID().toString().substring(0, 8);
    private String region = "local";
    private int port = 0;
    private long heartbeatIntervalMs = 5000;

    public String effectiveServerBaseUrl() {
        String base = serverUrl == null ? "" : serverUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public long getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    public void setHeartbeatIntervalMs(long heartbeatIntervalMs) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public Map<MonitorType, HealthChecker> healthCheckersByType(List<HealthChecker> checkers) {
        return checkers.stream()
                .collect(Collectors.toMap(HealthChecker::supportedType, Function.identity(), (a, b) -> a));
    }
}
