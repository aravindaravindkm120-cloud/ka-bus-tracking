package com.kabus.tracking.domain.repository;

import com.kabus.tracking.domain.entity.Trip;
import com.kabus.tracking.domain.enums.TripStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, Long> {

    List<Trip> findByTripNumberAndTripDate(String tripNumber, LocalDate tripDate);

    long countByRouteIdAndTripDate(Long routeId, LocalDate tripDate);

    Page<Trip> findByBusDepotIdIn(Collection<Long> depotIds, Pageable pageable);

    @Query("select t from Trip t where t.bus.depot.id in :depotIds "
            + "and (:date is null or t.tripDate = :date) "
            + "and (:status is null or t.status = :status) "
            + "and (:term is null or lower(t.tripNumber) like lower(concat('%', :term, '%')) "
            + "     or lower(t.route.code) like lower(concat('%', :term, '%')) "
            + "     or lower(t.route.name) like lower(concat('%', :term, '%')) "
            + "     or lower(t.bus.registrationNo) like lower(concat('%', :term, '%'))) "
            + "order by t.tripDate desc, t.scheduledDeparture desc")
    Page<Trip> search(@Param("depotIds") Collection<Long> depotIds,
                      @Param("date") LocalDate date,
                      @Param("status") TripStatus status,
                      @Param("term") String term,
                      Pageable pageable);

    @Query("select t from Trip t where t.bus.town.id in :townIds "
            + "and (:date is null or t.tripDate = :date) "
            + "and (:status is null or t.status = :status) "
            + "and (:term is null or lower(t.tripNumber) like lower(concat('%', :term, '%')) "
            + "     or lower(t.route.code) like lower(concat('%', :term, '%')) "
            + "     or lower(t.route.name) like lower(concat('%', :term, '%')) "
            + "     or lower(t.bus.registrationNo) like lower(concat('%', :term, '%'))) "
            + "order by t.tripDate desc, t.scheduledDeparture desc")
    Page<Trip> searchByTownIds(@Param("townIds") Collection<Long> townIds,
                               @Param("date") LocalDate date,
                               @Param("status") TripStatus status,
                               @Param("term") String term,
                               Pageable pageable);

    List<Trip> findByBusIdAndStatus(Long busId, TripStatus status);

    List<Trip> findByBusIdInAndStatus(List<Long> busIds, TripStatus status);

    List<Trip> findByRouteIdInAndStatus(List<Long> routeIds, TripStatus status);

    Optional<Trip> findFirstByBusIdAndStatus(Long busId, TripStatus status);

    long countByRouteIdInAndStatus(List<Long> routeIds, TripStatus status);

    long countByRouteIdIn(List<Long> routeIds);

    List<Trip> findByStatusAndTripDate(TripStatus status, LocalDate tripDate);

    List<Trip> findByRouteIdIn(List<Long> routeIds);

    long countByStatusAndBusDepotIdIn(TripStatus status, Collection<Long> depotIds);

    long countByStatusAndBusTownIdIn(TripStatus status, Collection<Long> townIds);

    Page<Trip> findByStatusAndBusDepotIdIn(TripStatus status, Collection<Long> depotIds, Pageable pageable);

    Page<Trip> findByTripDateAndBusDepotIdIn(LocalDate date, Collection<Long> depotIds, Pageable pageable);
}