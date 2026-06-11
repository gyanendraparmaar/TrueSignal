package com.truesignal.server.service;

import com.truesignal.common.dto.CreateMonitorRequest;
import com.truesignal.common.dto.MonitorResponse;
import com.truesignal.server.entity.MonitorEntity;
import com.truesignal.server.repository.CheckResultRepository;
import com.truesignal.server.repository.MonitorRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class MonitorService {

    private final MonitorRepository monitorRepository;
    private final CheckResultRepository checkResultRepository;

    public MonitorService(MonitorRepository monitorRepository, CheckResultRepository checkResultRepository) {
        this.monitorRepository = monitorRepository;
        this.checkResultRepository = checkResultRepository;
    }

    public MonitorResponse toResponse(MonitorEntity e) {
        Instant since30d = Instant.now().minus(30, ChronoUnit.DAYS);
        long total = checkResultRepository.countByMonitorIdSince(e.getId(), since30d);
        double uptimePercent = total == 0 ? 100.0 : (100.0 * checkResultRepository.countUpByMonitorIdSince(e.getId(), since30d)) / total;

        Instant since24h = Instant.now().minus(24, ChronoUnit.HOURS);
        Long avg = checkResultRepository.findAvgResponseTime(e.getId(), since24h);

        Instant lastCheckedAt = checkResultRepository
                .findByMonitorIdOrderByCheckedAtDesc(e.getId(), Pageable.ofSize(1))
                .stream()
                .findFirst()
                .map(cr -> cr.getCheckedAt())
                .orElse(null);

        return new MonitorResponse(
                e.getId(),
                e.getName(),
                e.getUrl(),
                e.getType(),
                e.getIntervalSeconds(),
                e.getTimeoutMs(),
                e.getExpectedStatusCode(),
                e.getKeyword(),
                e.getProjectSlug(),
                e.isPaused(),
                e.getCurrentStatus(),
                uptimePercent,
                avg,
                lastCheckedAt,
                e.getCreatedAt());
    }

    public MonitorResponse createMonitor(CreateMonitorRequest req) {
        MonitorEntity entity = new MonitorEntity();
        entity.setName(req.name());
        entity.setUrl(req.url());
        entity.setType(req.type());
        entity.setIntervalSeconds(req.intervalSeconds());
        entity.setTimeoutMs(req.timeoutMs());
        entity.setExpectedStatusCode(req.expectedStatusCode());
        entity.setKeyword(req.keyword());
        entity.setProjectSlug(req.projectSlug());
        entity.setPaused(false);
        MonitorEntity saved = monitorRepository.save(entity);
        return toResponse(saved);
    }

    public List<MonitorResponse> getAllMonitors() {
        return monitorRepository.findAll().stream().map(this::toResponse).toList();
    }

    public MonitorResponse getMonitor(Long id) {
        MonitorEntity e = monitorRepository
                .findById(id)
                .orElseThrow(() -> new RuntimeException("Monitor not found: " + id));
        return toResponse(e);
    }

    public MonitorResponse togglePause(Long id) {
        MonitorEntity e = monitorRepository
                .findById(id)
                .orElseThrow(() -> new RuntimeException("Monitor not found: " + id));
        e.setPaused(!e.isPaused());
        return toResponse(monitorRepository.save(e));
    }

    public void deleteMonitor(Long id) {
        monitorRepository.deleteById(id);
    }

    public List<MonitorResponse> getMonitorsByProject(String slug) {
        return monitorRepository.findByProjectSlug(slug).stream().map(this::toResponse).toList();
    }
}
