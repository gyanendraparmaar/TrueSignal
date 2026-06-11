package com.truesignal.common.protocol;

import com.truesignal.common.enums.CheckStatus;

import java.time.Instant;

public record CheckResultReport(
        String nodeId,
        String nodeRegion,
        Long monitorId,
        CheckStatus status,
        long responseTimeMs,
        int statusCode,
        String error,
        Instant checkedAt
) {}
