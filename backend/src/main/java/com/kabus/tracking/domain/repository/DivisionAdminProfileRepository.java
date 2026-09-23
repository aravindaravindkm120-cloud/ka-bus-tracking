package com.kabus.tracking.domain.repository;

import com.kabus.tracking.domain.entity.DivisionAdminProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DivisionAdminProfileRepository extends JpaRepository<DivisionAdminProfile, Long> {
    Optional<DivisionAdminProfile> findByUserId(Long userId);

    Optional<DivisionAdminProfile> findByDivisionId(Long divisionId);

    boolean existsByUserId(Long userId);

    void deleteByUserId(Long userId);
}