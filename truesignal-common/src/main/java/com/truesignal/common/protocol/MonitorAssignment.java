package com.truesignal.common.protocol;

import com.truesignal.common.enums.MonitorType;

import java.util.List;

public record MonitorAssignment(
        List<MonitorTask> tasks
) {
    public record MonitorTask(
            Long monitorId,
            String url,
            MonitorType type,
            int intervalSeconds,
            int timeoutMs,
            int expectedStatusCode,
            String keyword
    ) {}
}
