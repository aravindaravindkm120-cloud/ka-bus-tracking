package com.kabus.tracking.domain.repository;

import com.kabus.tracking.domain.entity.LiveLocation;
import com.kabus.tracking.domain.enums.LiveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LiveLocationRepository extends JpaRepository<LiveLocation, Long> {

    Optional<LiveLocation> findByBusId(Long busId);

    List<LiveLocation> findByBusIdIn(Collection<Long> busIds);

    List<LiveLocation> findAllByOrderByCapturedAtDesc();

    List<LiveLocation> findByStatus(LiveStatus status);

    @Query("select l from LiveLocation l where l.trip.route.division.id in :divisionIds order by l.updatedAt desc")
    List<LiveLocation> findByDivisionIds(@Param("divisionIds") Collection<Long> divisionIds);

    @Query("select l from LiveLocation l where l.bus.depot.id in :depotIds order by l.updatedAt desc")
    List<LiveLocation> findByDepotIds(@Param("depotIds") Collection<Long> depotIds);

    @Query("select l from LiveLocation l where l.bus.town.id in :townIds order by l.updatedAt desc")
    List<LiveLocation> findByTownIds(@Param("townIds") Collection<Long> townIds);

    @Query("select l from LiveLocation l where l.latitude between :minLat and :maxLat "
            + "and l.longitude between :minLon and :maxLon and l.status <> :excluded")
    List<LiveLocation> findWithinBounds(@Param("minLat") BigDecimal minLat,
                                        @Param("maxLat") BigDecimal maxLat,
                                        @Param("minLon") BigDecimal minLon,
                                        @Param("maxLon") BigDecimal maxLon,
                                        @Param("excluded") LiveStatus excluded);

    @Query("select l from LiveLocation l where l.bus.depot.id in :depotIds and l.status in :statuses")
    List<LiveLocation> findByDepotIdsAndStatusIn(@Param("depotIds") Collection<Long> depotIds,
                                                 @Param("statuses") Collection<LiveStatus> statuses);

    @Query("select count(l) from LiveLocation l where l.bus.depot.id in :depotIds")
    long countByBusDepotIdIn(@Param("depotIds") Collection<Long> depotIds);

    @Query("select count(l) from LiveLocation l where l.bus.depot.id in :depotIds and l.status = :status")
    long countByBusDepotIdInAndStatus(@Param("depotIds") Collection<Long> depotIds,
                                      @Param("status") LiveStatus status);

    @Query("select count(l) from LiveLocation l where l.bus.town.id in :townIds and l.status = :status")
    long countByBusTownIdInAndStatus(@Param("townIds") Collection<Long> townIds,
                                     @Param("status") LiveStatus status);

    @Query("select distinct l.bus.id from LiveLocation l where l.status = :status and l.capturedAt < :threshold")
    List<Long> findBusIdsByStatusAndCapturedBefore(@Param("status") LiveStatus status,
                                                   @Param("threshold") LocalDateTime threshold);

    @Modifying
    @Query("update LiveLocation l set l.status = com.kabus.tracking.domain.enums.LiveStatus.STALE where l.status = com.kabus.tracking.domain.enums.LiveStatus.LIVE and l.capturedAt < :threshold")
    int markStale(@Param("threshold") LocalDateTime threshold);

    @Modifying
    @Query("update LiveLocation l set l.status = com.kabus.tracking.domain.enums.LiveStatus.OFFLINE where l.status <> com.kabus.tracking.domain.enums.LiveStatus.OFFLINE and l.capturedAt < :threshold")
    int markOffline(@Param("threshold") LocalDateTime threshold);
}