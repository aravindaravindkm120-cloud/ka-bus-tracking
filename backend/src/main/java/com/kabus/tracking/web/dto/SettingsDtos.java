package com.kabus.tracking.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** DTOs for runtime system settings. */
public final class SettingsDtos {

    private SettingsDtos() {
    }

    public record SettingResponse(
            String key,
            String value,
            String type,
            String description,
            String defaultValue,
            Long updatedBy,
            String updatedByUsername,
            LocalDateTime updatedAt) {
    }

    public record UpdateSettingRequest(
            @NotNull(message = "Value is required.")
            @NotBlank(message = "Value must not be blank.")
            String value) {
    }
}
