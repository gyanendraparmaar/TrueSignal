package com.truesignal.server.repository;

import com.truesignal.common.enums.NodeStatus;
import com.truesignal.server.entity.MonitorNodeEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonitorNodeRepository extends JpaRepository<MonitorNodeEntity, String> {

    List<MonitorNodeEntity> findByStatus(NodeStatus status);

    List<MonitorNodeEntity> findByLastHeartbeatBefore(Instant threshold);
}
