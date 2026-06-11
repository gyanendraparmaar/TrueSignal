package com.truesignal.server.repository;

import com.truesignal.common.enums.IncidentStatus;
import com.truesignal.server.entity.IncidentEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRepository extends JpaRepository<IncidentEntity, Long> {

    List<IncidentEntity> findByMonitorIdOrderByStartedAtDesc(Long monitorId);

    List<IncidentEntity> findByStatus(IncidentStatus status);

    Optional<IncidentEntity> findByMonitorIdAndStatus(Long monitorId, IncidentStatus status);

    List<IncidentEntity> findTop10ByOrderByStartedAtDesc();
}
