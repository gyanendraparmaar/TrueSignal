package com.truesignal.monitor.heartbeat;

import com.truesignal.common.protocol.MonitorAssignment;
import com.truesignal.common.protocol.NodeRegistrationRequest;
import com.truesignal.monitor.config.MonitorConfig;
import com.truesignal.monitor.scheduler.CheckScheduler;
import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class NodeRegistrar {

    private static final Logger log = LoggerFactory.getLogger(NodeRegistrar.class);
    private static final long POLL_INTERVAL_SEC = 15;

    private final RestTemplate restTemplate;
    private final MonitorConfig monitorConfig;
    private final CheckScheduler checkScheduler;
    private final ServletWebServerApplicationContext webServerContext;
    private final AtomicBoolean registered = new AtomicBoolean(false);
    private final ScheduledExecutorService retryExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "truesignal-register-retry");
                t.setDaemon(true);
                return t;
            });

    public NodeRegistrar(
            RestTemplate restTemplate,
            MonitorConfig monitorConfig,
            CheckScheduler checkScheduler,
            ServletWebServerApplicationContext webServerContext) {
        this.restTemplate = restTemplate;
        this.monitorConfig = monitorConfig;
        this.checkScheduler = checkScheduler;
        this.webServerContext = webServerContext;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerWithServer() {
        attemptRegistration();
    }

    private void attemptRegistration() {
        String url = monitorConfig.effectiveServerBaseUrl() + "/api/internal/nodes/register";
        int port = webServerContext.getWebServer().getPort();
        var registration = new NodeRegistrationRequest(
                monitorConfig.getNodeId(),
                monitorConfig.getRegion(),
                resolveReachableAddress(),
                port);
        try {
            MonitorAssignment assignment =
                    restTemplate.postForObject(url, registration, MonitorAssignment.class);
            if (assignment == null) {
                assignment = new MonitorAssignment(List.of());
            }
            checkScheduler.updateAssignments(assignment);
            int n = assignment.tasks() == null ? 0 : assignment.tasks().size();
            log.info(
                    "Registered as node {} in region {}, received {} monitor assignments",
                    monitorConfig.getNodeId(),
                    monitorConfig.getRegion(),
                    n);

            if (registered.compareAndSet(false, true)) {
                retryExecutor.scheduleAtFixedRate(
                        this::pollAssignments, POLL_INTERVAL_SEC, POLL_INTERVAL_SEC, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.error(
                    "Node registration failed for {}; retrying in 10 seconds",
                    monitorConfig.getNodeId(),
                    e);
            retryExecutor.schedule(this::attemptRegistration, 10, TimeUnit.SECONDS);
        }
    }

    private void pollAssignments() {
        String url = monitorConfig.effectiveServerBaseUrl()
                + "/api/internal/nodes/" + monitorConfig.getNodeId() + "/assignments";
        try {
            MonitorAssignment assignment = restTemplate.getForObject(url, MonitorAssignment.class);
            if (assignment == null) {
                assignment = new MonitorAssignment(List.of());
            }
            checkScheduler.updateAssignments(assignment);
        } catch (Exception e) {
            log.warn("Failed to poll assignments for node {}: {}", monitorConfig.getNodeId(), e.getMessage());
        }
    }

    private String resolveReachableAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    @PreDestroy
    public void shutdownRetryExecutor() {
        retryExecutor.shutdown();
        try {
            if (!retryExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                retryExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            retryExecutor.shutdownNow();
        }
    }
}
