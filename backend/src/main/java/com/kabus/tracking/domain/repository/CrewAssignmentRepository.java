package com.kabus.tracking.domain.repository;

import com.kabus.tracking.domain.entity.CrewAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CrewAssignmentRepository extends JpaRepository<CrewAssignment, Long> {

    Optional<CrewAssignment> findFirstByCrewUserIdAndStatus(Long userId, String status);

    List<CrewAssignment> findByTripId(Long tripId);

    Optional<CrewAssignment> findFirstByCrewIdAndStatus(Long crewId, String status);

    List<CrewAssignment> findByCrewIdInAndStatus(List<Long> crewIds, String status);

    List<CrewAssignment> findByTripIdAndStatus(Long tripId, String status);

    Optional<CrewAssignment> findFirstByTripIdAndCrewTypeAndStatus(Long tripId, String crewType, String status);

    long countByStatus(String status);
}