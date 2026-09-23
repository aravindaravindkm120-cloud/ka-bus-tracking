package com.kabus.tracking.api;

import com.kabus.tracking.security.UserPrincipal;
import com.kabus.tracking.service.RouteService;
import com.kabus.tracking.web.dto.PassengerDtos;
import com.kabus.tracking.web.dto.RouteDtos;
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

/** Route + route-stop management with OSRM geometry validation. */
@RestController
@RequestMapping("/api/admin/routes")
public class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @GetMapping
    public ResponseEntity<Page<RouteDtos.RouteResponse>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(routeService.list(principal, search, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RouteDtos.RouteResponse> get(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        return ResponseEntity.ok(routeService.get(principal, id));
    }

    @GetMapping("/{id}/geometry")
    public ResponseEntity<PassengerDtos.RouteGeometryDto> geometry(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        return ResponseEntity.ok(routeService.geometry(principal, id));
    }

    @PostMapping
    public ResponseEntity<RouteDtos.RouteResponse> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody RouteDtos.CreateRouteRequest req,
            HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED).body(routeService.create(principal, req, http));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RouteDtos.RouteResponse> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody RouteDtos.UpdateRouteRequest req,
            HttpServletRequest http) {
        return ResponseEntity.ok(routeService.update(principal, id, req, http));
    }

    @PutMapping("/{id}/stops")
    public ResponseEntity<RouteDtos.RouteResponse> replaceStops(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody RouteDtos.ReplaceStopsRequest req,
            HttpServletRequest http) {
        return ResponseEntity.ok(routeService.replaceStops(principal, id, req, http));
    }

    @PatchMapping("/{id}/enabled")
    public ResponseEntity<RouteDtos.RouteResponse> setEnabled(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestParam boolean enabled,
            HttpServletRequest http) {
        return ResponseEntity.ok(routeService.setEnabled(principal, id, enabled, http));
    }

    @PostMapping("/geometry/preview")
    public ResponseEntity<RouteDtos.PreviewResponse> preview(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody RouteDtos.PreviewRequest req) {
        return ResponseEntity.ok(routeService.preview(principal, req));
    }
}
