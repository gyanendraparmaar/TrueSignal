package com.truesignal.server.diagnosis;

import com.truesignal.common.enums.DiagnosisCategory;
import com.truesignal.common.enums.DiagnosisConfidence;

public record DiagnosisResult(
        String diagnosis,
        DiagnosisCategory category,
        DiagnosisConfidence confidence,
        String explanation,
        String suggestion,
        String source
) {}
