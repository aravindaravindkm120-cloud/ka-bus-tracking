package com.kabus.tracking.service;

import com.kabus.tracking.domain.entity.Corporation;
import com.kabus.tracking.domain.entity.Division;
import com.kabus.tracking.domain.entity.DivisionManagerProfile;
import com.kabus.tracking.domain.entity.Depot;
import com.kabus.tracking.domain.entity.Town;
import com.kabus.tracking.domain.repository.CorporationRepository;
import com.kabus.tracking.domain.repository.DivisionRepository;
import com.kabus.tracking.domain.repository.DivisionManagerProfileRepository;
import com.kabus.tracking.domain.repository.DepotRepository;
import com.kabus.tracking.domain.repository.TownRepository;
import com.kabus.tracking.domain.repository.UserRepository;
import com.kabus.tracking.exception.ApiException;
import com.kabus.tracking.security.UserPrincipal;
import com.kabus.tracking.web.dto.OrgDtos;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Organization hierarchy management: Corporation &gt; Division &gt; Depot &gt; Town.
 *
 * Mutations are reserved for SUPER_ADMIN; every other role (including other
 * admin roles) receives 403. Writes are duplicate-checked against each level's
 * uniqueness key (corporation code globally; division/​depot/town code within
 * parent), never silently overwriting. Reads stay within the caller's existing
 * organizational scope; SUPER_ADMIN sees the whole system, a corporation-level
 * DIVISION_ADMIN sees its own corporation's branch only, and scoped admin
 * roles cannot read organization data. Every mutation is written to the audit log.
 */
@Service
public class OrgManagementService {

    private final CorporationRepository corporationRepository;
    private final DivisionRepository divisionRepository;
    private final DivisionManagerProfileRepository divisionManagerProfileRepository;
    private final DepotRepository depotRepository;
    private final TownRepository townRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ScopeResolver scopeResolver;

    public OrgManagementService(CorporationRepository corporationRepository,
                                DivisionRepository divisionRepository,
                                DivisionManagerProfileRepository divisionManagerProfileRepository,
                                DepotRepository depotRepository,
                                TownRepository townRepository,
                                UserRepository userRepository,
                                AuditService auditService,
                                ScopeResolver scopeResolver) {
        this.corporationRepository = corporationRepository;
        this.divisionRepository = divisionRepository;
        this.divisionManagerProfileRepository = divisionManagerProfileRepository;
        this.depotRepository = depotRepository;
        this.townRepository = townRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.scopeResolver = scopeResolver;
    }

    // ------------------------------------------------------------------
    // Guards
    // ------------------------------------------------------------------

    private void requireSuperAdmin(UserPrincipal principal) {
        if (principal == null || !principal.isSuperAdmin()) {
            throw ApiException.forbidden("Only SUPER_ADMIN may manage organization records.");
        }
    }

    /**
     * Read-side guard. SUPER_ADMIN may read anything; a corporation-level
     * DIVISION_ADMIN may read only the branch of their own corporation. Every
     * other role is denied organization reads.
     */
    private void requireReadWithin(UserPrincipal principal, Long divisionId) {
        if (principal == null) {
            throw ApiException.forbidden("Account has no administrative scope.");
        }
        if (principal.isSuperAdmin()) {
            return;
        }
        AccessScope scope = scopeResolver.resolve(principal);
        if (scope.corporationId() == null) {
            throw ApiException.forbidden("Only SUPER_ADMIN or a corporation Division Admin may read organization records.");
        }
        Long targetCorporationId = divisionId == null ? null
                : divisionRepository.findById(divisionId)
                        .map(d -> d.getCorporation().getId())
                        .orElse(null);
        if (targetCorporationId == null || !targetCorporationId.equals(scope.corporationId())) {
            throw ApiException.forbidden("Target is outside your corporation scope.");
        }
    }

    private void requireCorporationReadWithin(UserPrincipal principal, Long corporationId) {
        if (principal == null) {
            throw ApiException.forbidden("Account has no administrative scope.");
        }
        if (principal.isSuperAdmin()) {
            return;
        }
        AccessScope scope = scopeResolver.resolve(principal);
        if (scope.corporationId() == null || !scope.corporationId().equals(corporationId)) {
            throw ApiException.forbidden("Target is outside your corporation scope.");
        }
    }

    private void audit(UserPrincipal principal, HttpServletRequest http, String action, String resourceType,
                       Long resourceId, String detail) {
        String ip = http == null ? null : http.getRemoteAddr();
        String ua = http == null ? null : http.getHeader("User-Agent");
        auditService.record(principal == null ? null : principal.getUserId(), action, resourceType, resourceId,
                detail, ip, ua);
    }

    // ------------------------------------------------------------------
    // Corporation
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<OrgDtos.CorporationResponse> listCorporations(UserPrincipal principal) {
        if (principal == null) {
            throw ApiException.forbidden("Account has no administrative scope.");
        }
        if (!principal.isSuperAdmin()) {
            AccessScope scope = scopeResolver.resolve(principal);
            if (scope.corporationId() == null) {
                throw ApiException.forbidden("Only SUPER_ADMIN or a corporation Division Admin may read organization records.");
            }
            Corporation own = corporationRepository.findById(scope.corporationId())
                    .orElseThrow(() -> ApiException.notFound("Corporation not found: " + scope.corporationId()));
            return List.of(corporationResponse(own));
        }
        return corporationRepository.findAll().stream()
                .sorted(Comparator.comparing(Corporation::getCode))
                .map(this::corporationResponse)
                .toList();
    }

    @Transactional
    public OrgDtos.CorporationResponse createCorporation(UserPrincipal principal, OrgDtos.CorporationRequest req,
                                                         HttpServletRequest http) {
        requireSuperAdmin(principal);
        String code = normalizeCode(req.code());
        corporationRepository.findByCode(code)
                .ifPresent(c -> {
                    throw ApiException.conflict("Corporation code '" + code + "' is already in use.");
                });
        Corporation c = new Corporation();
        c.setCode(code);
        c.setName(req.name());
        c.setAddress(req.address());
        c.setCity(req.city());
        c.setState(corporationState(req.state()));
        c.setContactEmail(req.contactEmail());
        c.setContactPhone(req.contactPhone());
        c.setEnabled(true);
        Corporation saved = corporationRepository.save(c);
        audit(principal, http, "ORG_CORP_CREATE", "CORPORATION", saved.getId(),
                "Created corporation " + saved.getCode());
        return corporationResponse(saved);
    }

    @Transactional
    public OrgDtos.CorporationResponse updateCorporation(UserPrincipal principal, Long id,
                                                         OrgDtos.CorporationRequest req, HttpServletRequest http) {
        requireSuperAdmin(principal);
        Corporation c = corporationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Corporation not found: " + id));
        String code = normalizeCode(req.code());
        corporationRepository.findByCode(code)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw ApiException.conflict("Corporation code '" + code + "' is already in use.");
                });
        c.setCode(code);
        c.setName(req.name());
        c.setAddress(req.address());
        c.setCity(req.city());
        c.setState(corporationState(req.state()));
        c.setContactEmail(req.contactEmail());
        c.setContactPhone(req.contactPhone());
        Corporation saved = corporationRepository.save(c);
        audit(principal, http, "ORG_CORP_UPDATE", "CORPORATION", saved.getId(),
                "Updated corporation " + saved.getCode());
        return corporationResponse(saved);
    }

    @Transactional
    public OrgDtos.CorporationResponse toggleCorporation(UserPrincipal principal, Long id, boolean enabled,
                                                         HttpServletRequest http) {
        requireSuperAdmin(principal);
        Corporation c = corporationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Corporation not found: " + id));
        c.setEnabled(enabled);
        Corporation saved = corporationRepository.save(c);
        audit(principal, http, enabled ? "ORG_CORP_ENABLE" : "ORG_CORP_DISABLE", "CORPORATION",
                saved.getId(), (enabled ? "Enabled" : "Disabled") + " corporation " + saved.getCode());
        return corporationResponse(saved);
    }

    private OrgDtos.CorporationResponse corporationResponse(Corporation c) {
        return new OrgDtos.CorporationResponse(
                c.getId(), c.getCode(), c.getName(), c.getAddress(), c.getCity(), c.getState(),
                c.getContactEmail(), c.getContactPhone(), c.isEnabled(), c.getCreatedAt(), c.getUpdatedAt());
    }

    /** Trims and uppercases a corporation code (e.g. " ksrtc " -> "KSRTC"). */
    private String normalizeCode(String code) {
        return code == null ? null : code.trim().toUpperCase();
    }

    /** Discards blank/case variants of the state field ("karnataka" -> "Karnataka"). */
    private String corporationState(String state) {
        if (state == null || state.isBlank()) {
            return "Karnataka";
        }
        String trimmed = state.trim();
        return trimmed.equalsIgnoreCase("karnataka") ? "Karnataka" : trimmed;
    }

    // ------------------------------------------------------------------
    // Division
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<OrgDtos.DivisionResponse> listDivisions(UserPrincipal principal, Long corporationId) {
        if (principal == null) {
            throw ApiException.forbidden("Account has no administrative scope.");
        }
        requireCorporationReadWithin(principal, corporationId);
        List<Division> rows = corporationId == null
                ? divisionRepository.findAll()
                : divisionRepository.findByCorporationId(corporationId);
        return rows.stream().sorted(Comparator.comparing(Division::getCode)).map(this::divisionResponse).toList();
    }

    @Transactional
    public OrgDtos.DivisionResponse createDivision(UserPrincipal principal, OrgDtos.DivisionRequest req,
                                                   HttpServletRequest http) {
        requireSuperAdmin(principal);
        Corporation corp = corporationRepository.findById(req.corporationId())
                .orElseThrow(() -> ApiException.notFound("Corporation not found: " + req.corporationId()));
        divisionRepository.findByCorporationIdAndCode(corp.getId(), req.code())
                .ifPresent(d -> {
                    throw ApiException.conflict("Division code '" + req.code() + "' already exists in corporation "
                            + corp.getCode() + ".");
                });
        Division d = new Division();
        d.setCorporation(corp);
        d.setCode(req.code());
        d.setName(req.name());
        d.setHeadOffice(req.headOffice());
        d.setEnabled(true);
        Division saved = divisionRepository.save(d);
        audit(principal, http, "ORG_DIV_CREATE", "DIVISION", saved.getId(),
                "Created division " + saved.getCode() + " under " + corp.getCode());
        return divisionResponse(saved);
    }

    @Transactional
    public OrgDtos.DivisionResponse updateDivision(UserPrincipal principal, Long id, OrgDtos.DivisionRequest req,
                                                   HttpServletRequest http) {
        requireSuperAdmin(principal);
        Division d = divisionRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Division not found: " + id));
        Corporation corp = corporationRepository.findById(req.corporationId())
                .orElseThrow(() -> ApiException.notFound("Corporation not found: " + req.corporationId()));
        divisionRepository.findByCorporationIdAndCode(corp.getId(), req.code())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw ApiException.conflict("Division code '" + req.code() + "' already exists in corporation "
                            + corp.getCode() + ".");
                });
        d.setCorporation(corp);
        d.setCode(req.code());
        d.setName(req.name());
        d.setHeadOffice(req.headOffice());
        Division saved = divisionRepository.save(d);
        audit(principal, http, "ORG_DIV_UPDATE", "DIVISION", saved.getId(),
                "Updated division " + saved.getCode());
        return divisionResponse(saved);
    }

    @Transactional
    public OrgDtos.DivisionResponse toggleDivision(UserPrincipal principal, Long id, boolean enabled,
                                                   HttpServletRequest http) {
        requireSuperAdmin(principal);
        Division d = divisionRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Division not found: " + id));
        d.setEnabled(enabled);
        Division saved = divisionRepository.save(d);
        audit(principal, http, enabled ? "ORG_DIV_ENABLE" : "ORG_DIV_DISABLE", "DIVISION",
                saved.getId(), (enabled ? "Enabled" : "Disabled") + " division " + saved.getCode());
        return divisionResponse(saved);
    }

    private OrgDtos.DivisionResponse divisionResponse(Division d) {
        DivisionManagerProfile adm = divisionManagerProfileRepository.findByDivisionId(d.getId()).orElse(null);
        Long adminId = null;
        String adminName = null;
        String adminEmail = null;
        String adminStatus = null;
        if (adm != null && adm.getUser() != null) {
            adminId = adm.getId();
            adminName = adm.getUser().getFullName();
            adminEmail = adm.getUser().getEmail();
            adminStatus = adm.getUser().isEnabled() && !adm.getUser().isLocked() ? "ACTIVE" : "INACTIVE";
        }
        return new OrgDtos.DivisionResponse(
                d.getId(), d.getCorporation().getId(), d.getCorporation().getCode(), d.getCorporation().getName(),
                d.getCode(), d.getName(), d.getHeadOffice(), d.isEnabled(),
                adminId, adminName, adminEmail, adminStatus, "DIVISION_MANAGER",
                d.getCreatedAt(), d.getUpdatedAt());
    }

    // ------------------------------------------------------------------
    // Depot
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<OrgDtos.DepotResponse> listDepots(UserPrincipal principal, Long divisionId) {
        requireReadWithin(principal, divisionId);
        List<Depot> rows = divisionId == null ? depotRepository.findAll() : depotRepository.findByDivisionId(divisionId);
        return rows.stream().sorted(Comparator.comparing(Depot::getCode)).map(this::depotResponse).toList();
    }

    @Transactional
    public OrgDtos.DepotResponse createDepot(UserPrincipal principal, OrgDtos.DepotRequest req,
                                             HttpServletRequest http) {
        requireSuperAdmin(principal);
        Division div = divisionRepository.findById(req.divisionId())
                .orElseThrow(() -> ApiException.notFound("Division not found: " + req.divisionId()));
        depotRepository.findByDivisionIdAndCode(div.getId(), req.code())
                .ifPresent(dp -> {
                    throw ApiException.conflict("Depot code '" + req.code() + "' already exists in division "
                            + div.getCode() + ".");
                });
        Depot dp = new Depot();
        dp.setDivision(div);
        dp.setCode(req.code());
        dp.setName(req.name());
        dp.setAddress(req.address());
        dp.setPhone(req.phone());
        dp.setEnabled(true);
        Depot saved = depotRepository.save(dp);
        audit(principal, http, "ORG_DEPOT_CREATE", "DEPOT", saved.getId(),
                "Created depot " + saved.getCode() + " under " + div.getCode());
        return depotResponse(saved);
    }

    @Transactional
    public OrgDtos.DepotResponse updateDepot(UserPrincipal principal, Long id, OrgDtos.DepotRequest req,
                                             HttpServletRequest http) {
        requireSuperAdmin(principal);
        Depot dp = depotRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Depot not found: " + id));
        Division div = divisionRepository.findById(req.divisionId())
                .orElseThrow(() -> ApiException.notFound("Division not found: " + req.divisionId()));
        depotRepository.findByDivisionIdAndCode(div.getId(), req.code())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw ApiException.conflict("Depot code '" + req.code() + "' already exists in division "
                            + div.getCode() + ".");
                });
        dp.setDivision(div);
        dp.setCode(req.code());
        dp.setName(req.name());
        dp.setAddress(req.address());
        dp.setPhone(req.phone());
        Depot saved = depotRepository.save(dp);
        audit(principal, http, "ORG_DEPOT_UPDATE", "DEPOT", saved.getId(),
                "Updated depot " + saved.getCode());
        return depotResponse(saved);
    }

    @Transactional
    public OrgDtos.DepotResponse toggleDepot(UserPrincipal principal, Long id, boolean enabled,
                                             HttpServletRequest http) {
        requireSuperAdmin(principal);
        Depot dp = depotRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Depot not found: " + id));
        dp.setEnabled(enabled);
        Depot saved = depotRepository.save(dp);
        audit(principal, http, enabled ? "ORG_DEPOT_ENABLE" : "ORG_DEPOT_DISABLE", "DEPOT",
                saved.getId(), (enabled ? "Enabled" : "Disabled") + " depot " + saved.getCode());
        return depotResponse(saved);
    }

    private OrgDtos.DepotResponse depotResponse(Depot d) {
        return new OrgDtos.DepotResponse(
                d.getId(), d.getDivision().getId(), d.getDivision().getCode(), d.getDivision().getName(),
                d.getCode(), d.getName(), d.getAddress(), d.getPhone(), d.isEnabled(), d.getCreatedAt(),
                d.getUpdatedAt());
    }

    // ------------------------------------------------------------------
    // Town
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<OrgDtos.TownResponse> listTowns(UserPrincipal principal, Long depotId) {
        if (principal != null && !principal.isSuperAdmin() && depotId != null) {
            Long divisionId = depotRepository.findById(depotId)
                    .map(dp -> dp.getDivision().getId())
                    .orElse(null);
            requireReadWithin(principal, divisionId);
        }
        List<Town> rows = depotId == null ? townRepository.findAll() : townRepository.findByDepotId(depotId);
        return rows.stream().sorted(Comparator.comparing(Town::getCode)).map(this::townResponse).toList();
    }

    @Transactional
    public OrgDtos.TownResponse createTown(UserPrincipal principal, OrgDtos.TownRequest req,
                                           HttpServletRequest http) {
        requireSuperAdmin(principal);
        Depot dp = depotRepository.findById(req.depotId())
                .orElseThrow(() -> ApiException.notFound("Depot not found: " + req.depotId()));
        townRepository.findByDepotIdAndCode(dp.getId(), req.code())
                .ifPresent(t -> {
                    throw ApiException.conflict("Town code '" + req.code() + "' already exists in depot "
                            + dp.getCode() + ".");
                });
        Town t = new Town();
        t.setDepot(dp);
        t.setCode(req.code());
        t.setName(req.name());
        t.setEnabled(true);
        Town saved = townRepository.save(t);
        audit(principal, http, "ORG_TOWN_CREATE", "TOWN", saved.getId(),
                "Created town " + saved.getCode() + " under " + dp.getCode());
        return townResponse(saved);
    }

    @Transactional
    public OrgDtos.TownResponse updateTown(UserPrincipal principal, Long id, OrgDtos.TownRequest req,
                                           HttpServletRequest http) {
        requireSuperAdmin(principal);
        Town t = townRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Town not found: " + id));
        Depot dp = depotRepository.findById(req.depotId())
                .orElseThrow(() -> ApiException.notFound("Depot not found: " + req.depotId()));
        townRepository.findByDepotIdAndCode(dp.getId(), req.code())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw ApiException.conflict("Town code '" + req.code() + "' already exists in depot "
                            + dp.getCode() + ".");
                });
        t.setDepot(dp);
        t.setCode(req.code());
        t.setName(req.name());
        Town saved = townRepository.save(t);
        audit(principal, http, "ORG_TOWN_UPDATE", "TOWN", saved.getId(),
                "Updated town " + saved.getCode());
        return townResponse(saved);
    }

    @Transactional
    public OrgDtos.TownResponse toggleTown(UserPrincipal principal, Long id, boolean enabled,
                                           HttpServletRequest http) {
        requireSuperAdmin(principal);
        Town t = townRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Town not found: " + id));
        t.setEnabled(enabled);
        Town saved = townRepository.save(t);
        audit(principal, http, enabled ? "ORG_TOWN_ENABLE" : "ORG_TOWN_DISABLE", "TOWN",
                saved.getId(), (enabled ? "Enabled" : "Disabled") + " town " + saved.getCode());
        return townResponse(saved);
    }

    private OrgDtos.TownResponse townResponse(Town t) {
        return new OrgDtos.TownResponse(
                t.getId(), t.getDepot().getId(), t.getDepot().getCode(), t.getDepot().getName(),
                t.getCode(), t.getName(), t.isEnabled(), t.getCreatedAt(), t.getUpdatedAt());
    }
}
