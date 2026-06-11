package com.truesignal.server.controller;

import com.truesignal.server.repository.MonitorRepository;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final MonitorRepository monitorRepository;

    public ProjectController(MonitorRepository monitorRepository) {
        this.monitorRepository = monitorRepository;
    }

    @GetMapping
    public ResponseEntity<List<String>> getProjects() {
        return ResponseEntity.ok(monitorRepository.findDistinctProjectSlugs());
    }
}
