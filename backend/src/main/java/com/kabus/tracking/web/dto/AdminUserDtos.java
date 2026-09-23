package com.kabus.tracking.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * DTOs for SUPER_ADMIN admin-user management.
 *
 * <p>Roles are stored strictly in the role profile tables. A managed account
 * always has exactly one admin role and the matching organizational scope:
 * DIVISION_ADMIN / DIVISION_MANAGER need a division, DEPOT_HEAD a depot and
 * TOWN_MANAGER a town. SUPER_ADMIN accounts are intentionally out of scope for
 * this API and are rejected with 400.</p>
 */
public final class AdminUserDtos {

    private AdminUserDtos() {
    }

    /** Roles this API may create/assign. SUPER_ADMIN is deliberately excluded. */
    public static final java.util.Set<String> MANAGED_ROLES = java.util.Set.of(
            "DIVISION_ADMIN", "DIVISION_MANAGER", "DEPOT_HEAD", "TOWN_MANAGER");

    public record CreateUserRequest(
            @NotBlank(message = "Username is required.")
            @Size(max = 60, message = "Username must be at most 60 characters.")
            @Pattern(regexp = "[A-Za-z0-9._-]{3,60}", message = "Username may contain letters, digits, dot, underscore or hyphen (3-60 chars).")
            String username,

            @NotBlank(message = "Full name is required.")
            @Size(max = 120, message = "Full name must be at most 120 characters.")
            String fullName,

            @Size(max = 120, message = "Email must be at most 120 characters.")
            @Email(message = "Email must be a valid email address.")
            String email,

            @Size(max = 20, message = "Phone must be at most 20 characters.")
            String phone,

            @NotBlank(message = "Password is required.")
            @Size(min = 8, max = 72, message = "Password must be 8-72 characters.")
            String password,

            @NotBlank(message = "Role is required.")
            String role,

            Long corporationId,
            Long divisionId,
            Long depotId,
            Long townId,

            Boolean enabled) {
    }

    public record UpdateUserRequest(
            @NotBlank(message = "Full name is required.")
            @Size(max = 120, message = "Full name must be at most 120 characters.")
            String fullName,

            @Size(max = 120, message = "Email must be at most 120 characters.")
            @Email(message = "Email must be a valid email address.")
            String email,

            @Size(max = 20, message = "Phone must be at most 20 characters.")
            String phone) {
    }

    public record ChangeRoleRequest(
            @NotBlank(message = "Role is required.")
            String role,

            Long corporationId,
            Long divisionId,
            Long depotId,
            Long townId) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "Password is required.")
            @Size(min = 8, max = 72, message = "Password must be 8-72 characters.")
            String password) {
    }

    public record EnabledRequest(
            @NotNull(message = "enabled is required.")
            Boolean enabled) {
    }

    public record UserResponse(
            Long id,
            String username,
            String fullName,
            String email,
            String phone,
            boolean enabled,
            boolean locked,
            boolean mustChangePassword,
            String role,
            Long corporationId,
            String corporationName,
            Long divisionId,
            String divisionName,
            Long depotId,
            String depotName,
            Long townId,
            String townName,
            LocalDateTime lastLoginAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }
}
