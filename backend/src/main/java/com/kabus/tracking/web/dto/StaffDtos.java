package com.kabus.tracking.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** DTOs for staff management. */
public final class StaffDtos {

    private StaffDtos() {
    }

    public record CreateStaffRequest(
            @NotBlank(message = "Full name is required.")
            @Size(max = 120, message = "Full name must be at most 120 characters.")
            String fullName,

            @Size(max = 20, message = "Phone must be at most 20 characters.")
            String phone,

            @Size(max = 30, message = "Employee code must be at most 30 characters.")
            String empCode,

            @Size(max = 80, message = "Designation must be at most 80 characters.")
            String designation,

            @Pattern(regexp = "ACTIVE|INACTIVE|SUSPENDED|RETIRED",
                    message = "Status must be ACTIVE, INACTIVE, SUSPENDED or RETIRED.")
            String status,

            @NotNull(message = "Depot is required.")
            Long depotId,

            Long townId,

            Long userId) {
    }

    public record UpdateStaffRequest(
            @NotBlank(message = "Full name is required.")
            @Size(max = 120, message = "Full name must be at most 120 characters.")
            String fullName,

            @Size(max = 20, message = "Phone must be at most 20 characters.")
            String phone,

            @Size(max = 30, message = "Employee code must be at most 30 characters.")
            String empCode,

            @Size(max = 80, message = "Designation must be at most 80 characters.")
            String designation,

            @Pattern(regexp = "ACTIVE|INACTIVE|SUSPENDED|RETIRED",
                    message = "Status must be ACTIVE, INACTIVE, SUSPENDED or RETIRED.")
            String status,

            Long townId) {
    }

    public record StaffResponse(
            Long id,
            String fullName,
            String phone,
            String empCode,
            String designation,
            String status,
            Long userId,
            String username,
            Long corporationId,
            String corporationName,
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
