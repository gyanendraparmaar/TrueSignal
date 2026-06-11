package com.truesignal.server.alerting;

import com.truesignal.common.enums.AlertChannelType;
import com.truesignal.server.entity.IncidentEntity;
import com.truesignal.server.entity.MonitorEntity;

public interface AlertChannel {

    void send(MonitorEntity monitor, IncidentEntity incident, String eventType, String target);

    AlertChannelType getType();
}
