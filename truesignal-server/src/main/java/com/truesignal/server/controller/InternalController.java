package com.truesignal.server.controller;

import com.truesignal.common.protocol.CheckResultReport;
import com.truesignal.common.protocol.HeartbeatRequest;
import com.truesignal.common.protocol.MonitorAssignment;
import com.truesignal.common.protocol.NodeRegistrationRequest;
import com.truesignal.server.coordinator.ClusterCoordinator;
import com.truesignal.server.quorum.QuorumEngine;
import com.truesignal.server.sse.SseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal")
public class InternalController {

    private final ClusterCoordinator clusterCoordinator;
    private final QuorumEngine quorumEngine;
    private final SseService sseService;

    public InternalController(ClusterCoordinator clusterCoordinator, QuorumEngine quorumEngine, SseService sseService) {
        this.clusterCoordinator = clusterCoordinator;
        this.quorumEngine = quorumEngine;
        this.sseService = sseService;
    }

    @PostMapping("/nodes/register")
    public ResponseEntity<MonitorAssignment> registerNode(@RequestBody NodeRegistrationRequest req) {
        MonitorAssignment assignment = clusterCoordinator.registerNode(req);
        sseService.broadcast("node-registered", req);
        return ResponseEntity.ok(assignment);
    }

    @PostMapping("/nodes/heartbeat")
    public ResponseEntity<Void> handleHeartbeat(@RequestBody HeartbeatRequest req) {
        clusterCoordinator.handleHeartbeat(req);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/results")
    public ResponseEntity<Void> reportCheckResult(@RequestBody CheckResultReport report) {
        quorumEngine.processCheckResult(report);
        sseService.broadcast("check-result", report);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/nodes/{nodeId}/assignments")
    public ResponseEntity<MonitorAssignment> getAssignments(@PathVariable String nodeId) {
        return ResponseEntity.ok(clusterCoordinator.getAssignmentForNode(nodeId));
    }
}
