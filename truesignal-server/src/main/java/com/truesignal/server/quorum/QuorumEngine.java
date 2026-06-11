package com.truesignal.server.quorum;

import com.truesignal.common.enums.CheckStatus;
import com.truesignal.common.enums.IncidentStatus;
import com.truesignal.common.protocol.CheckResultReport;
import com.truesignal.server.alerting.AlertEngine;
import com.truesignal.server.config.TrueSignalProperties;
import com.truesignal.server.diagnosis.DiagnosisEngine;
import com.truesignal.server.diagnosis.DiagnosisResult;
import com.truesignal.server.entity.CheckResultEntity;
import com.truesignal.server.entity.IncidentEntity;
import com.truesignal.server.entity.MonitorEntity;
import com.truesignal.server.repository.CheckResultRepository;
import com.truesignal.server.repository.IncidentRepository;
import com.truesignal.server.repository.MonitorRepository;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
public class QuorumEngine {

    private static final Logger log = LoggerFactory.getLogger(QuorumEngine.class);

    private final CheckResultRepository checkResultRepository;
    private final MonitorRepository monitorRepository;
    private final IncidentRepository incidentRepository;
    private final TrueSignalProperties properties;
    private final AlertEngine alertEngine;
    private final DiagnosisEngine diagnosisEngine;
    private final ConcurrentHashMap<Long, ReentrantLock> monitorLocks = new ConcurrentHashMap<>();

    public QuorumEngine(
            CheckResultRepository checkResultRepository,
            MonitorRepository monitorRepository,
            IncidentRepository incidentRepository,
            TrueSignalProperties properties,
            @Lazy AlertEngine alertEngine,
            DiagnosisEngine diagnosisEngine) {
        this.checkResultRepository = checkResultRepository;
        this.monitorRepository = monitorRepository;
        this.incidentRepository = incidentRepository;
        this.properties = properties;
        this.alertEngine = alertEngine;
        this.diagnosisEngine = diagnosisEngine;
    }

    public void processCheckResult(CheckResultReport report) {
        CheckResultEntity entity = new CheckResultEntity();
        entity.setMonitorId(report.monitorId());
        entity.setNodeId(report.nodeId());
        entity.setNodeRegion(report.nodeRegion());
        entity.setStatus(report.status());
        entity.setResponseTimeMs(report.responseTimeMs());
        entity.setStatusCode(report.statusCode());
        entity.setError(report.error());
        entity.setCheckedAt(report.checkedAt());
        checkResultRepository.save(entity);
        evaluateMonitor(report.monitorId());
    }

    public void evaluateMonitor(Long monitorId) {
        ReentrantLock lock = monitorLocks.computeIfAbsent(monitorId, k -> new ReentrantLock());
        lock.lock();
        try {
            List<CheckResultEntity> results = checkResultRepository.findLatestPerNode(monitorId);
            if (results.isEmpty()) {
                return;
            }
            int totalVotes = results.size();
            int downVotes = 0;
            for (CheckResultEntity r : results) {
                CheckStatus s = r.getStatus();
                if (s == CheckStatus.DOWN || s == CheckStatus.DEGRADED) {
                    downVotes++;
                }
            }
            double downRatio = (double) downVotes / totalVotes;
            double threshold = properties.getQuorum().getThreshold();
            CheckStatus newStatus = downRatio > threshold ? CheckStatus.DOWN : CheckStatus.UP;

            MonitorEntity monitor = monitorRepository.findById(monitorId).orElse(null);
            if (monitor == null) {
                return;
            }
            CheckStatus oldStatus = monitor.getCurrentStatus();
            if (newStatus == oldStatus) {
                return;
            }
            monitor.setCurrentStatus(newStatus);
            monitorRepository.save(monitor);

            if ((oldStatus == CheckStatus.UP || oldStatus == CheckStatus.UNKNOWN) && newStatus == CheckStatus.DOWN) {
                List<CheckResultEntity> failedResults = results.stream()
                        .filter(r -> r.getStatus() == CheckStatus.DOWN || r.getStatus() == CheckStatus.DEGRADED)
                        .toList();
                List<CheckResultEntity> recentHistory = checkResultRepository
                        .findByMonitorIdAndCheckedAtAfter(monitorId, Instant.now().minusSeconds(3600));

                DiagnosisResult diagnosisResult = diagnosisEngine.diagnose(monitor, failedResults, recentHistory);

                IncidentEntity incident = new IncidentEntity();
                incident.setMonitorId(monitorId);
                incident.setCause("Quorum: " + downVotes + "/" + totalVotes + " nodes report DOWN");
                incident.setStatus(IncidentStatus.ONGOING);
                incident.setDiagnosis(diagnosisResult.diagnosis());
                incident.setDiagnosisCategory(diagnosisResult.category());
                incident.setDiagnosisConfidence(diagnosisResult.confidence());
                incident.setDiagnosisExplanation(diagnosisResult.explanation());
                incident.setDiagnosisSuggestion(diagnosisResult.suggestion());
                incident.setDiagnosisSource(diagnosisResult.source());
                incidentRepository.save(incident);
                alertEngine.fireAlert(monitor, incident, "DOWN");
                log.info(
                        "INCIDENT CREATED: Monitor {} ({}) is DOWN — {}/{} quorum — Diagnosis: {} [{}]",
                        monitor.getName(),
                        monitor.getUrl(),
                        downVotes,
                        totalVotes,
                        diagnosisResult.diagnosis(),
                        diagnosisResult.source());
            } else if (oldStatus == CheckStatus.DOWN && newStatus == CheckStatus.UP) {
                incidentRepository
                        .findByMonitorIdAndStatus(monitorId, IncidentStatus.ONGOING)
                        .ifPresent(
                                inc -> {
                                    inc.setStatus(IncidentStatus.RESOLVED);
                                    inc.setResolvedAt(Instant.now());
                                    incidentRepository.save(inc);
                                });
                alertEngine.fireAlert(monitor, null, "RECOVERED");
                log.info("INCIDENT RESOLVED: Monitor {} ({}) is UP", monitor.getName(), monitor.getUrl());
            }
        } finally {
            lock.unlock();
        }
    }
}
