package com.truesignal.server.alerting;

import com.truesignal.common.enums.AlertChannelType;
import com.truesignal.server.entity.IncidentEntity;
import com.truesignal.server.entity.MonitorEntity;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class WebhookAlertChannel implements AlertChannel {

    private static final Logger log = LoggerFactory.getLogger(WebhookAlertChannel.class);

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public AlertChannelType getType() {
        return AlertChannelType.WEBHOOK;
    }

    @Override
    public void send(MonitorEntity monitor, IncidentEntity incident, String eventType, String target) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("monitorId", monitor.getId());
            body.put("monitorName", monitor.getName());
            body.put("monitorUrl", monitor.getUrl());
            body.put("eventType", eventType);
            if (incident != null) {
                body.put("incidentId", incident.getId());
                body.put("cause", incident.getCause());
            }
            body.put("timestamp", Instant.now().toString());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForEntity(target, new HttpEntity<>(body, headers), String.class);
        } catch (Exception e) {
            log.error("Webhook alert failed for monitor {}: {}", monitor.getName(), e.getMessage(), e);
        }
    }
}
