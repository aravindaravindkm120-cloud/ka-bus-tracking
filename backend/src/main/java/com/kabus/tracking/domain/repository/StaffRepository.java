package com.kabus.tracking.domain.repository;

import com.kabus.tracking.domain.entity.Staff;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface StaffRepository extends JpaRepository<Staff, Long> {

    long countByDepotIdIn(Collection<Long> depotIds);

    Page<Staff> findByDepotIdIn(Collection<Long> depotIds, Pageable pageable);

    long countByTownIdIn(Collection<Long> townIds);

    Page<Staff> findByTownIdIn(Collection<Long> townIds, Pageable pageable);

    Optional<Staff> findByUserId(Long userId);

    boolean existsByEmpCodeIgnoreCase(String empCode);

    boolean existsByEmpCodeIgnoreCaseAndIdNot(String empCode, Long id);

    @Query("select s from Staff s where s.depot.id in :depotIds and ("
            + "lower(s.fullName) like lower(concat('%', :term, '%')) "
            + "or lower(coalesce(s.empCode, '')) like lower(concat('%', :term, '%')) "
            + "or lower(coalesce(s.phone, '')) like lower(concat('%', :term, '%')) "
            + "or lower(coalesce(s.designation, '')) like lower(concat('%', :term, '%')))")
    Page<Staff> searchInDepots(@Param("depotIds") Collection<Long> depotIds,
                               @Param("term") String term, Pageable pageable);

    @Query("select s from Staff s where s.town.id in :townIds and ("
            + "lower(s.fullName) like lower(concat('%', :term, '%')) "
            + "or lower(coalesce(s.empCode, '')) like lower(concat('%', :term, '%')) "
            + "or lower(coalesce(s.phone, '')) like lower(concat('%', :term, '%')) "
            + "or lower(coalesce(s.designation, '')) like lower(concat('%', :term, '%')))")
    Page<Staff> searchInTowns(@Param("townIds") Collection<Long> townIds,
                              @Param("term") String term, Pageable pageable);

    @Query("select s from Staff s where "
            + "lower(s.fullName) like lower(concat('%', :term, '%')) "
            + "or lower(coalesce(s.empCode, '')) like lower(concat('%', :term, '%')) "
            + "or lower(coalesce(s.phone, '')) like lower(concat('%', :term, '%')) "
            + "or lower(coalesce(s.designation, '')) like lower(concat('%', :term, '%'))")
    Page<Staff> searchAll(@Param("term") String term, Pageable pageable);
}
