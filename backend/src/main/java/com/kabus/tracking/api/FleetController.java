package com.kabus.tracking.api;

import com.kabus.tracking.security.UserPrincipal;
import com.kabus.tracking.service.FleetService;
import com.kabus.tracking.web.dto.FleetDtos;
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

/** Fleet (bus) management, scoped to the caller's organizational scope. */
@RestController
@RequestMapping("/api/admin/fleet/buses")
public class FleetController {

    private final FleetService fleetService;

    public FleetController(FleetService fleetService) {
        this.fleetService = fleetService;
    }

    @GetMapping
    public ResponseEntity<Page<FleetDtos.BusResponse>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(fleetService.list(principal, search, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FleetDtos.BusResponse> get(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        return ResponseEntity.ok(fleetService.get(principal, id));
    }

    @PostMapping
    public ResponseEntity<FleetDtos.BusResponse> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody FleetDtos.CreateBusRequest req,
            HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fleetService.create(principal, req, http));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FleetDtos.BusResponse> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody FleetDtos.UpdateBusRequest req,
            HttpServletRequest http) {
        return ResponseEntity.ok(fleetService.update(principal, id, req, http));
    }

    @PatchMapping("/{id}/enabled")
    public ResponseEntity<FleetDtos.BusResponse> setEnabled(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestParam boolean enabled,
            HttpServletRequest http) {
        return ResponseEntity.ok(fleetService.setEnabled(principal, id, enabled, http));
    }
}
