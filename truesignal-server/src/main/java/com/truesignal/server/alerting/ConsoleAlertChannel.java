package com.truesignal.server.alerting;

import com.truesignal.common.enums.AlertChannelType;
import com.truesignal.server.entity.IncidentEntity;
import com.truesignal.server.entity.MonitorEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ConsoleAlertChannel implements AlertChannel {

    private static final Logger log = LoggerFactory.getLogger(ConsoleAlertChannel.class);

    @Override
    public AlertChannelType getType() {
        return AlertChannelType.CONSOLE;
    }

    @Override
    public void send(MonitorEntity monitor, IncidentEntity incident, String eventType, String target) {
        String cause = incident != null && incident.getCause() != null ? incident.getCause() : "N/A";
        log.warn(
                "[ALERT] {} — Monitor: {} ({}), Target: {}, Cause: {}",
                eventType,
                monitor.getName(),
                monitor.getUrl(),
                target,
                cause);
    }
}
