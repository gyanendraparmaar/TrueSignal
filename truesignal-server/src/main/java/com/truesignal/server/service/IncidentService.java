package com.truesignal.server.service;

import com.truesignal.common.dto.IncidentResponse;
import com.truesignal.common.enums.IncidentStatus;
import com.truesignal.server.entity.IncidentEntity;
import com.truesignal.server.entity.MonitorEntity;
import com.truesignal.server.repository.IncidentRepository;
import com.truesignal.server.repository.MonitorRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final MonitorRepository monitorRepository;

    public IncidentService(IncidentRepository incidentRepository, MonitorRepository monitorRepository) {
        this.incidentRepository = incidentRepository;
        this.monitorRepository = monitorRepository;
    }

    public IncidentResponse toResponse(IncidentEntity e) {
        Optional<MonitorEntity> monitor = monitorRepository.findById(e.getMonitorId());
        String monitorName = monitor.map(MonitorEntity::getName).orElse("Unknown");
        String monitorUrl = monitor.map(MonitorEntity::getUrl).orElse("");
        String projectSlug = monitor.map(MonitorEntity::getProjectSlug).orElse("");

        long durationSeconds;
        if (e.getResolvedAt() != null) {
            durationSeconds = Duration.between(e.getStartedAt(), e.getResolvedAt()).getSeconds();
        } else {
            durationSeconds = Duration.between(e.getStartedAt(), Instant.now()).getSeconds();
        }

        return new IncidentResponse(
                e.getId(),
                e.getMonitorId(),
                monitorName,
                monitorUrl,
                projectSlug,
                e.getStatus(),
                e.getCause(),
                e.getStartedAt(),
                e.getResolvedAt(),
                durationSeconds,
                e.getDiagnosis(),
                e.getDiagnosisCategory(),
                e.getDiagnosisConfidence(),
                e.getDiagnosisExplanation(),
                e.getDiagnosisSuggestion(),
                e.getDiagnosisSource());
    }

    public List<IncidentResponse> getActiveIncidents() {
        return getActiveIncidents(null);
    }

    public List<IncidentResponse> getActiveIncidents(String projectSlug) {
        List<IncidentResponse> all = incidentRepository.findByStatus(IncidentStatus.ONGOING).stream()
                .map(this::toResponse)
                .toList();
        if (projectSlug != null && !projectSlug.isBlank()) {
            return all.stream().filter(i -> projectSlug.equals(i.projectSlug())).toList();
        }
        return all;
    }

    public List<IncidentResponse> getRecentIncidents() {
        return getRecentIncidents(null);
    }

    public List<IncidentResponse> getRecentIncidents(String projectSlug) {
        List<IncidentResponse> all = incidentRepository.findTop10ByOrderByStartedAtDesc().stream()
                .map(this::toResponse)
                .toList();
        if (projectSlug != null && !projectSlug.isBlank()) {
            return all.stream().filter(i -> projectSlug.equals(i.projectSlug())).toList();
        }
        return all;
    }

    public List<IncidentResponse> getIncidentsByMonitor(Long monitorId) {
        return incidentRepository.findByMonitorIdOrderByStartedAtDesc(monitorId).stream()
                .map(this::toResponse)
                .toList();
    }
}
