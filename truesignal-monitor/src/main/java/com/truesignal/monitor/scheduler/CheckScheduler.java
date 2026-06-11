package com.truesignal.monitor.scheduler;

import com.truesignal.common.enums.CheckStatus;
import com.truesignal.common.enums.MonitorType;
import com.truesignal.common.protocol.CheckResultReport;
import com.truesignal.common.protocol.MonitorAssignment;
import com.truesignal.monitor.checker.HealthChecker;
import com.truesignal.monitor.config.MonitorConfig;
import com.truesignal.monitor.reporter.ResultReporter;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CheckScheduler {

    private static final Logger log = LoggerFactory.getLogger(CheckScheduler.class);

    private final Map<MonitorType, HealthChecker> checkersByType;
    private final ResultReporter resultReporter;
    private final MonitorConfig monitorConfig;
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> activeTasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executor =
            Executors.newScheduledThreadPool(10, r -> {
                Thread t = new Thread(r, "truesignal-check");
                t.setDaemon(true);
                return t;
            });

    public CheckScheduler(
            Map<MonitorType, HealthChecker> healthCheckersByType,
            ResultReporter resultReporter,
            MonitorConfig monitorConfig) {
        this.checkersByType = healthCheckersByType;
        this.resultReporter = resultReporter;
        this.monitorConfig = monitorConfig;
    }

    public synchronized void updateAssignments(MonitorAssignment assignment) {
        List<MonitorAssignment.MonitorTask> tasks =
                (assignment == null || assignment.tasks() == null)
                        ? List.of() : assignment.tasks();

        java.util.Set<Long> incomingIds = new java.util.HashSet<>();
        for (MonitorAssignment.MonitorTask task : tasks) {
            if (task.monitorId() == null) {
                log.warn("Skipping monitor task without monitorId");
                continue;
            }
            incomingIds.add(task.monitorId());
            if (!activeTasks.containsKey(task.monitorId())) {
                long jitterSec = ThreadLocalRandom.current().nextInt(0, 6);
                long periodSec = Math.max(1L, task.intervalSeconds());
                ScheduledFuture<?> future = executor.scheduleAtFixedRate(
                        () -> runSingleCheck(task), jitterSec, periodSec, TimeUnit.SECONDS);
                activeTasks.put(task.monitorId(), future);
                log.info("Scheduled check for monitor {} every {}s", task.monitorId(), periodSec);
            }
        }

        activeTasks.entrySet().removeIf(entry -> {
            if (!incomingIds.contains(entry.getKey())) {
                entry.getValue().cancel(false);
                log.info("Removed check for monitor {}", entry.getKey());
                return true;
            }
            return false;
        });
    }

    private void runSingleCheck(MonitorAssignment.MonitorTask task) {
        HealthChecker checker = checkersByType.get(task.type());
        if (checker == null) {
            log.warn("No HealthChecker registered for {}", task.type());
            return;
        }
        try {
            HealthChecker.CheckResult result = checker.check(task);
            resultReporter.reportResult(new CheckResultReport(
                    monitorConfig.getNodeId(),
                    monitorConfig.getRegion(),
                    task.monitorId(),
                    result.status(),
                    result.responseTimeMs(),
                    result.statusCode(),
                    result.error(),
                    Instant.now()));
        } catch (Exception e) {
            log.error("Unexpected failure running check for monitor {}", task.monitorId(), e);
            resultReporter.reportResult(new CheckResultReport(
                    monitorConfig.getNodeId(),
                    monitorConfig.getRegion(),
                    task.monitorId(),
                    CheckStatus.UNKNOWN,
                    0L,
                    0,
                    e.getMessage(),
                    Instant.now()));
        }
    }

    public int getActiveCheckCount() {
        return activeTasks.size();
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
