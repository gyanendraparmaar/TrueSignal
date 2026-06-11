package com.truesignal.monitor.heartbeat;

import com.truesignal.common.protocol.HeartbeatRequest;
import com.truesignal.monitor.config.MonitorConfig;
import com.truesignal.monitor.scheduler.CheckScheduler;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class HeartbeatSender {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatSender.class);

    private final RestTemplate restTemplate;
    private final MonitorConfig monitorConfig;
    private final CheckScheduler checkScheduler;

    public HeartbeatSender(
            RestTemplate restTemplate, MonitorConfig monitorConfig, CheckScheduler checkScheduler) {
        this.restTemplate = restTemplate;
        this.monitorConfig = monitorConfig;
        this.checkScheduler = checkScheduler;
    }

    @Scheduled(fixedRateString = "${truesignal.monitor.heartbeat-interval-ms:5000}")
    public void sendHeartbeat() {
        String url = monitorConfig.effectiveServerBaseUrl() + "/api/internal/nodes/heartbeat";
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L);
        double load = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
        if (load < 0) {
            load = 0;
        }
        var request = new HeartbeatRequest(
                monitorConfig.getNodeId(),
                checkScheduler.getActiveCheckCount(),
                usedMb,
                load,
                Instant.now());
        try {
            restTemplate.postForEntity(url, request, Void.class);
        } catch (Exception e) {
            log.warn("Heartbeat failed for node {}", monitorConfig.getNodeId(), e);
        }
    }
}
