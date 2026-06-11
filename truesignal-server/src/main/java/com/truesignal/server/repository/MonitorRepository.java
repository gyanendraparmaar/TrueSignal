package com.truesignal.server.repository;

import com.truesignal.common.enums.CheckStatus;
import com.truesignal.server.entity.MonitorEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MonitorRepository extends JpaRepository<MonitorEntity, Long> {

    List<MonitorEntity> findByProjectSlug(String slug);

    List<MonitorEntity> findByPausedFalse();

    List<MonitorEntity> findByCurrentStatus(CheckStatus status);

    @Query("SELECT DISTINCT m.projectSlug FROM MonitorEntity m ORDER BY m.projectSlug")
    List<String> findDistinctProjectSlugs();
}
