package com.truesignal.server.service;

import com.truesignal.common.dto.NodeResponse;
import com.truesignal.server.entity.MonitorNodeEntity;
import com.truesignal.server.repository.MonitorNodeRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class NodeService {

    private final MonitorNodeRepository monitorNodeRepository;

    public NodeService(MonitorNodeRepository monitorNodeRepository) {
        this.monitorNodeRepository = monitorNodeRepository;
    }

    public NodeResponse toResponse(MonitorNodeEntity e) {
        return new NodeResponse(
                e.getNodeId(),
                e.getRegion(),
                e.getAddress(),
                e.getStatus(),
                e.getAssignedMonitors(),
                e.getLastHeartbeat(),
                e.getRegisteredAt());
    }

    public List<NodeResponse> getAllNodes() {
        return monitorNodeRepository.findAll().stream().map(this::toResponse).toList();
    }
}
