package com.kabus.tracking.domain.repository;

import com.kabus.tracking.domain.entity.CorporationAdminProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CorporationAdminProfileRepository extends JpaRepository<CorporationAdminProfile, Long> {
    Optional<CorporationAdminProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    void deleteByUserId(Long userId);
}