package com.kabus.tracking.service;

import com.kabus.tracking.domain.entity.Crew;
import com.kabus.tracking.domain.entity.CrewAssignment;
import com.kabus.tracking.domain.entity.GpsSession;
import com.kabus.tracking.domain.enums.GpsSessionStatus;
import com.kabus.tracking.domain.repository.CrewAssignmentRepository;
import com.kabus.tracking.domain.repository.CrewRepository;
import com.kabus.tracking.domain.repository.GpsSessionRepository;
import com.kabus.tracking.exception.ApiException;
import com.kabus.tracking.web.dto.CrewGpsDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CrewService {

    private final CrewRepository crewRepository;
    private final CrewAssignmentRepository crewAssignmentRepository;
    private final GpsSessionRepository gpsSessionRepository;

    public CrewService(CrewRepository crewRepository,
                       CrewAssignmentRepository crewAssignmentRepository,
                       GpsSessionRepository gpsSessionRepository) {
        this.crewRepository = crewRepository;
        this.crewAssignmentRepository = crewAssignmentRepository;
        this.gpsSessionRepository = gpsSessionRepository;
    }

    /**
     * Returns the real server-side assignment for a logged-in crew member.
     * The client is never allowed to pick bus/trip/route - it only receives.
     */
    @Transactional(readOnly = true)
    public CrewGpsDtos.AssignmentResponse getAssignment(Long userId) {
        Crew crew = crewRepository.findByUserId(userId)
                .orElseThrow(() -> ApiException.forbidden("No crew profile linked to this account."));

        CrewAssignment assignment = crewAssignmentRepository
                .findFirstByCrewUserIdAndStatus(userId, "ACTIVE")
                .orElse(null);

        if (assignment == null) {
            return CrewGpsDtos.AssignmentResponse.none("NO_ACTIVE_ASSIGNMENT");
        }

        GpsSession activeSession = gpsSessionRepository
                .findFirstByCrewUserIdAndStatusOrderByStartedAtDesc(userId, GpsSessionStatus.ACTIVE)
                .orElse(null);

        var trip = assignment.getTrip();
        var bus = trip.getBus();
        var route = trip.getRoute();
        return new CrewGpsDtos.AssignmentResponse(
                crew.getId(),
                crew.getBadgeNo(),
                crew.getFullName(),
                crew.getCrewType(),
                trip.getId(),
                trip.getTripNumber(),
                trip.getStatus().name(),
                bus.getId(),
                bus.getRegistrationNo(),
                bus.getBusType(),
                route == null ? null : route.getId(),
                route == null ? null : route.getName(),
                route == null ? null : route.getCode(),
                route == null ? null : route.getOrigin(),
                route == null ? null : route.getDestination(),
                activeSession == null ? null : activeSession.getStatus(),
                activeSession == null ? null : activeSession.getId(),
                activeSession == null ? null : activeSession.getSessionKey());
    }

    @Transactional(readOnly = true)
    public CrewAssignment requireActiveAssignment(Long userId) {
        return crewAssignmentRepository.findFirstByCrewUserIdAndStatus(userId, "ACTIVE")
                .orElseThrow(() -> ApiException.badRequest(
                        "No active crew assignment. Ask the depot to assign you to a trip."));
    }
}