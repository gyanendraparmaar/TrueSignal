package com.truesignal.server.alerting;

import com.truesignal.common.enums.AlertChannelType;
import com.truesignal.server.entity.IncidentEntity;
import com.truesignal.server.entity.MonitorEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailAlertChannel implements AlertChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailAlertChannel.class);

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Override
    public AlertChannelType getType() {
        return AlertChannelType.EMAIL;
    }

    @Override
    public void send(MonitorEntity monitor, IncidentEntity incident, String eventType, String target) {
        if (mailSender == null) {
            log.warn("Email alert skipped: JavaMailSender not configured");
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(target);
            message.setSubject("[TrueSignal] " + eventType + ": " + monitor.getName());
            StringBuilder text = new StringBuilder();
            text.append("Monitor: ").append(monitor.getName()).append("\n");
            text.append("URL: ").append(monitor.getUrl()).append("\n");
            text.append("Event: ").append(eventType).append("\n");
            if (incident != null) {
                text.append("Incident ID: ").append(incident.getId()).append("\n");
                text.append("Cause: ").append(incident.getCause()).append("\n");
            }
            message.setText(text.toString());
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Email alert failed for monitor {}: {}", monitor.getName(), e.getMessage(), e);
        }
    }
}
