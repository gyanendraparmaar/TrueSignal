package com.truesignal.server.service;

import com.truesignal.common.dto.IncidentResponse;
import com.truesignal.common.dto.StatusPageResponse;
import com.truesignal.common.enums.CheckStatus;
import com.truesignal.common.enums.IncidentStatus;
import com.truesignal.server.entity.CheckResultEntity;
import com.truesignal.server.entity.IncidentEntity;
import com.truesignal.server.entity.MonitorEntity;
import com.truesignal.server.repository.CheckResultRepository;
import com.truesignal.server.repository.IncidentRepository;
import com.truesignal.server.repository.MonitorRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class StatusPageService {

    private static final ZoneOffset ZONE = ZoneOffset.UTC;

    private final MonitorRepository monitorRepository;
    private final CheckResultRepository checkResultRepository;
    private final IncidentRepository incidentRepository;
    private final IncidentService incidentService;

    public StatusPageService(
            MonitorRepository monitorRepository,
            CheckResultRepository checkResultRepository,
            IncidentRepository incidentRepository,
            IncidentService incidentService) {
        this.monitorRepository = monitorRepository;
        this.checkResultRepository = checkResultRepository;
        this.incidentRepository = incidentRepository;
        this.incidentService = incidentService;
    }

    public StatusPageResponse getStatusPage(String projectSlug) {
        List<MonitorEntity> monitors = monitorRepository.findByProjectSlug(projectSlug);
        Set<Long> monitorIds = monitors.stream().map(MonitorEntity::getId).collect(Collectors.toSet());

        List<StatusPageResponse.StatusPageMonitor> pageMonitors =
                monitors.stream().map(this::toStatusPageMonitor).toList();

        CheckStatus overallStatus = CheckStatus.UP;
        for (StatusPageResponse.StatusPageMonitor m : pageMonitors) {
            if (m.currentStatus() == CheckStatus.DOWN) {
                overallStatus = CheckStatus.DOWN;
                break;
            }
        }
        if (overallStatus != CheckStatus.DOWN) {
            for (StatusPageResponse.StatusPageMonitor m : pageMonitors) {
                if (m.currentStatus() == CheckStatus.DEGRADED) {
                    overallStatus = CheckStatus.DEGRADED;
                    break;
                }
            }
        }

        List<IncidentResponse> activeIncidents = incidentRepository.findByStatus(IncidentStatus.ONGOING).stream()
                .filter(i -> monitorIds.contains(i.getMonitorId()))
                .map(incidentService::toResponse)
                .toList();

        List<IncidentResponse> recentResolved = incidentRepository.findByStatus(IncidentStatus.RESOLVED).stream()
                .filter(i -> monitorIds.contains(i.getMonitorId()))
                .sorted(Comparator.comparing(IncidentEntity::getResolvedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .map(incidentService::toResponse)
                .toList();

        return new StatusPageResponse(projectSlug, overallStatus, pageMonitors, activeIncidents, recentResolved);
    }

    private StatusPageResponse.StatusPageMonitor toStatusPageMonitor(MonitorEntity m) {
        Instant since30d = Instant.now().minus(30, ChronoUnit.DAYS);
        long total = checkResultRepository.countByMonitorIdSince(m.getId(), since30d);
        double uptimePercent = total == 0 ? 100.0 : (100.0 * checkResultRepository.countUpByMonitorIdSince(m.getId(), since30d)) / total;

        Map<String, Double> dailyUptime = computeDailyUptime(m.getId());

        return new StatusPageResponse.StatusPageMonitor(
                m.getId(), m.getName(), m.getUrl(), m.getCurrentStatus(), uptimePercent, dailyUptime);
    }

    private Map<String, Double> computeDailyUptime(Long monitorId) {
        Instant since90d = Instant.now().minus(90, ChronoUnit.DAYS);
        List<CheckResultEntity> results = checkResultRepository.findByMonitorIdAndCheckedAtAfter(monitorId, since90d);

        Map<LocalDate, Long> totalByDay = new HashMap<>();
        Map<LocalDate, Long> upByDay = new HashMap<>();
        for (CheckResultEntity cr : results) {
            LocalDate d = cr.getCheckedAt().atZone(ZONE).toLocalDate();
            totalByDay.merge(d, 1L, Long::sum);
            if (cr.getStatus() == CheckStatus.UP) {
                upByDay.merge(d, 1L, Long::sum);
            }
        }

        LocalDate today = LocalDate.now(ZONE);
        Map<String, Double> out = new LinkedHashMap<>();
        for (int i = 89; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            String key = d.toString();
            long dayTotal = totalByDay.getOrDefault(d, 0L);
            if (dayTotal == 0) {
                out.put(key, null);
            } else {
                long up = upByDay.getOrDefault(d, 0L);
                out.put(key, (100.0 * up) / dayTotal);
            }
        }
        return out;
    }
}
