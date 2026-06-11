package com.truesignal.server.coordinator;

import com.truesignal.common.enums.NodeStatus;
import com.truesignal.common.protocol.HeartbeatRequest;
import com.truesignal.common.protocol.MonitorAssignment;
import com.truesignal.common.protocol.MonitorAssignment.MonitorTask;
import com.truesignal.common.protocol.NodeRegistrationRequest;
import com.truesignal.server.config.TrueSignalProperties;
import com.truesignal.server.entity.MonitorEntity;
import com.truesignal.server.entity.MonitorNodeEntity;
import com.truesignal.server.repository.MonitorNodeRepository;
import com.truesignal.server.repository.MonitorRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ClusterCoordinator {

    private static final Logger log = LoggerFactory.getLogger(ClusterCoordinator.class);

    private final MonitorRepository monitorRepository;
    private final MonitorNodeRepository monitorNodeRepository;
    private final ConsistentHashRing hashRing;
    private final TrueSignalProperties properties;

    public ClusterCoordinator(
            MonitorRepository monitorRepository,
            MonitorNodeRepository monitorNodeRepository,
            ConsistentHashRing hashRing,
            TrueSignalProperties properties) {
        this.monitorRepository = monitorRepository;
        this.monitorNodeRepository = monitorNodeRepository;
        this.hashRing = hashRing;
        this.properties = properties;
    }

    public MonitorAssignment registerNode(NodeRegistrationRequest req) {
        Instant now = Instant.now();
        Optional<MonitorNodeEntity> existing = monitorNodeRepository.findById(req.nodeId());
        MonitorNodeEntity node =
                existing.orElseGet(() -> {
                    MonitorNodeEntity n = new MonitorNodeEntity();
                    n.setNodeId(req.nodeId());
                    n.setRegisteredAt(now);
                    return n;
                });
        node.setRegion(req.region());
        node.setAddress(req.address() + ":" + req.port());
        node.setStatus(NodeStatus.ALIVE);
        node.setLastHeartbeat(now);
        monitorNodeRepository.save(node);

        hashRing.removeNode(req.nodeId());
        hashRing.addNode(req.nodeId());

        List<MonitorEntity> monitors = monitorRepository.findByPausedFalse();
        int assigned = countAssignedForNode(req.nodeId(), monitors);
        node.setAssignedMonitors(assigned);
        monitorNodeRepository.save(node);

        List<MonitorTask> tasks = buildTasksForNode(req.nodeId(), monitors);
        log.info("Node {} registered in region {}, assigned {} monitors", req.nodeId(), req.region(), assigned);
        return new MonitorAssignment(tasks);
    }

    public void handleHeartbeat(HeartbeatRequest req) {
        Optional<MonitorNodeEntity> opt = monitorNodeRepository.findById(req.nodeId());
        if (opt.isEmpty()) {
            log.warn("Heartbeat from unknown node {}", req.nodeId());
            return;
        }
        MonitorNodeEntity node = opt.get();
        node.setLastHeartbeat(Instant.now());
        if (node.getStatus() == NodeStatus.SUSPECT) {
            node.setStatus(NodeStatus.ALIVE);
        }
        monitorNodeRepository.save(node);
    }

    @Scheduled(fixedRateString = "${truesignal.heartbeat.check-interval-ms:10000}")
    public void checkNodeHealth() {
        Instant now = Instant.now();
        long timeoutDeadMs = properties.getHeartbeat().getTimeoutDeadMs();
        long timeoutSuspectMs = properties.getHeartbeat().getTimeoutSuspectMs();
        Instant deadThreshold = now.minusMillis(timeoutDeadMs);
        Instant suspectThreshold = now.minusMillis(timeoutSuspectMs);
        Instant purgeThreshold = now.minusMillis(timeoutDeadMs * 10);

        List<MonitorNodeEntity> staleDead = monitorNodeRepository.findByLastHeartbeatBefore(deadThreshold);
        for (MonitorNodeEntity node : staleDead) {
            if (node.getStatus() != NodeStatus.DEAD) {
                node.setStatus(NodeStatus.DEAD);
                monitorNodeRepository.save(node);
                reassignMonitors(node.getNodeId());
            }
        }

        List<MonitorNodeEntity> staleSuspect = monitorNodeRepository.findByLastHeartbeatBefore(suspectThreshold);
        for (MonitorNodeEntity node : staleSuspect) {
            if (node.getStatus() == NodeStatus.ALIVE) {
                node.setStatus(NodeStatus.SUSPECT);
                monitorNodeRepository.save(node);
                log.warn("Node {} is SUSPECT (heartbeat stale)", node.getNodeId());
            }
        }

        List<MonitorNodeEntity> toPurge = monitorNodeRepository.findByLastHeartbeatBefore(purgeThreshold);
        for (MonitorNodeEntity node : toPurge) {
            if (node.getStatus() == NodeStatus.DEAD) {
                log.info("Auto-purging stale dead node {} (region: {}, last heartbeat: {})",
                        node.getNodeId(), node.getRegion(), node.getLastHeartbeat());
                hashRing.removeNode(node.getNodeId());
                monitorNodeRepository.delete(node);
            }
        }
    }

    public boolean removeNode(String nodeId) {
        Optional<MonitorNodeEntity> opt = monitorNodeRepository.findById(nodeId);
        if (opt.isEmpty()) {
            return false;
        }
        MonitorNodeEntity node = opt.get();
        hashRing.removeNode(nodeId);
        monitorNodeRepository.delete(node);

        List<MonitorEntity> monitors = monitorRepository.findByPausedFalse();
        List<MonitorNodeEntity> aliveNodes = monitorNodeRepository.findByStatus(NodeStatus.ALIVE);
        for (MonitorNodeEntity alive : aliveNodes) {
            int count = countAssignedForNode(alive.getNodeId(), monitors);
            alive.setAssignedMonitors(count);
            monitorNodeRepository.save(alive);
        }
        log.info("Node {} removed manually, monitors reassigned", nodeId);
        return true;
    }

    private void reassignMonitors(String deadNodeId) {
        hashRing.removeNode(deadNodeId);
        List<MonitorEntity> monitors = monitorRepository.findByPausedFalse();
        List<MonitorNodeEntity> aliveNodes = monitorNodeRepository.findByStatus(NodeStatus.ALIVE);
        for (MonitorNodeEntity node : aliveNodes) {
            int count = countAssignedForNode(node.getNodeId(), monitors);
            node.setAssignedMonitors(count);
            monitorNodeRepository.save(node);
        }
        log.info("Node {} marked DEAD, monitors reassigned", deadNodeId);
    }

    public MonitorAssignment getAssignmentForNode(String nodeId) {
        List<MonitorEntity> monitors = monitorRepository.findByPausedFalse();
        List<MonitorTask> tasks = buildTasksForNode(nodeId, monitors);
        return new MonitorAssignment(tasks);
    }

    private int countAssignedForNode(String nodeId, List<MonitorEntity> monitors) {
        int count = 0;
        for (MonitorEntity m : monitors) {
            if (nodeId.equals(hashRing.getAssignedNode(m.getId()))) {
                count++;
            }
        }
        return count;
    }

    private List<MonitorTask> buildTasksForNode(String nodeId, List<MonitorEntity> monitors) {
        List<MonitorTask> tasks = new ArrayList<>();
        for (MonitorEntity m : monitors) {
            if (nodeId.equals(hashRing.getAssignedNode(m.getId()))) {
                tasks.add(
                        new MonitorTask(
                                m.getId(),
                                m.getUrl(),
                                m.getType(),
                                m.getIntervalSeconds(),
                                m.getTimeoutMs(),
                                m.getExpectedStatusCode(),
                                m.getKeyword()));
            }
        }
        return tasks;
    }
}
