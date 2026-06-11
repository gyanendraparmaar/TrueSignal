package com.truesignal.server.controller;

import com.truesignal.common.dto.CheckResultResponse;
import com.truesignal.common.dto.CreateMonitorRequest;
import com.truesignal.common.dto.IncidentResponse;
import com.truesignal.common.dto.MonitorResponse;
import com.truesignal.server.entity.CheckResultEntity;
import com.truesignal.server.repository.CheckResultRepository;
import com.truesignal.server.service.IncidentService;
import com.truesignal.server.service.MonitorService;
import com.truesignal.server.sse.SseService;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitors")
public class MonitorController {

    private final MonitorService monitorService;
    private final CheckResultRepository checkResultRepository;
    private final IncidentService incidentService;
    private final SseService sseService;

    public MonitorController(
            MonitorService monitorService,
            CheckResultRepository checkResultRepository,
            IncidentService incidentService,
            SseService sseService) {
        this.monitorService = monitorService;
        this.checkResultRepository = checkResultRepository;
        this.incidentService = incidentService;
        this.sseService = sseService;
    }

    @PostMapping
    public ResponseEntity<MonitorResponse> createMonitor(@RequestBody CreateMonitorRequest req) {
        MonitorResponse created = monitorService.createMonitor(req);
        sseService.broadcast("monitor-created", created);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<MonitorResponse>> getAllMonitors() {
        return ResponseEntity.ok(monitorService.getAllMonitors());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MonitorResponse> getMonitor(@PathVariable Long id) {
        return ResponseEntity.ok(monitorService.getMonitor(id));
    }

    @GetMapping("/{id}/checks")
    public ResponseEntity<List<CheckResultResponse>> getCheckHistory(
            @PathVariable Long id, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        List<CheckResultEntity> list =
                checkResultRepository.findByMonitorIdOrderByCheckedAtDesc(id, PageRequest.of(page, size));
        List<CheckResultResponse> responses = list.stream().map(this::toCheckResultResponse).toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}/incidents")
    public ResponseEntity<List<IncidentResponse>> getMonitorIncidents(@PathVariable Long id) {
        return ResponseEntity.ok(incidentService.getIncidentsByMonitor(id));
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<MonitorResponse> togglePause(@PathVariable Long id) {
        MonitorResponse updated = monitorService.togglePause(id);
        sseService.broadcast("monitor-updated", updated);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMonitor(@PathVariable Long id) {
        monitorService.deleteMonitor(id);
        sseService.broadcast("monitor-deleted", id);
        return ResponseEntity.noContent().build();
    }

    private CheckResultResponse toCheckResultResponse(CheckResultEntity e) {
        return new CheckResultResponse(
                e.getId(),
                e.getMonitorId(),
                e.getNodeId(),
                e.getNodeRegion(),
                e.getStatus(),
                e.getResponseTimeMs(),
                e.getStatusCode(),
                e.getError(),
                e.getCheckedAt());
    }
}
