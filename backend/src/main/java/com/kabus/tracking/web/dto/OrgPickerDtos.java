package com.kabus.tracking.web.dto;

/**
 * DTOs for the read-only organization picker used by every admin "Add" form.
 *
 * <p>These deliberately mirror the shape of {@link OrgDtos} responses but carry
 * only {@code id}, {@code code}, {@code name} and the immediate {@code parentId}.
 * The picker exists because the management endpoints in
 * {@code AdminOrganizationController} are reserved for SUPER_ADMIN reads (and
 * SUPER_ADMIN-only writes), whereas every scoped admin role needs to resolve the
 * names behind the organization levels it is allowed to use when filling in a
 * staff / bus-number / fleet / route form.</p>
 *
 * <p>Raw ids are never shown to the user: the client selects a row from these
 * lists and sends the selected id back as the parent of the next level.</p>
 */
public final class OrgPickerDtos {

    private OrgPickerDtos() {
    }

    public record CorporationOption(
            Long id,
            String code,
            String name) {
    }

    public record DivisionOption(
            Long id,
            Long corporationId,
            String code,
            String name) {
    }

    public record DepotOption(
            Long id,
            Long divisionId,
            String code,
            String name) {
    }

    public record TownOption(
            Long id,
            Long depotId,
            String code,
            String name) {
    }
}