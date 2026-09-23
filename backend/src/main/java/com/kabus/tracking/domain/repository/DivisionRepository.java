package com.kabus.tracking.domain.repository;

import com.kabus.tracking.domain.entity.Division;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Optional;
import java.util.Optional;

public interface DivisionRepository extends JpaRepository<Division, Long> {

    List<Division> findByCorporationId(Long corporationId);

    List<Division> findByIdIn(Collection<Long> ids);

    Optional<Division> findByCorporationIdAndCode(Long corporationId, String code);

    long countByIdIn(Collection<Long> ids);
}