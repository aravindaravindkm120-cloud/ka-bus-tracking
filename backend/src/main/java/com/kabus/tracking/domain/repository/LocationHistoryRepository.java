package com.kabus.tracking.domain.repository;

import com.kabus.tracking.domain.entity.LocationHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface LocationHistoryRepository extends JpaRepository<LocationHistory, Long> {

    Page<LocationHistory> findByBusIdOrderByCapturedAtDesc(Long busId, Pageable pageable);

    @Modifying
    @Query("delete from LocationHistory h where h.capturedAt < :cutoff")
    int purgeOlderThan(@Param("cutoff") LocalDateTime cutoff);
}