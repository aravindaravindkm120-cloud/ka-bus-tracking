package com.kabus.tracking.api;

import com.kabus.tracking.security.UserPrincipal;
import com.kabus.tracking.service.AdminUserService;
import com.kabus.tracking.web.dto.AdminUserDtos;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
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

/**
 * SUPER_ADMIN admin-user and role management. Service-level guards reject any
 * non-SUPER_ADMIN caller with 403 regardless of route-level security.
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ResponseEntity<Page<AdminUserDtos.UserResponse>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminUserService.list(principal, search, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminUserDtos.UserResponse> get(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        return ResponseEntity.ok(adminUserService.get(principal, id));
    }

    @PostMapping
    public ResponseEntity<AdminUserDtos.UserResponse> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AdminUserDtos.CreateUserRequest req,
            HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminUserService.create(principal, req, http));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminUserDtos.UserResponse> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody AdminUserDtos.UpdateUserRequest req,
            HttpServletRequest http) {
        return ResponseEntity.ok(adminUserService.update(principal, id, req, http));
    }

    @PatchMapping("/{id}/enabled")
    public ResponseEntity<AdminUserDtos.UserResponse> setEnabled(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestParam boolean enabled,
            HttpServletRequest http) {
        return ResponseEntity.ok(adminUserService.setEnabled(principal, id, enabled, http));
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<AdminUserDtos.UserResponse> changeRole(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody AdminUserDtos.ChangeRoleRequest req,
            HttpServletRequest http) {
        return ResponseEntity.ok(adminUserService.changeRole(principal, id, req, http));
    }

    @PatchMapping("/{id}/password")
    public ResponseEntity<AdminUserDtos.UserResponse> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody AdminUserDtos.ChangePasswordRequest req,
            HttpServletRequest http) {
        return ResponseEntity.ok(adminUserService.changePassword(principal, id, req, http));
    }
}
