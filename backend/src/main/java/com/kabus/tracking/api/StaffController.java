package com.kabus.tracking.api;

import com.kabus.tracking.security.UserPrincipal;
import com.kabus.tracking.service.StaffService;
import com.kabus.tracking.web.dto.StaffDtos;
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

/** Staff management, scoped to the caller's organizational scope. */
@RestController
@RequestMapping("/api/admin/staff")
public class StaffController {

    private final StaffService staffService;

    public StaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    @GetMapping
    public ResponseEntity<Page<StaffDtos.StaffResponse>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(staffService.list(principal, search, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<StaffDtos.StaffResponse> get(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        return ResponseEntity.ok(staffService.get(principal, id));
    }

    @PostMapping
    public ResponseEntity<StaffDtos.StaffResponse> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody StaffDtos.CreateStaffRequest req,
            HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED).body(staffService.create(principal, req, http));
    }

    @PutMapping("/{id}")
    public ResponseEntity<StaffDtos.StaffResponse> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody StaffDtos.UpdateStaffRequest req,
            HttpServletRequest http) {
        return ResponseEntity.ok(staffService.update(principal, id, req, http));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<StaffDtos.StaffResponse> setStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestParam String status,
            HttpServletRequest http) {
        return ResponseEntity.ok(staffService.setStatus(principal, id, status, http));
    }
}
