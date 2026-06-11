package com.truesignal.server.controller;

import com.truesignal.common.dto.DashboardOverview;
import com.truesignal.common.dto.MonitorResponse;
import com.truesignal.common.dto.NodeResponse;
import com.truesignal.common.enums.CheckStatus;
import com.truesignal.common.enums.NodeStatus;
import com.truesignal.server.repository.MonitorNodeRepository;
import com.truesignal.server.repository.MonitorRepository;
import com.truesignal.server.service.IncidentService;
import com.truesignal.server.service.MonitorService;
import com.truesignal.server.service.NodeService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final MonitorService monitorService;
    private final IncidentService incidentService;
    private final NodeService nodeService;
    private final MonitorRepository monitorRepository;
    private final MonitorNodeRepository monitorNodeRepository;

    public DashboardController(
            MonitorService monitorService,
            IncidentService incidentService,
            NodeService nodeService,
            MonitorRepository monitorRepository,
            MonitorNodeRepository monitorNodeRepository) {
        this.monitorService = monitorService;
        this.incidentService = incidentService;
        this.nodeService = nodeService;
        this.monitorRepository = monitorRepository;
        this.monitorNodeRepository = monitorNodeRepository;
    }

    @GetMapping("/overview")
    public ResponseEntity<DashboardOverview> getOverview() {
        List<MonitorResponse> monitors = monitorService.getAllMonitors();
        int totalMonitors = (int) monitorRepository.count();
        int monitorsUp = (int) monitors.stream().filter(m -> m.currentStatus() == CheckStatus.UP).count();
        int monitorsDown = (int) monitors.stream().filter(m -> m.currentStatus() == CheckStatus.DOWN).count();
        int monitorsPaused = (int) monitors.stream().filter(MonitorResponse::paused).count();
        int activeIncidents = incidentService.getActiveIncidents().size();
        List<NodeResponse> nodes = nodeService.getAllNodes();
        int totalNodes = (int) monitorNodeRepository.count();
        int nodesAlive = (int) nodes.stream().filter(n -> n.status() == NodeStatus.ALIVE).count();

        double overallUptimePercent = 0.0;
        if (!monitors.isEmpty()) {
            double sum = monitors.stream().mapToDouble(MonitorResponse::uptimePercent).sum();
            overallUptimePercent = sum / monitors.size();
        }

        DashboardOverview overview = new DashboardOverview(
                totalMonitors,
                monitorsUp,
                monitorsDown,
                monitorsPaused,
                activeIncidents,
                totalNodes,
                nodesAlive,
                overallUptimePercent,
                monitors);
        return ResponseEntity.ok(overview);
    }
}
