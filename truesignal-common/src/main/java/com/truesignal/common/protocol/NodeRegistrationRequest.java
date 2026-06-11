package com.truesignal.common.protocol;

public record NodeRegistrationRequest(
        String nodeId,
        String region,
        String address,
        int port
) {}
