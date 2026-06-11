package com.truesignal.server.controller;

import com.truesignal.common.dto.IncidentResponse;
import com.truesignal.server.service.IncidentService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @GetMapping
    public ResponseEntity<List<IncidentResponse>> getActiveIncidents(
            @RequestParam(required = false) String project) {
        return ResponseEntity.ok(incidentService.getActiveIncidents(project));
    }

    @GetMapping("/recent")
    public ResponseEntity<List<IncidentResponse>> getRecentIncidents(
            @RequestParam(required = false) String project) {
        return ResponseEntity.ok(incidentService.getRecentIncidents(project));
    }
}
