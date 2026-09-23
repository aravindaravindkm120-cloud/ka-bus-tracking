package com.kabus.tracking.web.dto;

import com.kabus.tracking.domain.enums.GpsSessionStatus;
import com.kabus.tracking.domain.enums.TripStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** DTOs for crew assignment and GPS session flows. */
public final class CrewGpsDtos {

    private CrewGpsDtos() {
    }

    public record CrewAssignmentRequest() {
    }

    public record AssignmentResponse(
            Long crewId,
            String badgeNo,
            String crewName,
            String crewType,
            Long tripId,
            String tripNumber,
            String tripStatus,
            Long busId,
            String busRegistration,
            String busType,
            Long routeId,
            String routeName,
            String routeCode,
            String origin,
            String destination,
            GpsSessionStatus sessionStatus,
            Long sessionId,
            String sessionKey) {

        public static AssignmentResponse none(String reason) {
            return new AssignmentResponse(null, null, null, null,
                    null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null);
        }
    }

    public record GpsStartRequest(String deviceId, String appVersion) {
    }

    public record GpsStartResponse(
            String sessionKey,
            Long sessionId,
            Long busId,
            Long tripId,
            Long routeId,
            GpsSessionStatus status,
            LocalDateTime startedAt) {
    }

    public record GpsLocationRequest(
            @jakarta.validation.constraints.NotNull BigDecimal latitude,
            @jakarta.validation.constraints.NotNull BigDecimal longitude,
            @jakarta.validation.constraints.NotNull BigDecimal speed,
            @jakarta.validation.constraints.NotNull BigDecimal heading,
            @jakarta.validation.constraints.NotNull BigDecimal accuracy,
            BigDecimal altitude,
            @jakarta.validation.constraints.NotNull LocalDateTime timestamp,
            Long clientNowEpochMillis) {
    }

    public record GpsLocationResponse(
            boolean accepted,
            String sessionKey,
            Long busId,
            Long tripId,
            String busStatus,
            int intervalMs,
            String message) {
    }

    public record GpsEndResponse(
            String sessionKey,
            Long busId,
            Long tripId,
            GpsSessionStatus status,
            TripStatus tripStatus,
            LocalDateTime endedAt) {
    }

    public record CrewStatusResponse(
            AssignmentResponse assignment,
            GpsSessionStatus gpsStatus,
            int lastUpdateAgeSeconds,
            Long lastUpdateAtEpochMs) {
    }
}