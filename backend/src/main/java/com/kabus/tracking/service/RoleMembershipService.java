package com.kabus.tracking.service;

import com.kabus.tracking.domain.enums.RoleCode;
import com.kabus.tracking.domain.repository.CorporationAdminProfileRepository;
import com.kabus.tracking.domain.repository.CrewRepository;
import com.kabus.tracking.domain.repository.DepotHeadProfileRepository;
import com.kabus.tracking.domain.repository.DivisionAdminProfileRepository;
import com.kabus.tracking.domain.repository.DivisionManagerProfileRepository;
import com.kabus.tracking.domain.repository.SuperAdminProfileRepository;
import com.kabus.tracking.domain.repository.TownManagerProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;

/**
 * Single source of truth for role membership. An account may assume a role
 * ONLY when a matching row exists in the role profile tables (admin roles) or
 * in the crew table (DRIVER/CONDUCTOR). The authentication layer therefore
 * does not derive roles from a generic role column - it asks this service.
 *
 * <p>{@link ScopeRef} carries the organizational scope that is bound to that
 * single role membership and is embedded into the signed JWT.</p>
 */
@Service
public class RoleMembershipService {

    public static final Set<RoleCode> ADMIN_ROLES = Set.of(
            RoleCode.SUPER_ADMIN, RoleCode.DIVISION_ADMIN, RoleCode.DIVISION_MANAGER,
            RoleCode.DEPOT_HEAD, RoleCode.TOWN_MANAGER);

    /** Immutable scope bound to one role membership. */
    public record ScopeRef(Long corporationId, Long divisionId, Long depotId, Long townId) {

        public boolean wholeSystem() {
            return corporationId == null && divisionId == null && depotId == null && townId == null;
        }
    }

    private final SuperAdminProfileRepository superAdminProfileRepository;
    private final CorporationAdminProfileRepository corporationAdminProfileRepository;
    private final DivisionAdminProfileRepository divisionAdminProfileRepository;
    private final DivisionManagerProfileRepository divisionManagerProfileRepository;
    private final DepotHeadProfileRepository depotHeadProfileRepository;
    private final TownManagerProfileRepository townManagerProfileRepository;
    private final CrewRepository crewRepository;

    public RoleMembershipService(SuperAdminProfileRepository superAdminProfileRepository,
                                 CorporationAdminProfileRepository corporationAdminProfileRepository,
                                 DivisionAdminProfileRepository divisionAdminProfileRepository,
                                 DivisionManagerProfileRepository divisionManagerProfileRepository,
                                 DepotHeadProfileRepository depotHeadProfileRepository,
                                 TownManagerProfileRepository townManagerProfileRepository,
                                 CrewRepository crewRepository) {
        this.superAdminProfileRepository = superAdminProfileRepository;
        this.corporationAdminProfileRepository = corporationAdminProfileRepository;
        this.divisionAdminProfileRepository = divisionAdminProfileRepository;
        this.divisionManagerProfileRepository = divisionManagerProfileRepository;
        this.depotHeadProfileRepository = depotHeadProfileRepository;
        this.townManagerProfileRepository = townManagerProfileRepository;
        this.crewRepository = crewRepository;
    }

    /** True when requestedRole is one of the five admin roles. */
    public boolean isAdminRole(RoleCode requestedRole) {
        return ADMIN_ROLES.contains(requestedRole);
    }

    /**
     * Resolves the scope of a specific role membership, if it exists.
     * Empty when the user has no row in the requested role's table.
     */
    @Transactional(readOnly = true)
    public Optional<ScopeRef> resolve(RoleCode role, Long userId) {
        switch (role) {
            case SUPER_ADMIN -> {
                return superAdminProfileRepository.findByUserId(userId).map(p -> new ScopeRef(null, null, null, null));
            }
            case DIVISION_ADMIN -> {
                // Corporation-level DIVISION_ADMIN (new model) first; the legacy
                // division-scoped DIVISION_ADMIN row is still honoured as a fallback.
                var corpAdmin = corporationAdminProfileRepository.findByUserId(userId);
                if (corpAdmin.isPresent()) {
                    return corpAdmin.map(p -> new ScopeRef(p.getCorporation().getId(), null, null, null));
                }
                return divisionAdminProfileRepository.findByUserId(userId)
                        .map(p -> new ScopeRef(null, p.getDivision().getId(), null, null));
            }
            case DIVISION_MANAGER -> {
                return divisionManagerProfileRepository.findByUserId(userId)
                        .map(p -> new ScopeRef(null, p.getDivision().getId(), null, null));
            }
            case DEPOT_HEAD -> {
                return depotHeadProfileRepository.findByUserId(userId)
                        .map(p -> new ScopeRef(null, null, p.getDepot().getId(), null));
            }
            case TOWN_MANAGER -> {
                return townManagerProfileRepository.findByUserId(userId)
                        .map(p -> new ScopeRef(null, null, null, p.getTown().getId()));
            }
            case DRIVER, CONDUCTOR -> {
                return crewRepository.findByUserId(userId)
                        .filter(c -> role.name().equalsIgnoreCase(c.getCrewType()))
                        .map(c -> new ScopeRef(null, null, null, null));
            }
            default -> {
                return Optional.empty();
            }
        }
    }

    /** The crew role (DRIVER/CONDUCTOR) linked to a user, when a crew record exists. */
    @Transactional(readOnly = true)
    public Optional<RoleCode> crewRole(Long userId) {
        return crewRepository.findByUserId(userId)
                .filter(c -> RoleCode.isSupported(c.getCrewType()))
                .map(c -> RoleCode.valueOf(c.getCrewType()));
    }
}