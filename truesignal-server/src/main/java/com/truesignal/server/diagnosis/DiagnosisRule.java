package com.truesignal.server.diagnosis;

import com.truesignal.server.entity.CheckResultEntity;
import com.truesignal.server.entity.MonitorEntity;
import java.util.List;
import java.util.Optional;

public interface DiagnosisRule {

    Optional<DiagnosisResult> evaluate(
            MonitorEntity monitor,
            List<CheckResultEntity> failedResults,
            List<CheckResultEntity> recentHistory);

    int priority();
}
