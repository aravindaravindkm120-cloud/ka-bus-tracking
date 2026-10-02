package com.kabus.tracking.domain.repository;

import com.kabus.tracking.domain.entity.BusNumber;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BusNumberRepository extends JpaRepository<BusNumber, Long> {

    Optional<BusNumber> findByDepotIdAndBusNumber(Long depotId, String busNumber);

    boolean existsByDepotIdAndBusNumber(Long depotId, String busNumber);

    boolean existsByDepotIdAndBusNumberAndIdNot(Long depotId, String busNumber, Long id);

    long countByDepotIdAndEnabledTrue(Long depotId);

    List<BusNumber> findByDepotIdInAndEnabledTrueOrderByBusNumberAsc(Collection<Long> depotIds);

    List<BusNumber> findByDepotIdIn(Collection<Long> depotIds);

    Page<BusNumber> findByDepotIdInOrderByBusNumberAsc(Collection<Long> depotIds, Pageable pageable);

    Page<BusNumber> findByTownIdInAndEnabledTrueOrderByBusNumberAsc(Collection<Long> townIds, Pageable pageable);

    Page<BusNumber> findAllByOrderByBusNumberAsc(Pageable pageable);

    @Query("select bn from BusNumber bn where bn.depot.id in :depotIds and ("
            + "lower(bn.busNumber) like lower(concat('%', :term, '%')) "
            + "or lower(coalesce(bn.busType, '')) like lower(concat('%', :term, '%'))) "
            + "order by bn.busNumber asc")
    Page<BusNumber> searchInDepots(@Param("depotIds") Collection<Long> depotIds,
                                   @Param("term") String term, Pageable pageable);

    @Query("select bn from BusNumber bn where bn.town.id in :townIds and bn.enabled = true and ("
            + "lower(bn.busNumber) like lower(concat('%', :term, '%')) "
            + "or lower(coalesce(bn.busType, '')) like lower(concat('%', :term, '%'))) "
            + "order by bn.busNumber asc")
    Page<BusNumber> searchInTowns(@Param("townIds") Collection<Long> townIds,
                                  @Param("term") String term, Pageable pageable);

    @Query("select bn from BusNumber bn where ("
            + "lower(bn.busNumber) like lower(concat('%', :term, '%')) "
            + "or lower(coalesce(bn.busType, '')) like lower(concat('%', :term, '%'))) "
            + "order by bn.busNumber asc")
    Page<BusNumber> searchAll(@Param("term") String term, Pageable pageable);
}
