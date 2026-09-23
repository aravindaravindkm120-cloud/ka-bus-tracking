package com.kabus.tracking.api;

import com.kabus.tracking.security.UserPrincipal;
import com.kabus.tracking.service.OrgManagementService;
import com.kabus.tracking.web.dto.OrgDtos;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/organizations")
public class AdminOrganizationController {

    private final OrgManagementService orgManagementService;

    public AdminOrganizationController(OrgManagementService orgManagementService) {
        this.orgManagementService = orgManagementService;
    }

    // ------------------------------------------------------------------
    // Corporation
    // ------------------------------------------------------------------

    @GetMapping("/corporations")
    public ResponseEntity<List<OrgDtos.CorporationResponse>> listCorporations(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(orgManagementService.listCorporations(principal));
    }

    @PostMapping("/corporations")
    public ResponseEntity<OrgDtos.CorporationResponse> createCorporation(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody OrgDtos.CorporationRequest req,
            HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orgManagementService.createCorporation(principal, req, http));
    }

    @PutMapping("/corporations/{id}")
    public ResponseEntity<OrgDtos.CorporationResponse> updateCorporation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody OrgDtos.CorporationRequest req,
            HttpServletRequest http) {
        return ResponseEntity.ok(orgManagementService.updateCorporation(principal, id, req, http));
    }

    @PatchMapping("/corporations/{id}/enabled")
    public ResponseEntity<OrgDtos.CorporationResponse> toggleCorporation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestParam boolean enabled,
            HttpServletRequest http) {
        return ResponseEntity.ok(orgManagementService.toggleCorporation(principal, id, enabled, http));
    }

    // ------------------------------------------------------------------
    // Division
    // ------------------------------------------------------------------

    @GetMapping("/divisions")
    public ResponseEntity<List<OrgDtos.DivisionResponse>> listDivisions(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam Long parentId) {
        return ResponseEntity.ok(orgManagementService.listDivisions(principal, parentId));
    }

    @PostMapping("/divisions")
    public ResponseEntity<OrgDtos.DivisionResponse> createDivision(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody OrgDtos.DivisionRequest req,
            HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orgManagementService.createDivision(principal, req, http));
    }

    @PutMapping("/divisions/{id}")
    public ResponseEntity<OrgDtos.DivisionResponse> updateDivision(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody OrgDtos.DivisionRequest req,
            HttpServletRequest http) {
        return ResponseEntity.ok(orgManagementService.updateDivision(principal, id, req, http));
    }

    @PatchMapping("/divisions/{id}/enabled")
    public ResponseEntity<OrgDtos.DivisionResponse> toggleDivision(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestParam boolean enabled,
            HttpServletRequest http) {
        return ResponseEntity.ok(orgManagementService.toggleDivision(principal, id, enabled, http));
    }

    // ------------------------------------------------------------------
    // Depot
    // ------------------------------------------------------------------

    @GetMapping("/depots")
    public ResponseEntity<List<OrgDtos.DepotResponse>> listDepots(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam Long parentId) {
        return ResponseEntity.ok(orgManagementService.listDepots(principal, parentId));
    }

    @PostMapping("/depots")
    public ResponseEntity<OrgDtos.DepotResponse> createDepot(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody OrgDtos.DepotRequest req,
            HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orgManagementService.createDepot(principal, req, http));
    }

    @PutMapping("/depots/{id}")
    public ResponseEntity<OrgDtos.DepotResponse> updateDepot(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody OrgDtos.DepotRequest req,
            HttpServletRequest http) {
        return ResponseEntity.ok(orgManagementService.updateDepot(principal, id, req, http));
    }

    @PatchMapping("/depots/{id}/enabled")
    public ResponseEntity<OrgDtos.DepotResponse> toggleDepot(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestParam boolean enabled,
            HttpServletRequest http) {
        return ResponseEntity.ok(orgManagementService.toggleDepot(principal, id, enabled, http));
    }

    // ------------------------------------------------------------------
    // Town
    // ------------------------------------------------------------------

    @GetMapping("/towns")
    public ResponseEntity<List<OrgDtos.TownResponse>> listTowns(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam Long parentId) {
        return ResponseEntity.ok(orgManagementService.listTowns(principal, parentId));
    }

    @PostMapping("/towns")
    public ResponseEntity<OrgDtos.TownResponse> createTown(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody OrgDtos.TownRequest req,
            HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orgManagementService.createTown(principal, req, http));
    }

    @PutMapping("/towns/{id}")
    public ResponseEntity<OrgDtos.TownResponse> updateTown(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody OrgDtos.TownRequest req,
            HttpServletRequest http) {
        return ResponseEntity.ok(orgManagementService.updateTown(principal, id, req, http));
    }

    @PatchMapping("/towns/{id}/enabled")
    public ResponseEntity<OrgDtos.TownResponse> toggleTown(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestParam boolean enabled,
            HttpServletRequest http) {
        return ResponseEntity.ok(orgManagementService.toggleTown(principal, id, enabled, http));
    }
}
