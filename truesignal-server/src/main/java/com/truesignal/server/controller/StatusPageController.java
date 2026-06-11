package com.truesignal.server.controller;

import com.truesignal.common.dto.StatusPageResponse;
import com.truesignal.server.service.StatusPageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/status")
public class StatusPageController {

    private final StatusPageService statusPageService;

    public StatusPageController(StatusPageService statusPageService) {
        this.statusPageService = statusPageService;
    }

    @GetMapping("/{slug}")
    public ResponseEntity<StatusPageResponse> getStatusPage(@PathVariable String slug) {
        return ResponseEntity.ok(statusPageService.getStatusPage(slug));
    }
}
