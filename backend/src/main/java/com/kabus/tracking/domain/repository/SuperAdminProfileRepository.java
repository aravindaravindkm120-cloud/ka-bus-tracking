package com.kabus.tracking.domain.repository;

import com.kabus.tracking.domain.entity.SuperAdminProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SuperAdminProfileRepository extends JpaRepository<SuperAdminProfile, Long> {
    Optional<SuperAdminProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    void deleteByUserId(Long userId);
}