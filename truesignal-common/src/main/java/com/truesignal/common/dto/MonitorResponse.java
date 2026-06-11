package com.truesignal.common.dto;

import com.truesignal.common.enums.CheckStatus;
import com.truesignal.common.enums.MonitorType;

import java.time.Instant;

public record MonitorResponse(
        Long id,
        String name,
        String url,
        MonitorType type,
        int intervalSeconds,
        int timeoutMs,
        int expectedStatusCode,
        String keyword,
        String projectSlug,
        boolean paused,
        CheckStatus currentStatus,
        double uptimePercent,
        Long avgResponseTimeMs,
        Instant lastCheckedAt,
        Instant createdAt
) {}
