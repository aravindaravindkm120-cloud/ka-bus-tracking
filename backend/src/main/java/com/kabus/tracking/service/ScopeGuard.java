package com.kabus.tracking.service;

import com.kabus.tracking.domain.repository.DivisionRepository;
import com.kabus.tracking.exception.ApiException;
import org.springframework.stereotype.Service;

/**
 * Enforces that a mutation targets an organizational node inside the caller's
 * server-resolved {@link AccessScope}. Scope is never taken from the request.
 *
 * <p>The checks are ordered most-restrictive-first (town, then depot, then
 * division) so that a narrower scope is never widened by a dimension they also
 * happen to carry. A TOWN_MANAGER has a division and a depot too, but their
 * scope is exactly their one town; a DEPOT_HEAD has a division, but their scope
 * is exactly their one depot.</p>
 */
@Service
public class ScopeGuard {

    private final DivisionRepository divisionRepository;

    public ScopeGuard(DivisionRepository divisionRepository) {
        this.divisionRepository = divisionRepository;
    }

    /**
     * Requires the target node to fall within the scope. Provide the ids the
     * entity actually carries; null means "unknown/not applicable".
     *
     * @param scope      resolved scope of the caller
     * @param divisionId division of the target (or null)
     * @param depotId    depot of the target (or null)
     * @param townId     town of the target (or null)
     */
    public void requireWithin(AccessScope scope, Long divisionId, Long depotId, Long townId) {
        if (scope == null) {
            throw ApiException.forbidden("Account has no administrative scope.");
        }
        if (scope.wholeSystem()) {
            return;
        }
        if (scope.corporationId() != null) {
            Long targetCorporationId = divisionId == null ? null
                    : divisionRepository.findById(divisionId)
                            .map(d -> d.getCorporation().getId())
                            .orElse(null);
            if (targetCorporationId == null || !targetCorporationId.equals(scope.corporationId())) {
                throw ApiException.forbidden("Target is outside your corporation scope.");
            }
            return;
        }
        if (scope.townId() != null) {
            if (townId == null || !townId.equals(scope.townId())) {
                throw ApiException.forbidden("Target is outside your town scope.");
            }
            return;
        }
        if (scope.depotId() != null) {
            if (depotId == null || !depotId.equals(scope.depotId())) {
                throw ApiException.forbidden("Target is outside your depot scope.");
            }
            return;
        }
        if (scope.divisionId() != null) {
            if (divisionId == null || !divisionId.equals(scope.divisionId())) {
                throw ApiException.forbidden("Target is outside your division scope.");
            }
            return;
        }
        throw ApiException.forbidden("Account has no administrative scope.");
    }

    /** Read-side equivalent: true when the node is visible to the caller. */
    public boolean visible(AccessScope scope, Long divisionId, Long depotId, Long townId) {
        try {
            requireWithin(scope, divisionId, depotId, townId);
            return true;
        } catch (ApiException e) {
            return false;
        }
    }
}
