package com.truesignal.server.entity;

import com.truesignal.common.enums.DiagnosisCategory;
import com.truesignal.common.enums.DiagnosisConfidence;
import com.truesignal.common.enums.IncidentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "incidents")
public class IncidentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "monitor_id", nullable = false)
    private Long monitorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentStatus status = IncidentStatus.ONGOING;

    @Column(length = 500, nullable = false)
    private String cause;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(length = 500)
    private String diagnosis;

    @Enumerated(EnumType.STRING)
    @Column(name = "diagnosis_category")
    private DiagnosisCategory diagnosisCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "diagnosis_confidence")
    private DiagnosisConfidence diagnosisConfidence;

    @Column(name = "diagnosis_explanation", length = 2000)
    private String diagnosisExplanation;

    @Column(name = "diagnosis_suggestion", length = 1000)
    private String diagnosisSuggestion;

    @Column(name = "diagnosis_source", length = 20)
    private String diagnosisSource;

    @PrePersist
    void onCreate() {
        startedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMonitorId() {
        return monitorId;
    }

    public void setMonitorId(Long monitorId) {
        this.monitorId = monitorId;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public void setStatus(IncidentStatus status) {
        this.status = status;
    }

    public String getCause() {
        return cause;
    }

    public void setCause(String cause) {
        this.cause = cause;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public String getDiagnosis() {
        return diagnosis;
    }

    public void setDiagnosis(String diagnosis) {
        this.diagnosis = diagnosis;
    }

    public DiagnosisCategory getDiagnosisCategory() {
        return diagnosisCategory;
    }

    public void setDiagnosisCategory(DiagnosisCategory diagnosisCategory) {
        this.diagnosisCategory = diagnosisCategory;
    }

    public DiagnosisConfidence getDiagnosisConfidence() {
        return diagnosisConfidence;
    }

    public void setDiagnosisConfidence(DiagnosisConfidence diagnosisConfidence) {
        this.diagnosisConfidence = diagnosisConfidence;
    }

    public String getDiagnosisExplanation() {
        return diagnosisExplanation;
    }

    public void setDiagnosisExplanation(String diagnosisExplanation) {
        this.diagnosisExplanation = diagnosisExplanation;
    }

    public String getDiagnosisSuggestion() {
        return diagnosisSuggestion;
    }

    public void setDiagnosisSuggestion(String diagnosisSuggestion) {
        this.diagnosisSuggestion = diagnosisSuggestion;
    }

    public String getDiagnosisSource() {
        return diagnosisSource;
    }

    public void setDiagnosisSource(String diagnosisSource) {
        this.diagnosisSource = diagnosisSource;
    }
}
