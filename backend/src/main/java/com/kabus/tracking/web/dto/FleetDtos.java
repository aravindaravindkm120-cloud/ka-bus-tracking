package com.kabus.tracking.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** DTOs for the bus number master and fleet (vehicle) management. */
public final class FleetDtos {

    /** Bus types the project already recognises. */
    public static final java.util.List<String> BUS_TYPES =
            java.util.List.of("ORDINARY", "EXPRESS", "RAJADHARSHA");

    private FleetDtos() {
    }

    // ------------------------------------------------------------------
    // Bus number master
    // ------------------------------------------------------------------

    public record CreateBusNumberRequest(
            @NotBlank(message = "Bus number is required.")
            @Size(max = 20, message = "Bus number must be at most 20 characters.")
            String busNumber,

            @NotBlank(message = "Bus type is required.")
            @Size(max = 40, message = "Bus type must be at most 40 characters.")
            String busType,

            @NotNull(message = "Depot is required.")
            Long depotId,

            @NotNull(message = "Town is required.")
            Long townId) {
    }

    public record UpdateBusNumberRequest(
            @NotBlank(message = "Bus type is required.")
            @Size(max = 40, message = "Bus type must be at most 40 characters.")
            String busType,

            Long townId) {
    }

    public record BusNumberResponse(
            Long id,
            String busNumber,
            String busType,
            boolean enabled,
            Long corporationId,
            Long divisionId,
            Long depotId,
            String depotName,
            Long townId,
            String townName,
            Long vehicleCount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    // ------------------------------------------------------------------
    // Fleet (vehicles)
    // ------------------------------------------------------------------

    public record CreateBusRequest(
            @NotNull(message = "Bus number is required. Pick one from the bus number master.")
            Long busNumberId,

            @NotNull(message = "Capacity is required.")
            @Min(value = 1, message = "Capacity must be at least 1.")
            @Max(value = 200, message = "Capacity is unrealistically large.")
            Integer capacity,

            @Size(max = 20, message = "Fuel type must be at most 20 characters.")
            String fuelType,

            @Size(max = 80, message = "Make/model must be at most 80 characters.")
            String makeModel,

            @Min(value = 1950, message = "Manufacture year is invalid.")
            @Max(value = 2100, message = "Manufacture year is invalid.")
            Integer manufactureYear,

            @Size(max = 60, message = "GPS device id must be at most 60 characters.")
            String gpsDeviceId,

            Boolean gpsEnabled,

            @Pattern(regexp = "ACTIVE|MAINTENANCE|INACTIVE|RETIRED",
                    message = "Status must be ACTIVE, MAINTENANCE, INACTIVE or RETIRED.")
            String status,

            @NotNull(message = "Depot is required.")
            Long depotId,

            @NotNull(message = "Town is required.")
            Long townId) {
    }

    public record UpdateBusRequest(
            @NotNull(message = "Capacity is required.")
            @Min(value = 1, message = "Capacity must be at least 1.")
            @Max(value = 200, message = "Capacity is unrealistically large.")
            Integer capacity,

            @Size(max = 20, message = "Fuel type must be at most 20 characters.")
            String fuelType,

            @Size(max = 80, message = "Make/model must be at most 80 characters.")
            String makeModel,

            @Min(value = 1950, message = "Manufacture year is invalid.")
            @Max(value = 2100, message = "Manufacture year is invalid.")
            Integer manufactureYear,

            @Size(max = 60, message = "GPS device id must be at most 60 characters.")
            String gpsDeviceId,

            Boolean gpsEnabled,

            @Pattern(regexp = "ACTIVE|MAINTENANCE|INACTIVE|RETIRED",
                    message = "Status must be ACTIVE, MAINTENANCE, INACTIVE or RETIRED.")
            String status,

            Long townId) {
    }

    /**
     * Registration number and bus type are still reported under their historical
     * JSON names so the passenger app, crew app and admin dashboards keep working
     * unchanged; their values now come from the bus number master.
     */
    public record BusResponse(
            Long id,
            String registrationNo,
            String busType,
            Long busNumberId,
            Integer capacity,
            String fuelType,
            String makeModel,
            Integer manufactureYear,
            String gpsDeviceId,
            boolean gpsEnabled,
            String status,
            boolean enabled,
            Long divisionId,
            String divisionName,
            Long depotId,
            String depotName,
            Long townId,
            String townName,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }
}