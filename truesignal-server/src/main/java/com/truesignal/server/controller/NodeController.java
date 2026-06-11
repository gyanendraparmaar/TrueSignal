package com.truesignal.server.controller;

import com.truesignal.common.dto.NodeResponse;
import com.truesignal.server.coordinator.ClusterCoordinator;
import com.truesignal.server.service.NodeService;
import com.truesignal.server.sse.SseService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/nodes")
public class NodeController {

    private final NodeService nodeService;
    private final ClusterCoordinator clusterCoordinator;
    private final SseService sseService;

    public NodeController(NodeService nodeService, ClusterCoordinator clusterCoordinator, SseService sseService) {
        this.nodeService = nodeService;
        this.clusterCoordinator = clusterCoordinator;
        this.sseService = sseService;
    }

    @GetMapping
    public ResponseEntity<List<NodeResponse>> getAllNodes() {
        return ResponseEntity.ok(nodeService.getAllNodes());
    }

    @DeleteMapping("/{nodeId}")
    public ResponseEntity<Void> removeNode(@PathVariable String nodeId) {
        boolean removed = clusterCoordinator.removeNode(nodeId);
        if (!removed) {
            return ResponseEntity.notFound().build();
        }
        sseService.broadcast("node-removed", nodeId);
        return ResponseEntity.noContent().build();
    }
}
