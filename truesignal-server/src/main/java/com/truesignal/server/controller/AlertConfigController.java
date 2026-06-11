package com.truesignal.server.controller;

import com.truesignal.common.dto.CreateAlertRequest;
import com.truesignal.server.entity.AlertConfigEntity;
import com.truesignal.server.repository.AlertConfigRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alerts")
public class AlertConfigController {

    private final AlertConfigRepository alertConfigRepository;

    public AlertConfigController(AlertConfigRepository alertConfigRepository) {
        this.alertConfigRepository = alertConfigRepository;
    }

    @PostMapping
    public ResponseEntity<AlertConfigEntity> createAlert(@RequestBody CreateAlertRequest req) {
        AlertConfigEntity entity = new AlertConfigEntity();
        entity.setMonitorId(req.monitorId());
        entity.setChannelType(req.channelType());
        entity.setTarget(req.target());
        entity.setCooldownSeconds(req.cooldownSeconds());
        entity.setEnabled(true);
        AlertConfigEntity saved = alertConfigRepository.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping
    public ResponseEntity<List<AlertConfigEntity>> getAllAlerts() {
        return ResponseEntity.ok(alertConfigRepository.findByEnabledTrue());
    }

    @GetMapping("/monitor/{monitorId}")
    public ResponseEntity<List<AlertConfigEntity>> getAlertsForMonitor(@PathVariable Long monitorId) {
        return ResponseEntity.ok(alertConfigRepository.findByMonitorId(monitorId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAlert(@PathVariable Long id) {
        alertConfigRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
