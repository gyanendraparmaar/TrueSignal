package com.truesignal.common.dto;

import com.truesignal.common.enums.AlertChannelType;

public record CreateAlertRequest(
        Long monitorId,
        AlertChannelType channelType,
        String target,
        int cooldownSeconds
) {
    public CreateAlertRequest {
        if (cooldownSeconds <= 0) cooldownSeconds = 300;
    }
}
