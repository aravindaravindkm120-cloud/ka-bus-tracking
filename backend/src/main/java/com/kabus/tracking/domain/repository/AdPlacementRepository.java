package com.kabus.tracking.domain.repository;

import com.kabus.tracking.domain.entity.AdPlacement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdPlacementRepository extends JpaRepository<AdPlacement, Long> {
    Optional<AdPlacement> findByCode(String code);
}