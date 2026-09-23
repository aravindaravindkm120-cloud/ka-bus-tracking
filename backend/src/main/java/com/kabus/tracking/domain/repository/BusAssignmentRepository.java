package com.kabus.tracking.domain.repository;

import com.kabus.tracking.domain.entity.BusAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BusAssignmentRepository extends JpaRepository<BusAssignment, Long> {
    List<BusAssignment> findByTripId(Long tripId);

    List<BusAssignment> findByTripIdAndStatus(Long tripId, String status);
}