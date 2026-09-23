package com.kabus.tracking.web.dto;

import com.kabus.tracking.domain.enums.TripStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** DTOs for trip scheduling and bus/crew assignment. */
public final class TripDtos {

    private TripDtos() {
    }

    public record CreateTripRequest(
            @NotNull(message = "Route is required.")
            Long routeId,

            @NotNull(message = "Bus is required.")
            Long busId,

            @Size(max = 30, message = "Trip number must be at most 30 characters.")
            String tripNumber,

            @NotNull(message = "Trip date is required.")
            LocalDate tripDate,

            @NotNull(message = "Scheduled departure is required.")
            LocalDateTime scheduledDeparture,

            @NotNull(message = "Scheduled arrival is required.")
            LocalDateTime scheduledArrival,

            @Pattern(regexp = "OUTBOUND|INBOUND", message = "Direction must be OUTBOUND or INBOUND.")
            String direction,

            TripStatus status) {
    }

    public record UpdateTripRequest(
            @NotNull(message = "Bus is required.")
            Long busId,

            @NotNull(message = "Trip date is required.")
            LocalDate tripDate,

            @NotNull(message = "Scheduled departure is required.")
            LocalDateTime scheduledDeparture,

            @NotNull(message = "Scheduled arrival is required.")
            LocalDateTime scheduledArrival,

            @Pattern(regexp = "OUTBOUND|INBOUND", message = "Direction must be OUTBOUND or INBOUND.")
            String direction) {
    }

    public record AssignBusRequest(
            @NotNull(message = "Bus is required.")
            Long busId) {
    }

    public record AssignCrewRequest(
            @NotNull(message = "Crew is required.")
            Long crewId) {
    }

    public record CrewAssignmentResponse(
            Long id,
            Long crewId,
            String badgeNo,
            String fullName,
            String crewType,
            String status,
            LocalDateTime assignedFrom,
            LocalDateTime assignedTo) {
    }

    public record BusAssignmentResponse(
            Long id,
            Long busId,
            String registrationNo,
            String status,
            LocalDateTime assignedFrom,
            LocalDateTime assignedTo) {
    }

    public record TripResponse(
            Long id,
            String tripNumber,
            LocalDate tripDate,
            LocalDateTime scheduledDeparture,
            LocalDateTime scheduledArrival,
            String status,
            String direction,
            Long routeId,
            String routeCode,
            String routeName,
            Long busId,
            String busRegistrationNo,
            Long depotId,
            String depotName,
            Long divisionId,
            String divisionName,
            List<CrewAssignmentResponse> crewAssignments,
            List<BusAssignmentResponse> busAssignments,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }
}
