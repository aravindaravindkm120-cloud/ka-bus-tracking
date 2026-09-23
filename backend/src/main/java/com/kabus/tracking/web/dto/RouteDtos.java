package com.kabus.tracking.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** DTOs for route + route-stop management and geometry preview. */
public final class RouteDtos {

    private RouteDtos() {
    }

    public record CreateRouteRequest(
            Long divisionId,

            @NotBlank(message = "Route code is required.")
            @Size(max = 20, message = "Route code must be at most 20 characters.")
            String code,

            @NotBlank(message = "Route name is required.")
            @Size(max = 160, message = "Route name must be at most 160 characters.")
            String name,

            @NotBlank(message = "Origin is required.")
            @Size(max = 120, message = "Origin must be at most 120 characters.")
            String origin,

            @NotBlank(message = "Destination is required.")
            @Size(max = 120, message = "Destination must be at most 120 characters.")
            String destination,

            @DecimalMin(value = "0.0", message = "Distance cannot be negative.")
            BigDecimal distanceKm,

            @Min(value = 1, message = "Estimated duration must be positive.")
            Integer estDurationMin,

            @Pattern(regexp = "ACTIVE|INACTIVE|SUSPENDED",
                    message = "Status must be ACTIVE, INACTIVE or SUSPENDED.")
            String status) {
    }

    public record UpdateRouteRequest(
            @NotBlank(message = "Route name is required.")
            @Size(max = 160, message = "Route name must be at most 160 characters.")
            String name,

            @NotBlank(message = "Origin is required.")
            @Size(max = 120, message = "Origin must be at most 120 characters.")
            String origin,

            @NotBlank(message = "Destination is required.")
            @Size(max = 120, message = "Destination must be at most 120 characters.")
            String destination,

            @DecimalMin(value = "0.0", message = "Distance cannot be negative.")
            BigDecimal distanceKm,

            @Min(value = 1, message = "Estimated duration must be positive.")
            Integer estDurationMin,

            @Pattern(regexp = "ACTIVE|INACTIVE|SUSPENDED",
                    message = "Status must be ACTIVE, INACTIVE or SUSPENDED.")
            String status) {
    }

    public record StopInput(
            @NotNull(message = "Stop order is required.")
            @Min(value = 1, message = "Stop order starts at 1.")
            Integer stopOrder,

            @NotBlank(message = "Stop name is required.")
            @Size(max = 160, message = "Stop name must be at most 160 characters.")
            String stopName,

            @NotNull(message = "Latitude is required.")
            @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90.")
            @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90.")
            BigDecimal latitude,

            @NotNull(message = "Longitude is required.")
            @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180.")
            @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180.")
            BigDecimal longitude,

            @DecimalMin(value = "0.0", message = "Distance from start cannot be negative.")
            BigDecimal distanceFromStart) {
    }

    public record ReplaceStopsRequest(
            @NotEmpty(message = "At least one stop is required.")
            List<@Valid StopInput> stops) {
    }

    public record RouteStopResponse(
            Long id,
            Integer stopOrder,
            String stopName,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal distanceFromStart) {
    }

    public record RouteResponse(
            Long id,
            String code,
            String name,
            String origin,
            String destination,
            BigDecimal distanceKm,
            Integer estDurationMin,
            String status,
            boolean enabled,
            Long divisionId,
            String divisionName,
            String corporationName,
            int stopCount,
            List<RouteStopResponse> stops,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record PreviewRequest(
            @NotEmpty(message = "At least one stop is required.")
            List<@Valid StopInput> stops) {
    }

    public record PreviewResponse(
            boolean available,
            String reason,
            PassengerDtos.GeometryLineStringDto geometry,
            Double distanceKm,
            Double durationSec) {
    }
}
