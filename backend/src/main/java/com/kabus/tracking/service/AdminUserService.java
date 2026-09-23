package com.kabus.tracking.service;

import com.kabus.tracking.domain.entity.Corporation;
import com.kabus.tracking.domain.entity.CorporationAdminProfile;
import com.kabus.tracking.domain.entity.Depot;
import com.kabus.tracking.domain.entity.DepotHeadProfile;
import com.kabus.tracking.domain.entity.Division;
import com.kabus.tracking.domain.entity.DivisionAdminProfile;
import com.kabus.tracking.domain.entity.DivisionManagerProfile;
import com.kabus.tracking.domain.entity.Role;
import com.kabus.tracking.domain.entity.Town;
import com.kabus.tracking.domain.entity.TownManagerProfile;
import com.kabus.tracking.domain.entity.User;
import com.kabus.tracking.domain.enums.RoleCode;
import com.kabus.tracking.domain.repository.CorporationAdminProfileRepository;
import com.kabus.tracking.domain.repository.CorporationRepository;
import com.kabus.tracking.domain.repository.DepotHeadProfileRepository;
import com.kabus.tracking.domain.repository.DepotRepository;
import com.kabus.tracking.domain.repository.DivisionAdminProfileRepository;
import com.kabus.tracking.domain.repository.DivisionManagerProfileRepository;
import com.kabus.tracking.domain.repository.DivisionRepository;
import com.kabus.tracking.domain.repository.RefreshTokenRepository;
import com.kabus.tracking.domain.repository.RoleRepository;
import com.kabus.tracking.domain.repository.SuperAdminProfileRepository;
import com.kabus.tracking.domain.repository.TownManagerProfileRepository;
import com.kabus.tracking.domain.repository.TownRepository;
import com.kabus.tracking.domain.repository.UserRepository;
import com.kabus.tracking.exception.ApiException;
import com.kabus.tracking.security.UserPrincipal;
import com.kabus.tracking.web.dto.AdminUserDtos;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SUPER_ADMIN admin-user management.
 *
 * <p>Every managed account has exactly one admin role and the matching scope
 * row. Role membership lives only in the role profile tables, so changing a
 * role or disabling an account immediately invalidates existing access tokens
 * (the JWT filter re-validates membership on every request) and all refresh
 * tokens are revoked as well.</p>
 */
@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final SuperAdminProfileRepository superAdminProfileRepository;
    private final CorporationAdminProfileRepository corporationAdminProfileRepository;
    private final DivisionAdminProfileRepository divisionAdminProfileRepository;
    private final DivisionManagerProfileRepository divisionManagerProfileRepository;
    private final DepotHeadProfileRepository depotHeadProfileRepository;
    private final TownManagerProfileRepository townManagerProfileRepository;
    private final CorporationRepository corporationRepository;
    private final DivisionRepository divisionRepository;
    private final DepotRepository depotRepository;
    private final TownRepository townRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public AdminUserService(UserRepository userRepository,
                            RoleRepository roleRepository,
                            SuperAdminProfileRepository superAdminProfileRepository,
                            CorporationAdminProfileRepository corporationAdminProfileRepository,
                            DivisionAdminProfileRepository divisionAdminProfileRepository,
                            DivisionManagerProfileRepository divisionManagerProfileRepository,
                            DepotHeadProfileRepository depotHeadProfileRepository,
                            TownManagerProfileRepository townManagerProfileRepository,
                            CorporationRepository corporationRepository,
                            DivisionRepository divisionRepository,
                            DepotRepository depotRepository,
                            TownRepository townRepository,
                            RefreshTokenRepository refreshTokenRepository,
                            PasswordEncoder passwordEncoder,
                            AuditService auditService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.superAdminProfileRepository = superAdminProfileRepository;
        this.corporationAdminProfileRepository = corporationAdminProfileRepository;
        this.divisionAdminProfileRepository = divisionAdminProfileRepository;
        this.divisionManagerProfileRepository = divisionManagerProfileRepository;
        this.depotHeadProfileRepository = depotHeadProfileRepository;
        this.townManagerProfileRepository = townManagerProfileRepository;
        this.corporationRepository = corporationRepository;
        this.divisionRepository = divisionRepository;
        this.depotRepository = depotRepository;
        this.townRepository = townRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------
    // Guards
    // ------------------------------------------------------------------

    private void requireSuperAdmin(UserPrincipal principal) {
        if (principal == null || !principal.isSuperAdmin()) {
            throw ApiException.forbidden("Only SUPER_ADMIN may manage admin users.");
        }
    }

    private RoleCode parseManagedRole(String raw) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.badRequest("Role is required.");
        }
        String normalized = raw.trim().toUpperCase();
        if (!AdminUserDtos.MANAGED_ROLES.contains(normalized)) {
            throw ApiException.badRequest("Role '" + raw + "' is not manageable through this API. "
                    + "Allowed: " + AdminUserDtos.MANAGED_ROLES);
        }
        return RoleCode.valueOf(normalized);
    }

    private void audit(UserPrincipal principal, HttpServletRequest http, String action, String resourceType,
                       Long resourceId, Object detail) {
        String ip = http == null ? null : http.getRemoteAddr();
        String ua = http == null ? null : http.getHeader("User-Agent");
        auditService.record(principal == null ? null : principal.getUserId(), action, resourceType, resourceId,
                detail, ip, ua);
    }

    // ------------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<AdminUserDtos.UserResponse> list(UserPrincipal principal, String search, int page, int size) {
        requireSuperAdmin(principal);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        Page<User> users = (search == null || search.isBlank())
                ? userRepository.findAllByOrderByIdAsc(pageable)
                : userRepository.searchByTerm(search.trim(), pageable);
        return users.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public AdminUserDtos.UserResponse get(UserPrincipal principal, Long id) {
        requireSuperAdmin(principal);
        User user = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("User not found: " + id));
        return toResponse(user);
    }

    // ------------------------------------------------------------------
    // Mutations
    // ------------------------------------------------------------------

    @Transactional
    public AdminUserDtos.UserResponse create(UserPrincipal principal, AdminUserDtos.CreateUserRequest req,
                                             HttpServletRequest http) {
        requireSuperAdmin(principal);
        RoleCode role = parseManagedRole(req.role());
        Scope scope = validateScope(role, req.corporationId(), req.divisionId(), req.depotId(), req.townId());

        if (userRepository.existsByUsername(req.username())) {
            throw ApiException.conflict("Username '" + req.username() + "' is already in use.");
        }
        if (req.email() != null && !req.email().isBlank() && userRepository.existsByEmail(req.email())) {
            throw ApiException.conflict("Email '" + req.email() + "' is already in use.");
        }

        User user = new User();
        user.setUsername(req.username().trim());
        user.setFullName(req.fullName().trim());
        user.setEmail(blankToNull(req.email()));
        user.setPhone(blankToNull(req.phone()));
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setEnabled(req.enabled() == null || req.enabled());
        applyScopeColumns(user, scope);
        userRepository.save(user);

        Role roleEntity = roleRepository.findByCode(role)
                .orElseGet(() -> roleRepository.save(new Role(role, role.name(), null)));
        user.getRoles().add(roleEntity);
        userRepository.save(user);

        createProfile(user, role, scope);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("username", user.getUsername());
        detail.put("role", role.name());
        detail.put("corporationId", scope.corporationId());
        detail.put("divisionId", scope.divisionId());
        detail.put("depotId", scope.depotId());
        detail.put("townId", scope.townId());
        audit(principal, http, "ADMIN_USER_CREATE", "USER", user.getId(), detail);

        return toResponse(user);
    }

    @Transactional
    public AdminUserDtos.UserResponse update(UserPrincipal principal, Long id,
                                             AdminUserDtos.UpdateUserRequest req, HttpServletRequest http) {
        requireSuperAdmin(principal);
        User user = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("User not found: " + id));

        if (req.email() != null && !req.email().isBlank()
                && userRepository.findByEmail(req.email()).filter(other -> !other.getId().equals(id)).isPresent()) {
            throw ApiException.conflict("Email '" + req.email() + "' is already in use.");
        }

        Map<String, Object> before = snapshot(user);
        user.setFullName(req.fullName().trim());
        user.setEmail(blankToNull(req.email()));
        user.setPhone(blankToNull(req.phone()));
        userRepository.save(user);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("before", before);
        detail.put("after", snapshot(user));
        audit(principal, http, "ADMIN_USER_UPDATE", "USER", user.getId(), detail);

        return toResponse(user);
    }

    @Transactional
    public AdminUserDtos.UserResponse changeRole(UserPrincipal principal, Long id,
                                                 AdminUserDtos.ChangeRoleRequest req, HttpServletRequest http) {
        requireSuperAdmin(principal);
        User user = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("User not found: " + id));
        if (superAdminProfileRepository.existsByUserId(id)) {
            throw ApiException.badRequest("SUPER_ADMIN accounts cannot be modified through this API.");
        }
        RoleCode newRole = parseManagedRole(req.role());
        Scope scope = validateScope(newRole, req.corporationId(), req.divisionId(), req.depotId(), req.townId());

        String oldRole = resolveRole(id);
        deleteAllProfiles(id);
        applyScopeColumns(user, scope);
        syncAdminRole(user, newRole);
        userRepository.save(user);
        createProfile(user, newRole, scope);

        // Membership changed: kill refresh tokens so the old role cannot be renewed.
        refreshTokenRepository.revokeAllForUser(id);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("fromRole", oldRole);
        detail.put("toRole", newRole.name());
        detail.put("corporationId", scope.corporationId());
        detail.put("divisionId", scope.divisionId());
        detail.put("depotId", scope.depotId());
        detail.put("townId", scope.townId());
        audit(principal, http, "ADMIN_USER_ROLE_CHANGE", "USER", id, detail);

        return toResponse(user);
    }

    @Transactional
    public AdminUserDtos.UserResponse setEnabled(UserPrincipal principal, Long id, boolean enabled,
                                                 HttpServletRequest http) {
        requireSuperAdmin(principal);
        User user = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("User not found: " + id));
        if (!enabled && superAdminProfileRepository.existsByUserId(id)) {
            throw ApiException.badRequest("SUPER_ADMIN accounts cannot be disabled through this API.");
        }
        if (!enabled && principal.getUserId().equals(id)) {
            throw ApiException.badRequest("You cannot disable your own account.");
        }
        user.setEnabled(enabled);
        userRepository.save(user);
        if (!enabled) {
            refreshTokenRepository.revokeAllForUser(id);
        }
        audit(principal, http, enabled ? "ADMIN_USER_ENABLE" : "ADMIN_USER_DISABLE", "USER", id,
                Map.of("enabled", enabled));
        return toResponse(user);
    }

    @Transactional
    public AdminUserDtos.UserResponse changePassword(UserPrincipal principal, Long id,
                                                     AdminUserDtos.ChangePasswordRequest req,
                                                     HttpServletRequest http) {
        requireSuperAdmin(principal);
        User user = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("User not found: " + id));
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setMustChangePassword(true);
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(id);
        // Never log or return the password.
        audit(principal, http, "ADMIN_USER_PASSWORD_RESET", "USER", id, Map.of("mustChangePassword", true));
        return toResponse(user);
    }

    // ------------------------------------------------------------------
    // Scope helpers
    // ------------------------------------------------------------------

    private record Scope(Long corporationId, Long divisionId, Long depotId, Long townId) {
    }

    private Scope validateScope(RoleCode role, Long corporationId, Long divisionId, Long depotId, Long townId) {
        switch (role) {
            case DIVISION_ADMIN -> {
                if (corporationId != null) {
                    corporationRepository.findById(corporationId)
                            .orElseThrow(() -> ApiException.badRequest("Corporation not found: " + corporationId));
                    return new Scope(corporationId, null, null, null);
                }
                if (divisionId != null) {
                    divisionRepository.findById(divisionId)
                            .orElseThrow(() -> ApiException.badRequest("Division not found: " + divisionId));
                    return new Scope(null, divisionId, null, null);
                }
                throw ApiException.badRequest("DIVISION_ADMIN requires a corporationId (or a divisionId for a division-scoped admin).");
            }
            case DIVISION_MANAGER -> {
                if (divisionId == null) {
                    throw ApiException.badRequest(role.name() + " requires a divisionId.");
                }
                divisionRepository.findById(divisionId)
                        .orElseThrow(() -> ApiException.badRequest("Division not found: " + divisionId));
                return new Scope(null, divisionId, null, null);
            }
            case DEPOT_HEAD -> {
                if (depotId == null) {
                    throw ApiException.badRequest("DEPOT_HEAD requires a depotId.");
                }
                depotRepository.findById(depotId)
                        .orElseThrow(() -> ApiException.badRequest("Depot not found: " + depotId));
                return new Scope(null, null, depotId, null);
            }
            case TOWN_MANAGER -> {
                if (townId == null) {
                    throw ApiException.badRequest("TOWN_MANAGER requires a townId.");
                }
                townRepository.findById(townId)
                        .orElseThrow(() -> ApiException.badRequest("Town not found: " + townId));
                return new Scope(null, null, null, townId);
            }
            default -> throw ApiException.badRequest("Unsupported role: " + role);
        }
    }

    private void applyScopeColumns(User user, Scope scope) {
        user.setDivision(scope.divisionId() == null ? null
                : divisionRepository.getReferenceById(scope.divisionId()));
        user.setDepot(scope.depotId() == null ? null
                : depotRepository.getReferenceById(scope.depotId()));
        user.setTown(scope.townId() == null ? null
                : townRepository.getReferenceById(scope.townId()));
    }

    private void createProfile(User user, RoleCode role, Scope scope) {
        switch (role) {
            case DIVISION_ADMIN -> {
                if (scope.corporationId() != null) {
                    CorporationAdminProfile p = new CorporationAdminProfile();
                    p.setUser(user);
                    p.setCorporation(corporationRepository.getReferenceById(scope.corporationId()));
                    corporationAdminProfileRepository.save(p);
                } else {
                    DivisionAdminProfile p = new DivisionAdminProfile();
                    p.setUser(user);
                    p.setDivision(divisionRepository.getReferenceById(scope.divisionId()));
                    divisionAdminProfileRepository.save(p);
                }
            }
            case DIVISION_MANAGER -> {
                DivisionManagerProfile p = new DivisionManagerProfile();
                p.setUser(user);
                p.setDivision(divisionRepository.getReferenceById(scope.divisionId()));
                divisionManagerProfileRepository.save(p);
            }
            case DEPOT_HEAD -> {
                DepotHeadProfile p = new DepotHeadProfile();
                p.setUser(user);
                p.setDepot(depotRepository.getReferenceById(scope.depotId()));
                depotHeadProfileRepository.save(p);
            }
            case TOWN_MANAGER -> {
                TownManagerProfile p = new TownManagerProfile();
                p.setUser(user);
                p.setTown(townRepository.getReferenceById(scope.townId()));
                townManagerProfileRepository.save(p);
            }
            default -> throw ApiException.badRequest("Unsupported role: " + role);
        }
    }

    private void deleteAllProfiles(Long userId) {
        superAdminProfileRepository.deleteByUserId(userId);
        corporationAdminProfileRepository.deleteByUserId(userId);
        divisionAdminProfileRepository.deleteByUserId(userId);
        divisionManagerProfileRepository.deleteByUserId(userId);
        depotHeadProfileRepository.deleteByUserId(userId);
        townManagerProfileRepository.deleteByUserId(userId);
    }

    private void syncAdminRole(User user, RoleCode role) {
        user.getRoles().removeIf(r -> r.getCode() != null && r.getCode().name().endsWith("_ADMIN")
                || r.getCode() == RoleCode.DIVISION_MANAGER
                || r.getCode() == RoleCode.DEPOT_HEAD
                || r.getCode() == RoleCode.TOWN_MANAGER);
        Role roleEntity = roleRepository.findByCode(role)
                .orElseGet(() -> roleRepository.save(new Role(role, role.name(), null)));
        user.getRoles().add(roleEntity);
    }

    // ------------------------------------------------------------------
    // Response mapping
    // ------------------------------------------------------------------

    private AdminUserDtos.UserResponse toResponse(User user) {
        ProfileInfo info = resolveProfile(user.getId());
        return new AdminUserDtos.UserResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.isEnabled(),
                user.isLocked(),
                user.isMustChangePassword(),
                info == null ? null : info.role(),
                info == null ? null : info.corporationId(),
                info == null ? null : info.corporationName(),
                info == null ? null : info.divisionId(),
                info == null ? null : info.divisionName(),
                info == null ? null : info.depotId(),
                info == null ? null : info.depotName(),
                info == null ? null : info.townId(),
                info == null ? null : info.townName(),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }

    private record ProfileInfo(String role, Long corporationId, String corporationName,
                               Long divisionId, String divisionName,
                               Long depotId, String depotName, Long townId, String townName) {
    }

    private ProfileInfo resolveProfile(Long userId) {
        var superAdmin = superAdminProfileRepository.findByUserId(userId);
        if (superAdmin.isPresent()) {
            return new ProfileInfo("SUPER_ADMIN", null, null, null, null, null, null, null, null);
        }
        var corpAdmin = corporationAdminProfileRepository.findByUserId(userId);
        if (corpAdmin.isPresent()) {
            Corporation c = corpAdmin.get().getCorporation();
            return new ProfileInfo("DIVISION_ADMIN", c.getId(), c.getName(), null, null, null, null, null, null);
        }
        var divAdmin = divisionAdminProfileRepository.findByUserId(userId);
        if (divAdmin.isPresent()) {
            Division d = divAdmin.get().getDivision();
            return new ProfileInfo("DIVISION_ADMIN", null, null, d.getId(), d.getName(), null, null, null, null);
        }
        var divManager = divisionManagerProfileRepository.findByUserId(userId);
        if (divManager.isPresent()) {
            Division d = divManager.get().getDivision();
            return new ProfileInfo("DIVISION_MANAGER", null, null, d.getId(), d.getName(), null, null, null, null);
        }
        var depotHead = depotHeadProfileRepository.findByUserId(userId);
        if (depotHead.isPresent()) {
            Depot d = depotHead.get().getDepot();
            return new ProfileInfo("DEPOT_HEAD", null, null, null, null, d.getId(), d.getName(), null, null);
        }
        var townManager = townManagerProfileRepository.findByUserId(userId);
        if (townManager.isPresent()) {
            Town t = townManager.get().getTown();
            return new ProfileInfo("TOWN_MANAGER", null, null, null, null, null, null, t.getId(), t.getName());
        }
        return null;
    }

    private String resolveRole(Long userId) {
        ProfileInfo info = resolveProfile(userId);
        return info == null ? null : info.role();
    }

    private Map<String, Object> snapshot(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("fullName", user.getFullName());
        map.put("email", user.getEmail());
        map.put("phone", user.getPhone());
        map.put("enabled", user.isEnabled());
        return map;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
