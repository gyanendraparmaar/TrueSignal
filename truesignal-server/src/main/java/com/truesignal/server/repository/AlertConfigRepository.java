package com.truesignal.server.repository;

import com.truesignal.server.entity.AlertConfigEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertConfigRepository extends JpaRepository<AlertConfigEntity, Long> {

    List<AlertConfigEntity> findByMonitorId(Long monitorId);

    List<AlertConfigEntity> findByMonitorIdIsNullAndEnabledTrue();

    List<AlertConfigEntity> findByEnabledTrue();
}
