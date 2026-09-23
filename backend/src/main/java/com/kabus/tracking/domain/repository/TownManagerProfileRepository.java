package com.kabus.tracking.domain.repository;

import com.kabus.tracking.domain.entity.TownManagerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TownManagerProfileRepository extends JpaRepository<TownManagerProfile, Long> {
    Optional<TownManagerProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    void deleteByUserId(Long userId);
}