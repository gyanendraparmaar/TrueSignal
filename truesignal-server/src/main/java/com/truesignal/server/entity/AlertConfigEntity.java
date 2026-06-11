package com.truesignal.server.entity;

import com.truesignal.common.enums.AlertChannelType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "alert_configs")
public class AlertConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "monitor_id")
    private Long monitorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false)
    private AlertChannelType channelType;

    @Column(nullable = false)
    private String target;

    @Column(name = "cooldown_seconds", nullable = false)
    private int cooldownSeconds = 300;

    @Column(name = "last_alerted_at")
    private Instant lastAlertedAt;

    @Column(nullable = false)
    private boolean enabled = true;

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

    public AlertChannelType getChannelType() {
        return channelType;
    }

    public void setChannelType(AlertChannelType channelType) {
        this.channelType = channelType;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public void setCooldownSeconds(int cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }

    public Instant getLastAlertedAt() {
        return lastAlertedAt;
    }

    public void setLastAlertedAt(Instant lastAlertedAt) {
        this.lastAlertedAt = lastAlertedAt;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
