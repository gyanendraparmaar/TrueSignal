package com.truesignal.common.dto;

import com.truesignal.common.enums.CheckStatus;

import java.time.Instant;

public record CheckResultResponse(
        Long id,
        Long monitorId,
        String nodeId,
        String nodeRegion,
        CheckStatus status,
        long responseTimeMs,
        int statusCode,
        String error,
        Instant checkedAt
) {}
