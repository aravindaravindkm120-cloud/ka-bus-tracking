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
import org.springframework.web.bind.annotation.DeleteMapping;
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

/**
 * The bus number master, scoped to the caller's organizational scope.
 *
 * <p>Kept on its own controller because it is a different resource from the
 * vehicle fleet below, even though both are administered from the Fleet screen.</p>
 */
@RestController
@RequestMapping("/api/admin/fleet/bus-numbers")
public class BusNumberController {

    private final FleetService fleetService;

    public BusNumberController(FleetService fleetService) {
        this.fleetService = fleetService;
    }

    @GetMapping
    public ResponseEntity<Page<FleetDtos.BusNumberResponse>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(fleetService.listBusNumbers(principal, search, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FleetDtos.BusNumberResponse> get(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        return ResponseEntity.ok(fleetService.getBusNumber(principal, id));
    }

    /** Enabled bus numbers for one depot, for the fleet/trip assignment dropdowns. */
    @GetMapping("/by-depot/{depotId}")
    public ResponseEntity<List<FleetDtos.BusNumberResponse>> listForDepot(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long depotId) {
        return ResponseEntity.ok(fleetService.listForDepot(principal, depotId));
    }

    @PostMapping
    public ResponseEntity<FleetDtos.BusNumberResponse> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody FleetDtos.CreateBusNumberRequest req,
            HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(fleetService.createBusNumber(principal, req, http));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FleetDtos.BusNumberResponse> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody FleetDtos.UpdateBusNumberRequest req,
            HttpServletRequest http) {
        return ResponseEntity.ok(fleetService.updateBusNumber(principal, id, req, http));
    }

    @PatchMapping("/{id}/enabled")
    public ResponseEntity<FleetDtos.BusNumberResponse> setEnabled(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestParam boolean enabled,
            HttpServletRequest http) {
        return ResponseEntity.ok(fleetService.setBusNumberEnabled(principal, id, enabled, http));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            HttpServletRequest http) {
        fleetService.deleteBusNumber(principal, id, http);
        return ResponseEntity.noContent().build();
    }
}
