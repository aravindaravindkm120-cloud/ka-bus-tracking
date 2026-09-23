package com.kabus.tracking.service;

import com.kabus.tracking.domain.enums.RoleCode;
import com.kabus.tracking.exception.ApiException;
import com.kabus.tracking.security.UserPrincipal;
import com.kabus.tracking.domain.repository.DepotRepository;
import com.kabus.tracking.domain.repository.DivisionRepository;
import com.kabus.tracking.domain.repository.TownRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Resolves the organizational scope of an authenticated user from the
 * database-backed principal. Role and scope are always server-determined.
 */
@Service
public class ScopeResolver {

    private final DivisionRepository divisionRepository;
    private final DepotRepository depotRepository;
    private final TownRepository townRepository;

    public ScopeResolver(DivisionRepository divisionRepository,
                         DepotRepository depotRepository,
                         TownRepository townRepository) {
        this.divisionRepository = divisionRepository;
        this.depotRepository = depotRepository;
        this.townRepository = townRepository;
    }

    @Transactional(readOnly = true)
    public AccessScope resolve(UserPrincipal principal) {
        if (principal.isSuperAdmin()) {
            return AccessScope.system();
        }
        if (principal.hasRole(RoleCode.DIVISION_ADMIN)) {
            // Corporation-level DIVISION_ADMIN: scope is the whole corporation.
            if (principal.getCorporationId() != null) {
                return AccessScope.corporation(principal.getCorporationId());
            }
            require(principal.getDivisionId() != null, "Division admin has no division assigned.");
            return AccessScope.division(principal.getDivisionId());
        }
        if (principal.hasRole(RoleCode.DIVISION_MANAGER)) {
            require(principal.getDivisionId() != null, "Division manager has no division assigned.");
            return AccessScope.division(principal.getDivisionId());
        }
        if (principal.hasRole(RoleCode.DEPOT_HEAD)) {
            require(principal.getDepotId() != null, "Depot head has no depot assigned.");
            Long divisionId = depotRepository.findById(principal.getDepotId())
                    .orElseThrow(() -> ApiException.notFound("Depot not found in scope."))
                    .getDivision().getId();
            return AccessScope.depot(divisionId, principal.getDepotId());
        }
        if (principal.hasRole(RoleCode.TOWN_MANAGER)) {
            require(principal.getTownId() != null, "Town manager has no town assigned.");
            Long depotId = townRepository.findById(principal.getTownId())
                    .orElseThrow(() -> ApiException.notFound("Town not found in scope."))
                    .getDepot().getId();
            Long divisionId = depotRepository.findById(depotId)
                    .orElseThrow(() -> ApiException.notFound("Depot not found in scope."))
                    .getDivision().getId();
            return AccessScope.town(divisionId, depotId, principal.getTownId());
        }
        throw ApiException.forbidden("Account has no administrative scope.");
    }

    /** Convenience for services: division ids covered by a scope. */
    @Transactional(readOnly = true)
    public List<Long> divisionIds(AccessScope scope) {
        if (scope.wholeSystem()) {
            return divisionRepository.findAll().stream().map(d -> d.getId()).toList();
        }
        if (scope.corporationId() != null) {
            return divisionRepository.findByCorporationId(scope.corporationId()).stream()
                    .map(d -> d.getId()).toList();
        }
        if (scope.divisionId() != null) {
            return List.of(scope.divisionId());
        }
        return List.of();
    }

    /** Convenience for services: depot ids covered by a scope. */
    @Transactional(readOnly = true)
    public List<Long> depotIds(AccessScope scope) {
        if (scope.wholeSystem()) {
            return depotRepository.findAll().stream().map(d -> d.getId()).toList();
        }
        if (scope.corporationId() != null) {
            List<Long> divisionIds = divisionIds(scope);
            return depotRepository.findByDivisionIdIn(divisionIds).stream().map(d -> d.getId()).toList();
        }
        // A depot head is limited to exactly their depot.
        if (scope.depotId() != null) {
            return List.of(scope.depotId());
        }
        // A town manager is limited to the depot that owns their town.
        if (scope.townId() != null) {
            return townRepository.findById(scope.townId())
                    .map(t -> List.of(t.getDepot().getId()))
                    .orElseGet(List::of);
        }
        return depotRepository.findByDivisionIdIn(List.of(scope.divisionId())).stream().map(d -> d.getId()).toList();
    }

    /** Convenience for services: town ids covered by a scope. */
    @Transactional(readOnly = true)
    public List<Long> townIds(AccessScope scope) {
        if (scope.wholeSystem()) {
            return townRepository.findAll().stream().map(t -> t.getId()).toList();
        }
        if (scope.corporationId() != null) {
            List<Long> depotIds = depotIds(scope);
            return townRepository.findByDepotIdIn(depotIds).stream().map(t -> t.getId()).toList();
        }
        // A town manager is limited to exactly their town.
        if (scope.townId() != null) {
            return List.of(scope.townId());
        }
        // A depot head is limited to the towns of their one depot.
        if (scope.depotId() != null) {
            return townRepository.findByDepotId(scope.depotId()).stream().map(t -> t.getId()).toList();
        }
        List<Long> depotIds = depotRepository.findByDivisionIdIn(List.of(scope.divisionId()))
                .stream().map(d -> d.getId()).toList();
        return townRepository.findByDepotIdIn(depotIds).stream().map(t -> t.getId()).toList();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw ApiException.forbidden(message);
        }
    }
}