package com.truesignal.common.dto;

import com.truesignal.common.enums.MonitorType;

public record CreateMonitorRequest(
        String name,
        String url,
        MonitorType type,
        int intervalSeconds,
        int timeoutMs,
        int expectedStatusCode,
        String keyword,
        String projectSlug
) {
    public CreateMonitorRequest {
        if (intervalSeconds <= 0) intervalSeconds = 60;
        if (timeoutMs <= 0) timeoutMs = 10000;
        if (expectedStatusCode <= 0) expectedStatusCode = 200;
        if (projectSlug == null || projectSlug.isBlank()) projectSlug = "default";
    }
}
