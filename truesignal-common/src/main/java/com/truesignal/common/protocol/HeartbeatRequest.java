package com.truesignal.common.protocol;

import java.time.Instant;

public record HeartbeatRequest(
        String nodeId,
        int activeChecks,
        long usedMemoryMb,
        double cpuLoad,
        Instant timestamp
) {}
