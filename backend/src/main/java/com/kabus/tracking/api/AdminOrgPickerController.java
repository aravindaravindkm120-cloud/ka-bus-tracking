package com.kabus.tracking.api;

import com.kabus.tracking.security.UserPrincipal;
import com.kabus.tracking.service.OrgPickerService;
import com.kabus.tracking.web.dto.OrgPickerDtos;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only organization lookups that feed the cascading Corporation -&gt;
 * Division -&gt; Depot -&gt; Town pickers in the admin app.
 *
 * <p>Kept apart from {@link AdminOrganizationController} on purpose: that
 * controller is the organization management screen (SUPER_ADMIN-only writes),
 * whereas these endpoints exist purely so any scoped admin role can resolve the
 * names of the organizational levels it is allowed to assign. Results are always
 * clamped to the caller's server-resolved scope by
 * {@link OrgPickerService}.</p>
 */
@RestController
@RequestMapping("/api/admin/org-picker")
public class AdminOrgPickerController {

    private final OrgPickerService orgPickerService;

    public AdminOrgPickerController(OrgPickerService orgPickerService) {
        this.orgPickerService = orgPickerService;
    }

    @GetMapping("/corporations")
    public ResponseEntity<List<OrgPickerDtos.CorporationOption>> corporations(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(orgPickerService.corporations(principal));
    }

    @GetMapping("/divisions")
    public ResponseEntity<List<OrgPickerDtos.DivisionOption>> divisions(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) Long parentId) {
        return ResponseEntity.ok(orgPickerService.divisions(principal, parentId));
    }

    @GetMapping("/depots")
    public ResponseEntity<List<OrgPickerDtos.DepotOption>> depots(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) Long parentId) {
        return ResponseEntity.ok(orgPickerService.depots(principal, parentId));
    }

    @GetMapping("/towns")
    public ResponseEntity<List<OrgPickerDtos.TownOption>> towns(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) Long parentId) {
        return ResponseEntity.ok(orgPickerService.towns(principal, parentId));
    }
}