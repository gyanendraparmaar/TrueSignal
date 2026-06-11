package com.truesignal.common.dto;

import java.util.List;

public record DashboardOverview(
        int totalMonitors,
        int monitorsUp,
        int monitorsDown,
        int monitorsPaused,
        int activeIncidents,
        int totalNodes,
        int nodesAlive,
        double overallUptimePercent,
        List<MonitorResponse> monitors
) {}
