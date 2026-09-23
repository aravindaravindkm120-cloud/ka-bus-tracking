package com.kabus.tracking.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * DTOs for organization hierarchy management (Corporation -&gt; Division -&gt; Depot -&gt; Town).
 * Code uniqueness follows the schema's UQ keys: corporation code is globally unique;
 * division/depot/town codes are unique within their parent.
 */
public final class OrgDtos {

    private OrgDtos() {
    }

    // ------------------------------------------------------------------
    // Audit action label markers (passed to audit(); descriptive only).
    // ------------------------------------------------------------------
    public static final String CORP_CREATE = "CORPORATION:CREATE";
    public static final String CORP_UPDATE = "CORPORATION:UPDATE";
    public static final String CORP_TOGGLE = "CORPORATION:TOGGLE";
    public static final String DIV_CREATE = "DIVISION:CREATE";
    public static final String DIV_UPDATE = "DIVISION:UPDATE";
    public static final String DIV_TOGGLE = "DIVISION:TOGGLE";
    public static final String DEPOT_CREATE = "DEPOT:CREATE";
    public static final String DEPOT_UPDATE = "DEPOT:UPDATE";
    public static final String DEPOT_TOGGLE = "DEPOT:TOGGLE";
    public static final String TOWN_CREATE = "TOWN:CREATE";
    public static final String TOWN_UPDATE = "TOWN:UPDATE";
    public static final String TOWN_TOGGLE = "TOWN:TOGGLE";

    // ------------------------------------------------------------------
    // Corporation
    // ------------------------------------------------------------------

    public record CorporationRequest(
            @NotBlank(message = "Code is required.")
            @Size(max = 20, message = "Code must be at most 20 characters.")
            @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{0,19}", message = "Code must start with a letter or digit (A-Z, a-z, 0-9, _ or -).")
            String code,

            @NotBlank(message = "Name is required.")
            @Size(max = 160, message = "Name must be at most 160 characters.")
            String name,

            @Size(max = 255, message = "Address must be at most 255 characters.")
            String address,

            @Size(max = 80, message = "City must be at most 80 characters.")
            String city,

            @Size(max = 40, message = "State must be at most 40 characters.")
            String state,

            @Size(max = 120, message = "Contact email must be at most 120 characters.")
            @Email(message = "Contact email must be a valid email address.")
            String contactEmail,

            @Size(max = 20, message = "Contact phone must be at most 20 characters.")
            String contactPhone) {
    }

    public record CorporationResponse(
            Long id,
            String code,
            String name,
            String address,
            String city,
            String state,
            String contactEmail,
            String contactPhone,
            boolean enabled,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    // ------------------------------------------------------------------
    // Division
    // ------------------------------------------------------------------

    public record DivisionRequest(
            @NotNull(message = "Corporation id is required.")
            Long corporationId,

            @NotBlank(message = "Code is required.")
            @Size(max = 20, message = "Code must be at most 20 characters.")
            @Pattern(regexp = "[A-Z0-9][A-Z0-9_-]{0,19}", message = "Code must start with a letter or digit (A-Z, 0-9, _ or -).")
            String code,

            @NotBlank(message = "Name is required.")
            @Size(max = 160, message = "Name must be at most 160 characters.")
            String name,

            @Size(max = 160, message = "Head office must be at most 160 characters.")
            String headOffice) {
    }

    public record DivisionResponse(
            Long id,
            Long corporationId,
            String corporationCode,
            String corporationName,
            String code,
            String name,
            String headOffice,
            boolean enabled,
            Long adminId,
            String adminName,
            String adminEmail,
            String adminStatus,
            String adminRole,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    // ------------------------------------------------------------------
    // Depot
    // ------------------------------------------------------------------

    public record DepotRequest(
            @NotNull(message = "Division id is required.")
            Long divisionId,

            @NotBlank(message = "Code is required.")
            @Size(max = 20, message = "Code must be at most 20 characters.")
            @Pattern(regexp = "[A-Z0-9][A-Z0-9_-]{0,19}", message = "Code must start with a letter or digit (A-Z, 0-9, _ or -).")
            String code,

            @NotBlank(message = "Name is required.")
            @Size(max = 160, message = "Name must be at most 160 characters.")
            String name,

            @Size(max = 255, message = "Address must be at most 255 characters.")
            String address,

            @Size(max = 20, message = "Phone must be at most 20 characters.")
            String phone) {
    }

    public record DepotResponse(
            Long id,
            Long divisionId,
            String divisionCode,
            String divisionName,
            String code,
            String name,
            String address,
            String phone,
            boolean enabled,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    // ------------------------------------------------------------------
    // Town
    // ------------------------------------------------------------------

    public record TownRequest(
            @NotNull(message = "Depot id is required.")
            Long depotId,

            @NotBlank(message = "Code is required.")
            @Size(max = 20, message = "Code must be at most 20 characters.")
            @Pattern(regexp = "[A-Z0-9][A-Z0-9_-]{0,19}", message = "Code must start with a letter or digit (A-Z, 0-9, _ or -).")
            String code,

            @NotBlank(message = "Name is required.")
            @Size(max = 160, message = "Name must be at most 160 characters.")
            String name) {
    }

    public record TownResponse(
            Long id,
            Long depotId,
            String depotCode,
            String depotName,
            String code,
            String name,
            boolean enabled,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }
}
