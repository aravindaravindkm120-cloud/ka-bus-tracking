package com.kabus.tracking.domain.repository;

import com.kabus.tracking.domain.entity.Bus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BusRepository extends JpaRepository<Bus, Long> {

    @Query("select b from Bus b where upper(b.busNumber.busNumber) = upper(:registrationNo)")
    Optional<Bus> findByRegistrationNo(String registrationNo);

    Page<Bus> findByDepotIdIn(Collection<Long> depotIds, Pageable pageable);

    List<Bus> findByDepotIdIn(Collection<Long> depotIds);

    long countByDepotIdIn(Collection<Long> depotIds);

    Page<Bus> findByTownIdIn(Collection<Long> townIds, Pageable pageable);

    long countByTownIdIn(Collection<Long> townIds);

    @Query("select b from Bus b where b.depot.id in :depotIds and b.status = :status")
    List<Bus> findInDepotsByStatus(@Param("depotIds") Collection<Long> depotIds,
                                   @Param("status") String status);

    @Query("select count(b) from Bus b where b.depot.id in :depotIds and b.status = :status")
    long countInDepotsByStatus(@Param("depotIds") Collection<Long> depotIds,
                               @Param("status") String status);

    @Query("select b from Bus b where b.town.id in :townIds and b.status = :status")
    List<Bus> findInTownsByStatus(@Param("townIds") Collection<Long> townIds,
                                  @Param("status") String status);

    @Query("select count(b) from Bus b where b.town.id in :townIds and b.status = :status")
    long countInTownsByStatus(@Param("townIds") Collection<Long> townIds,
                              @Param("status") String status);

    @Query("select b from Bus b where b.depot.id in :depotIds and ("
            + "lower(b.busNumber.busNumber) like lower(concat('%', :term, '%')) "
            + "or lower(coalesce(b.busNumber.busType, '')) like lower(concat('%', :term, '%')) "
            + "or lower(coalesce(b.makeModel, '')) like lower(concat('%', :term, '%')))")
    Page<Bus> searchInDepots(@Param("depotIds") Collection<Long> depotIds,
                             @Param("term") String term, Pageable pageable);

    @Query("select b from Bus b where b.town.id in :townIds and ("
            + "lower(b.busNumber.busNumber) like lower(concat('%', :term, '%')) "
            + "or lower(coalesce(b.busNumber.busType, '')) like lower(concat('%', :term, '%')) "
            + "or lower(coalesce(b.makeModel, '')) like lower(concat('%', :term, '%')))")
    Page<Bus> searchInTowns(@Param("townIds") Collection<Long> townIds,
                            @Param("term") String term, Pageable pageable);

    @Query("select b from Bus b where "
            + "lower(b.busNumber.busNumber) like lower(concat('%', :term, '%')) "
            + "or lower(coalesce(b.busNumber.busType, '')) like lower(concat('%', :term, '%')) "
            + "or lower(coalesce(b.makeModel, '')) like lower(concat('%', :term, '%'))")
    Page<Bus> searchAll(@Param("term") String term, Pageable pageable);
}