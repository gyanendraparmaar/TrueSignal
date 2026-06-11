package com.truesignal.common.dto;

import com.truesignal.common.enums.NodeStatus;

import java.time.Instant;

public record NodeResponse(
        String nodeId,
        String region,
        String address,
        NodeStatus status,
        int assignedMonitors,
        Instant lastHeartbeat,
        Instant registeredAt
) {}
