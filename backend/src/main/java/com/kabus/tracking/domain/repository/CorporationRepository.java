package com.kabus.tracking.domain.repository;

import com.kabus.tracking.domain.entity.Corporation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CorporationRepository extends JpaRepository<Corporation, Long> {
    Optional<Corporation> findByCode(String code);
}