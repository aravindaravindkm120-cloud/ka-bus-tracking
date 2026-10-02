package com.kabus.tracking.service;

import com.kabus.tracking.domain.entity.Corporation;
import com.kabus.tracking.domain.entity.Depot;
import com.kabus.tracking.domain.entity.Division;
import com.kabus.tracking.domain.entity.Town;
import com.kabus.tracking.domain.repository.CorporationRepository;
import com.kabus.tracking.domain.repository.DepotRepository;
import com.kabus.tracking.domain.repository.DivisionRepository;
import com.kabus.tracking.domain.repository.TownRepository;
import com.kabus.tracking.exception.ApiException;
import com.kabus.tracking.security.UserPrincipal;
import com.kabus.tracking.web.dto.OrgPickerDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Read-only organization lookups for the cascading pickers on the admin "Add"
 * forms (staff, bus number, fleet, route, admin user).
 *
 * <p>This is deliberately a separate surface from
 * {@link OrgManagementService} / {@code AdminOrganizationController}: those
 * endpoints are the organization <em>management</em> screen and stay
 * SUPER_ADMIN-only for writes. Pickers must not widen that, but every scoped
 * admin role does need to resolve the names behind the levels it may assign.</p>
 *
 * <p>Every list is intersected with the caller's server-resolved
 * {@link AccessScope} through {@link ScopeResolver}. A division-scoped admin
 * sees only its own division, a depot head only its own depot, a town manager
 * only its own town, SUPER_ADMIN everything. The optional parent argument
 * narrows within the scope and can never widen it; an out-of-scope parent is a
 * 403 rather than an empty list, so a tampered request fails loudly instead of
 * looking like "there is nothing to choose from".</p>
 */
@Service
public class OrgPickerService {

    private final CorporationRepository corporationRepository;
    private final DivisionRepository divisionRepository;
    private final DepotRepository depotRepository;
    private final TownRepository townRepository;
    private final ScopeResolver scopeResolver;

    public OrgPickerService(CorporationRepository corporationRepository,
                            DivisionRepository divisionRepository,
                            DepotRepository depotRepository,
                            TownRepository townRepository,
                            ScopeResolver scopeResolver) {
        this.corporationRepository = corporationRepository;
        this.divisionRepository = divisionRepository;
        this.depotRepository = depotRepository;
        this.townRepository = townRepository;
        this.scopeResolver = scopeResolver;
    }

    // ------------------------------------------------------------------
    // Corporation
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<OrgPickerDtos.CorporationOption> corporations(UserPrincipal principal) {
        AccessScope scope = requireScope(principal);
        Set<Long> visible = visibleCorporationIds(scope);
        return corporationRepository.findAll().stream()
                .filter(c -> visible.contains(c.getId()))
                .sorted(Comparator.comparing(Corporation::getCode))
                .map(c -> new OrgPickerDtos.CorporationOption(c.getId(), c.getCode(), c.getName()))
                .toList();
    }

    // ------------------------------------------------------------------
    // Division
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<OrgPickerDtos.DivisionOption> divisions(UserPrincipal principal, Long corporationId) {
        AccessScope scope = requireScope(principal);
        Set<Long> allowed = new LinkedHashSet<>(scopeResolver.divisionIds(scope));

        if (corporationId != null) {
            requireVisibleCorporation(scope, corporationId);
            // Under a non-system scope there is exactly one visible corporation,
            // so it is the only parent that can own any of our divisions.
            List<Division> rows = scope.wholeSystem()
                    ? divisionRepository.findByCorporationId(corporationId)
                    : divisionRepository.findByCorporationId(requireVisibleCorporationId(scope));
            return divisionsByCode(rows).stream()
                    .filter(d -> allowed.contains(d.getId()))
                    .map(OrgPickerService::divisionOption)
                    .toList();
        }

        if (allowed.isEmpty()) {
            return List.of();
        }
        Set<Long> visible = visibleCorporationIds(scope);
        return divisionsByCode(divisionRepository.findByIdIn(allowed)).stream()
                .filter(d -> d.getCorporation() != null && visible.contains(d.getCorporation().getId()))
                .map(OrgPickerService::divisionOption)
                .toList();
    }

    // ------------------------------------------------------------------
    // Depot
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<OrgPickerDtos.DepotOption> depots(UserPrincipal principal, Long divisionId) {
        AccessScope scope = requireScope(principal);
        Set<Long> allowed = new LinkedHashSet<>(scopeResolver.depotIds(scope));

        if (divisionId != null && !scopeResolver.divisionIds(scope).contains(divisionId)) {
            throw ApiException.forbidden("Division " + divisionId + " is outside your scope.");
        }
        if (allowed.isEmpty()) {
            return List.of();
        }
        Set<Long> visible = visibleCorporationIds(scope);
        return depotsByCode(depotRepository.findByIdIn(allowed)).stream()
                .filter(d -> divisionId == null || divisionId.equals(d.getDivision().getId()))
                .filter(d -> inVisibleCorporation(d.getDivision(), visible))
                .map(OrgPickerService::depotOption)
                .toList();
    }

    // ------------------------------------------------------------------
    // Town
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<OrgPickerDtos.TownOption> towns(UserPrincipal principal, Long depotId) {
        AccessScope scope = requireScope(principal);
        Set<Long> allowed = new LinkedHashSet<>(scopeResolver.townIds(scope));

        if (depotId != null && !scopeResolver.depotIds(scope).contains(depotId)) {
            throw ApiException.forbidden("Depot " + depotId + " is outside your scope.");
        }
        if (allowed.isEmpty()) {
            return List.of();
        }
        Set<Long> visible = visibleCorporationIds(scope);
        return townsByCode(townRepository.findByIdIn(allowed)).stream()
                .filter(t -> depotId == null || depotId.equals(t.getDepot().getId()))
                .filter(t -> inVisibleCorporation(t.getDepot() == null ? null : t.getDepot().getDivision(), visible))
                .map(OrgPickerService::townOption)
                .toList();
    }

    // ------------------------------------------------------------------
    // Mapping
    // ------------------------------------------------------------------

    private static OrgPickerDtos.DivisionOption divisionOption(Division d) {
        return new OrgPickerDtos.DivisionOption(d.getId(), d.getCorporation().getId(), d.getCode(), d.getName());
    }

    private static OrgPickerDtos.DepotOption depotOption(Depot d) {
        return new OrgPickerDtos.DepotOption(d.getId(), d.getDivision().getId(), d.getCode(), d.getName());
    }

    private static OrgPickerDtos.TownOption townOption(Town t) {
        return new OrgPickerDtos.TownOption(t.getId(), t.getDepot().getId(), t.getCode(), t.getName());
    }

    private static List<Division> divisionsByCode(List<Division> rows) {
        return rows.stream().sorted(Comparator.comparing(Division::getCode)).toList();
    }

    private static List<Depot> depotsByCode(List<Depot> rows) {
        return rows.stream().sorted(Comparator.comparing(Depot::getCode)).toList();
    }

    private static List<Town> townsByCode(List<Town> rows) {
        return rows.stream().sorted(Comparator.comparing(Town::getCode)).toList();
    }

    // ------------------------------------------------------------------
    // Scope helpers
    // ------------------------------------------------------------------

    private AccessScope requireScope(UserPrincipal principal) {
        if (principal == null) {
            throw ApiException.forbidden("Account has no administrative scope.");
        }
        return scopeResolver.resolve(principal);
    }

    /** True when the division's corporation is one the caller may see. */
    private static boolean inVisibleCorporation(Division division, Set<Long> visibleCorporationIds) {
        return division != null
                && division.getCorporation() != null
                && visibleCorporationIds.contains(division.getCorporation().getId());
    }

    /**
     * Corporation ids the caller may see. A corporation-scoped admin sees its own
     * corporation; narrower roles are lifted to the corporation owning their
     * division. Touches the database, so it is resolved per call.
     */
    private Set<Long> visibleCorporationIds(AccessScope scope) {
        if (scope.wholeSystem()) {
            Set<Long> all = new LinkedHashSet<>();
            corporationRepository.findAll().forEach(c -> all.add(c.getId()));
            return all;
        }
        Long corporationId = effectiveCorporationId(scope);
        return corporationId == null ? Set.of() : Set.of(corporationId);
    }

    private Long effectiveCorporationId(AccessScope scope) {
        if (scope.corporationId() != null) {
            return scope.corporationId();
        }
        if (scope.divisionId() == null) {
            return null;
        }
        return divisionRepository.findById(scope.divisionId())
                .map(d -> d.getCorporation().getId())
                .orElse(null);
    }

    private Long requireVisibleCorporationId(AccessScope scope) {
        Long corporationId = effectiveCorporationId(scope);
        if (corporationId == null) {
            throw ApiException.forbidden("Account has no administrative scope.");
        }
        return corporationId;
    }

    private void requireVisibleCorporation(AccessScope scope, Long corporationId) {
        if (!visibleCorporationIds(scope).contains(corporationId)) {
            throw ApiException.forbidden("Corporation " + corporationId + " is outside your scope.");
        }
    }
}