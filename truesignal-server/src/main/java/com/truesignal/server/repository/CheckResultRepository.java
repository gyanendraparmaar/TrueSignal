package com.truesignal.server.repository;

import com.truesignal.server.entity.CheckResultEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CheckResultRepository extends JpaRepository<CheckResultEntity, Long> {

    List<CheckResultEntity> findByMonitorIdOrderByCheckedAtDesc(Long monitorId, Pageable pageable);

    List<CheckResultEntity> findByMonitorIdAndCheckedAtAfter(Long monitorId, Instant after);

    @Query(
            "SELECT cr FROM CheckResultEntity cr WHERE cr.monitorId = :monitorId AND cr.checkedAt = "
                    + "(SELECT MAX(cr2.checkedAt) FROM CheckResultEntity cr2 WHERE cr2.monitorId = :monitorId AND cr2.nodeId = cr.nodeId)")
    List<CheckResultEntity> findLatestPerNode(@Param("monitorId") Long monitorId);

    @Query(
            "SELECT CAST(AVG(cr.responseTimeMs) AS long) FROM CheckResultEntity cr WHERE cr.monitorId = :monitorId AND cr.checkedAt > :since AND "
                    + "cr.status = com.truesignal.common.enums.CheckStatus.UP")
    Long findAvgResponseTime(@Param("monitorId") Long monitorId, @Param("since") Instant since);

    @Query("SELECT COUNT(cr) FROM CheckResultEntity cr WHERE cr.monitorId = :monitorId AND cr.checkedAt > :since")
    long countByMonitorIdSince(@Param("monitorId") Long monitorId, @Param("since") Instant since);

    @Query(
            "SELECT COUNT(cr) FROM CheckResultEntity cr WHERE cr.monitorId = :monitorId AND cr.checkedAt > :since AND "
                    + "cr.status = com.truesignal.common.enums.CheckStatus.UP")
    long countUpByMonitorIdSince(@Param("monitorId") Long monitorId, @Param("since") Instant since);
}
