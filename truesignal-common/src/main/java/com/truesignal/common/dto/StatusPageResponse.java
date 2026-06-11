package com.truesignal.common.dto;

import com.truesignal.common.enums.CheckStatus;

import java.util.List;
import java.util.Map;

public record StatusPageResponse(
        String projectSlug,
        CheckStatus overallStatus,
        List<StatusPageMonitor> monitors,
        List<IncidentResponse> activeIncidents,
        List<IncidentResponse> recentIncidents
) {
    public record StatusPageMonitor(
            Long id,
            String name,
            String url,
            CheckStatus currentStatus,
            double uptimePercent,
            Map<String, Double> dailyUptime
    ) {}
}
