package com.truesignal.monitor.checker;

import com.truesignal.common.enums.CheckStatus;
import com.truesignal.common.enums.MonitorType;
import com.truesignal.common.protocol.MonitorAssignment;

public interface HealthChecker {

    CheckResult check(MonitorAssignment.MonitorTask task);

    MonitorType supportedType();

    record CheckResult(CheckStatus status, long responseTimeMs, int statusCode, String error) {}
}
