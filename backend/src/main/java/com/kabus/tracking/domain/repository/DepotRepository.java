package com.kabus.tracking.domain.repository;

import com.kabus.tracking.domain.entity.Depot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DepotRepository extends JpaRepository<Depot, Long> {

    List<Depot> findByDivisionId(Long divisionId);

    List<Depot> findByDivisionIdIn(Collection<Long> divisionIds);

    List<Depot> findByIdIn(Collection<Long> ids);

    Optional<Depot> findByDivisionIdAndCode(Long divisionId, String code);

    long countByDivisionIdIn(Collection<Long> divisionIds);
}