package com.truesignal.server.alerting;

import com.truesignal.common.enums.AlertChannelType;
import com.truesignal.server.entity.AlertConfigEntity;
import com.truesignal.server.entity.IncidentEntity;
import com.truesignal.server.entity.MonitorEntity;
import com.truesignal.server.repository.AlertConfigRepository;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AlertEngine {

    private static final Logger log = LoggerFactory.getLogger(AlertEngine.class);

    private final AlertConfigRepository alertConfigRepository;
    private final List<AlertChannel> channels;
    private final Map<AlertChannelType, AlertChannel> channelMap = new EnumMap<>(AlertChannelType.class);

    public AlertEngine(AlertConfigRepository alertConfigRepository, List<AlertChannel> channels) {
        this.alertConfigRepository = alertConfigRepository;
        this.channels = channels;
    }

    @PostConstruct
    void buildChannelMap() {
        for (AlertChannel channel : channels) {
            channelMap.put(channel.getType(), channel);
        }
    }

    public void fireAlert(MonitorEntity monitor, IncidentEntity incident, String eventType) {
        List<AlertConfigEntity> configs = new ArrayList<>();
        configs.addAll(alertConfigRepository.findByMonitorId(monitor.getId()));
        configs.addAll(alertConfigRepository.findByMonitorIdIsNullAndEnabledTrue());
        Instant now = Instant.now();
        for (AlertConfigEntity config : configs) {
            if (!config.isEnabled()) {
                continue;
            }
            Instant last = config.getLastAlertedAt();
            if (last != null) {
                long elapsedSeconds = Duration.between(last, now).getSeconds();
                if (elapsedSeconds < config.getCooldownSeconds()) {
                    continue;
                }
            }
            AlertChannel channel = channelMap.get(config.getChannelType());
            if (channel != null) {
                channel.send(monitor, incident, eventType, config.getTarget());
                config.setLastAlertedAt(now);
                alertConfigRepository.save(config);
                log.info(
                        "Alert sent via {} to {} for monitor {}",
                        config.getChannelType(),
                        config.getTarget(),
                        monitor.getName());
            }
        }
    }
}
