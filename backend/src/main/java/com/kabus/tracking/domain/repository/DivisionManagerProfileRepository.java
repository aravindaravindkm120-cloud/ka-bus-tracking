package com.kabus.tracking.domain.repository;

import com.kabus.tracking.domain.entity.DivisionManagerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DivisionManagerProfileRepository extends JpaRepository<DivisionManagerProfile, Long> {
    Optional<DivisionManagerProfile> findByUserId(Long userId);

    Optional<DivisionManagerProfile> findByDivisionId(Long divisionId);

    boolean existsByUserId(Long userId);

    void deleteByUserId(Long userId);
}