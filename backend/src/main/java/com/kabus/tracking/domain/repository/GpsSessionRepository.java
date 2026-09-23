package com.kabus.tracking.domain.repository;

import com.kabus.tracking.domain.entity.GpsSession;
import com.kabus.tracking.domain.enums.GpsSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GpsSessionRepository extends JpaRepository<GpsSession, Long> {

    Optional<GpsSession> findBySessionKey(String sessionKey);

    Optional<GpsSession> findFirstByCrewUserIdAndStatusOrderByStartedAtDesc(Long userId, GpsSessionStatus status);

    Optional<GpsSession> findFirstByBusIdAndStatusOrderByStartedAtDesc(Long busId, GpsSessionStatus status);

    List<GpsSession> findByStatusAndLastHeartbeatAtBefore(GpsSessionStatus status, LocalDateTime threshold);

    long countByStatus(GpsSessionStatus status);

    long countByStatusAndBusDepotIdIn(GpsSessionStatus status, Collection<Long> depotIds);

    long countByStatusAndBusTownIdIn(GpsSessionStatus status, Collection<Long> townIds);

    @Query("select g from GpsSession g where g.status = :status and g.lastHeartbeatAt < :threshold "
            + "or (g.status = :status and g.lastHeartbeatAt is null and g.startedAt < :startedBefore)")
    List<GpsSession> findExpiredSessions(@Param("status") GpsSessionStatus status,
                                         @Param("threshold") LocalDateTime threshold,
                                         @Param("startedBefore") LocalDateTime startedBefore);
}