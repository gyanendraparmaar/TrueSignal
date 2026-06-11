package com.truesignal.common.dto;

import com.truesignal.common.enums.DiagnosisCategory;
import com.truesignal.common.enums.DiagnosisConfidence;
import com.truesignal.common.enums.IncidentStatus;

import java.time.Instant;

public record IncidentResponse(
        Long id,
        Long monitorId,
        String monitorName,
        String monitorUrl,
        String projectSlug,
        IncidentStatus status,
        String cause,
        Instant startedAt,
        Instant resolvedAt,
        long durationSeconds,
        String diagnosis,
        DiagnosisCategory diagnosisCategory,
        DiagnosisConfidence diagnosisConfidence,
        String diagnosisExplanation,
        String diagnosisSuggestion,
        String diagnosisSource
) {}
