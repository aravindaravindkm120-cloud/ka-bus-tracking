package com.kabus.tracking.domain.repository;

import com.kabus.tracking.domain.entity.DepotHeadProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DepotHeadProfileRepository extends JpaRepository<DepotHeadProfile, Long> {
    Optional<DepotHeadProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    void deleteByUserId(Long userId);
}