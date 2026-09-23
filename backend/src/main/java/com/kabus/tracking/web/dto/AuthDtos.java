package com.kabus.tracking.web.dto;

import com.kabus.tracking.domain.enums.RoleCode;

import java.util.Set;

/** Request/response DTOs for authentication. All types are immutable records. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @jakarta.validation.constraints.NotBlank String username,
            @jakarta.validation.constraints.NotBlank String password,
            String deviceId) {
    }

    /**
     * Role-specific admin login. {@code requestedRole} is one of
     * SUPER_ADMIN / DIVISION_ADMIN / DIVISION_MANAGER / DEPOT_HEAD /
     * TOWN_MANAGER; the backend only grants it when a matching role profile
     * row exists for the email.
     */
    public record AdminLoginRequest(
            @jakarta.validation.constraints.Email
            @jakarta.validation.constraints.NotBlank String email,
            @jakarta.validation.constraints.NotBlank String password,
            @jakarta.validation.constraints.NotBlank String requestedRole) {
    }

    public record RefreshRequest(@jakarta.validation.constraints.NotBlank String refreshToken) {
    }

    public record RecoveryDisableRequest(@jakarta.validation.constraints.NotNull Boolean enable) {
    }

    public record AuthUser(
            Long id,
            String username,
            String fullName,
            String email,
            String phone,
            Set<RoleCode> roles,
            Long divisionId,
            Long depotId,
            Long townId) {
    }

    public record LoginResponse(
            AuthUser user,
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresInSeconds) {
    }

    public record RefreshResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresInSeconds) {
    }
}