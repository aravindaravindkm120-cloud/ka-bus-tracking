package com.kabus.tracking.web.dto;

import java.util.List;

/** DTOs for the admin app. */
public final class AdminDtos {

    private AdminDtos() {
    }

    public record ScopeInfo(
            boolean wholeSystem,
            Long corporationId,
            Long divisionId,
            Long depotId,
            Long townId,
            List<Long> divisionIds,
            List<Long> depotIds,
            List<Long> townIds) {
    }

    public record DashboardResponse(
            String role,
            ScopeInfo scope,
            int corporations,
            long divisions,
            long depots,
            long towns,
            long buses,
            long busesActive,
            long busesLive,
            long busesStale,
            long busesOffline,
            long crew,
            long staff,
            long routes,
            long activeTrips,
            long activeGpsSessions) {
    }

    public record LiveBusItem(
            Long busId,
            String registrationNo,
            String busType,
            Long tripId,
            Long routeId,
            String routeName,
            java.math.BigDecimal latitude,
            java.math.BigDecimal longitude,
            java.math.BigDecimal speedKmh,
            java.math.BigDecimal heading,
            String status,
            java.time.LocalDateTime capturedAt) {
    }

    public record CrewItem(
            Long crewId,
            String badgeNo,
            String fullName,
            String crewType,
            String status,
            String dutyStatus) {
    }
}