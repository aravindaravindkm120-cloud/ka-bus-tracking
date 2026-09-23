package com.kabus.tracking.service;

import com.kabus.tracking.domain.entity.Depot;
import com.kabus.tracking.domain.entity.Staff;
import com.kabus.tracking.domain.entity.Town;
import com.kabus.tracking.domain.entity.User;
import com.kabus.tracking.domain.repository.DepotRepository;
import com.kabus.tracking.domain.repository.StaffRepository;
import com.kabus.tracking.domain.repository.TownRepository;
import com.kabus.tracking.domain.repository.UserRepository;
import com.kabus.tracking.exception.ApiException;
import com.kabus.tracking.security.UserPrincipal;
import com.kabus.tracking.web.dto.StaffDtos;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** Staff management, restricted to the caller's server-resolved scope. */
@Service
public class StaffService {

    private final StaffRepository staffRepository;
    private final DepotRepository depotRepository;
    private final TownRepository townRepository;
    private final UserRepository userRepository;
    private final ScopeResolver scopeResolver;
    private final ScopeGuard scopeGuard;
    private final AuditService auditService;

    public StaffService(StaffRepository staffRepository,
                        DepotRepository depotRepository,
                        TownRepository townRepository,
                        UserRepository userRepository,
                        ScopeResolver scopeResolver,
                        ScopeGuard scopeGuard,
                        AuditService auditService) {
        this.staffRepository = staffRepository;
        this.depotRepository = depotRepository;
        this.townRepository = townRepository;
        this.userRepository = userRepository;
        this.scopeResolver = scopeResolver;
        this.scopeGuard = scopeGuard;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<StaffDtos.StaffResponse> list(UserPrincipal principal, String search, int page, int size) {
        AccessScope scope = scopeResolver.resolve(principal);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        String term = (search == null || search.isBlank()) ? null : search.trim();

        Page<Staff> staff;
        if (scope.wholeSystem()) {
            staff = term == null ? staffRepository.findAll(pageable) : staffRepository.searchAll(term, pageable);
        } else if (scope.townId() != null) {
            List<Long> townIds = List.of(scope.townId());
            staff = term == null
                    ? staffRepository.findByTownIdIn(townIds, pageable)
                    : staffRepository.searchInTowns(townIds, term, pageable);
        } else {
            List<Long> depotIds = scopeResolver.depotIds(scope);
            if (depotIds.isEmpty()) {
                return Page.empty(pageable);
            }
            staff = term == null
                    ? staffRepository.findByDepotIdIn(depotIds, pageable)
                    : staffRepository.searchInDepots(depotIds, term, pageable);
        }
        return staff.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public StaffDtos.StaffResponse get(UserPrincipal principal, Long id) {
        AccessScope scope = scopeResolver.resolve(principal);
        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Staff not found: " + id));
        requireVisible(scope, staff);
        return toResponse(staff);
    }

    @Transactional
    public StaffDtos.StaffResponse create(UserPrincipal principal, StaffDtos.CreateStaffRequest req,
                                          HttpServletRequest http) {
        AccessScope scope = scopeResolver.resolve(principal);
        Depot depot = depotRepository.findById(req.depotId())
                .orElseThrow(() -> ApiException.badRequest("Depot not found: " + req.depotId()));
        Long divisionId = depot.getDivision().getId();
        Long townId = resolveTown(req.townId(), depot);
        scopeGuard.requireWithin(scope, divisionId, depot.getId(), townId);

        if (req.empCode() != null && !req.empCode().isBlank()
                && staffRepository.existsByEmpCodeIgnoreCase(req.empCode().trim())) {
            throw ApiException.conflict("Employee code '" + req.empCode() + "' already exists.");
        }

        Staff staff = new Staff();
        staff.setDepot(depot);
        staff.setDivision(depot.getDivision());
        staff.setCorporation(depot.getDivision().getCorporation());
        staff.setTown(townId == null ? null : townRepository.getReferenceById(townId));
        staff.setFullName(req.fullName().trim());
        staff.setPhone(blankToNull(req.phone()));
        staff.setEmpCode(blankToNull(req.empCode()));
        staff.setDesignation(blankToNull(req.designation()));
        staff.setStatus(req.status() == null || req.status().isBlank() ? "ACTIVE" : req.status().trim().toUpperCase());
        linkUser(staff, req.userId(), scope);
        staffRepository.save(staff);

        audit(principal, http, "STAFF_CREATE", staff.getId(),
                Map.of("fullName", staff.getFullName(), "depotId", depot.getId()));
        return toResponse(staff);
    }

    @Transactional
    public StaffDtos.StaffResponse update(UserPrincipal principal, Long id, StaffDtos.UpdateStaffRequest req,
                                          HttpServletRequest http) {
        AccessScope scope = scopeResolver.resolve(principal);
        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Staff not found: " + id));
        requireVisible(scope, staff);

        if (req.empCode() != null && !req.empCode().isBlank()) {
            String code = req.empCode().trim();
            if (staffRepository.existsByEmpCodeIgnoreCaseAndIdNot(code, id)) {
                throw ApiException.conflict("Employee code '" + code + "' already exists.");
            }
            staff.setEmpCode(code);
        } else {
            staff.setEmpCode(null);
        }

        if (req.townId() != null && !req.townId().equals(staff.getTown() == null ? null : staff.getTown().getId())) {
            Long townId = resolveTown(req.townId(), staff.getDepot());
            scopeGuard.requireWithin(scope, staff.getDivision().getId(), staff.getDepot().getId(), townId);
            staff.setTown(townRepository.getReferenceById(townId));
        }

        staff.setFullName(req.fullName().trim());
        staff.setPhone(blankToNull(req.phone()));
        staff.setDesignation(blankToNull(req.designation()));
        staff.setStatus(req.status() == null || req.status().isBlank() ? "ACTIVE" : req.status().trim().toUpperCase());
        staffRepository.save(staff);

        audit(principal, http, "STAFF_UPDATE", staff.getId(), Map.of("fullName", staff.getFullName()));
        return toResponse(staff);
    }

    @Transactional
    public StaffDtos.StaffResponse setStatus(UserPrincipal principal, Long id, String status,
                                             HttpServletRequest http) {
        AccessScope scope = scopeResolver.resolve(principal);
        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Staff not found: " + id));
        requireVisible(scope, staff);
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!List.of("ACTIVE", "INACTIVE", "SUSPENDED", "RETIRED").contains(normalized)) {
            throw ApiException.badRequest("Status must be ACTIVE, INACTIVE, SUSPENDED or RETIRED.");
        }
        staff.setStatus(normalized);
        staffRepository.save(staff);
        audit(principal, http, "STAFF_STATUS_CHANGE", staff.getId(), Map.of("status", normalized));
        return toResponse(staff);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Long resolveTown(Long townId, Depot depot) {
        if (townId == null) {
            return null;
        }
        Town town = townRepository.findById(townId)
                .orElseThrow(() -> ApiException.badRequest("Town not found: " + townId));
        if (!town.getDepot().getId().equals(depot.getId())) {
            throw ApiException.badRequest("Town " + townId + " does not belong to depot " + depot.getId() + ".");
        }
        return town.getId();
    }

    private void linkUser(Staff staff, Long userId, AccessScope scope) {
        if (userId == null) {
            return;
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.badRequest("User not found: " + userId));
        // A scoped admin may only link a user that itself lives inside the scope;
        // this blocks linking another division's user or a system-level account.
        scopeGuard.requireWithin(scope,
                user.getDivision() == null ? null : user.getDivision().getId(),
                user.getDepot() == null ? null : user.getDepot().getId(),
                user.getTown() == null ? null : user.getTown().getId());
        staffRepository.findByUserId(userId).ifPresent(existing -> {
            throw ApiException.conflict("User " + userId + " is already linked to staff " + existing.getId() + ".");
        });
        staff.setUser(user);
    }

    private void requireVisible(AccessScope scope, Staff staff) {
        scopeGuard.requireWithin(scope, staff.getDivision().getId(), staff.getDepot().getId(),
                staff.getTown() == null ? null : staff.getTown().getId());
    }

    private StaffDtos.StaffResponse toResponse(Staff s) {
        return new StaffDtos.StaffResponse(
                s.getId(),
                s.getFullName(),
                s.getPhone(),
                s.getEmpCode(),
                s.getDesignation(),
                s.getStatus(),
                s.getUser() == null ? null : s.getUser().getId(),
                s.getUser() == null ? null : s.getUser().getUsername(),
                s.getCorporation().getId(),
                s.getCorporation().getName(),
                s.getDivision().getId(),
                s.getDivision().getName(),
                s.getDepot().getId(),
                s.getDepot().getName(),
                s.getTown() == null ? null : s.getTown().getId(),
                s.getTown() == null ? null : s.getTown().getName(),
                s.getCreatedAt(),
                s.getUpdatedAt());
    }

    private void audit(UserPrincipal principal, HttpServletRequest http, String action, Long id, Object detail) {
        auditService.record(principal == null ? null : principal.getUserId(), action, "STAFF", id, detail,
                http == null ? null : http.getRemoteAddr(),
                http == null ? null : http.getHeader("User-Agent"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
