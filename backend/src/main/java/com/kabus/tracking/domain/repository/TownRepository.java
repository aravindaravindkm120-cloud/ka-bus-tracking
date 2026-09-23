package com.kabus.tracking.domain.repository;

import com.kabus.tracking.domain.entity.Town;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Optional;
import java.util.Optional;

public interface TownRepository extends JpaRepository<Town, Long> {

    List<Town> findByDepotId(Long depotId);

    List<Town> findByDepotIdIn(Collection<Long> depotIds);

    List<Town> findByIdIn(Collection<Long> ids);

    Optional<Town> findByDepotIdAndCode(Long depotId, String code);

    long countByDepotIdIn(Collection<Long> depotIds);
}