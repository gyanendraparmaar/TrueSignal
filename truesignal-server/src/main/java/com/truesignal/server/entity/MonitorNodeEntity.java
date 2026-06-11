package com.truesignal.server.entity;

import com.truesignal.common.enums.NodeStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "monitor_nodes")
public class MonitorNodeEntity {

    @Id
    @Column(name = "node_id", nullable = false)
    private String nodeId;

    @Column(nullable = false)
    private String region;

    @Column(nullable = false)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NodeStatus status = NodeStatus.ALIVE;

    @Column(name = "assigned_monitors", nullable = false)
    private int assignedMonitors = 0;

    @Column(name = "last_heartbeat", nullable = false)
    private Instant lastHeartbeat;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        registeredAt = now;
        lastHeartbeat = now;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public NodeStatus getStatus() {
        return status;
    }

    public void setStatus(NodeStatus status) {
        this.status = status;
    }

    public int getAssignedMonitors() {
        return assignedMonitors;
    }

    public void setAssignedMonitors(int assignedMonitors) {
        this.assignedMonitors = assignedMonitors;
    }

    public Instant getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(Instant lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(Instant registeredAt) {
        this.registeredAt = registeredAt;
    }
}
