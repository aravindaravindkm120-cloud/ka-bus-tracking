package com.kabus.tracking.api;

import com.kabus.tracking.domain.enums.TripStatus;
import com.kabus.tracking.security.UserPrincipal;
import com.kabus.tracking.service.TripService;
import com.kabus.tracking.web.dto.TripDtos;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;

/** Trip scheduling and bus/crew assignment. */
@RestController
@RequestMapping("/api/admin/trips")
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    @GetMapping
    public ResponseEntity<Page<TripDtos.TripResponse>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) TripStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(tripService.list(principal, date, status, search, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TripDtos.TripResponse> get(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        return ResponseEntity.ok(tripService.get(principal, id));
    }

    @PostMapping
    public ResponseEntity<TripDtos.TripResponse> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody TripDtos.CreateTripRequest req,
            HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tripService.create(principal, req, http));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TripDtos.TripResponse> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody TripDtos.UpdateTripRequest req,
            HttpServletRequest http) {
        return ResponseEntity.ok(tripService.update(principal, id, req, http));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<TripDtos.TripResponse> setStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestParam String status,
            HttpServletRequest http) {
        return ResponseEntity.ok(tripService.setStatus(principal, id, status, http));
    }

    @PutMapping("/{id}/bus")
    public ResponseEntity<TripDtos.TripResponse> assignBus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody TripDtos.AssignBusRequest req,
            HttpServletRequest http) {
        return ResponseEntity.ok(tripService.assignBus(principal, id, req, http));
    }

    @DeleteMapping("/{id}/bus")
    public ResponseEntity<TripDtos.TripResponse> unassignBus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            HttpServletRequest http) {
        return ResponseEntity.ok(tripService.unassignBus(principal, id, http));
    }

    @PostMapping("/{id}/crew")
    public ResponseEntity<TripDtos.TripResponse> assignCrew(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody TripDtos.AssignCrewRequest req,
            HttpServletRequest http) {
        return ResponseEntity.ok(tripService.assignCrew(principal, id, req, http));
    }

    @DeleteMapping("/{id}/crew/{crewId}")
    public ResponseEntity<TripDtos.TripResponse> unassignCrew(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @PathVariable Long crewId,
            HttpServletRequest http) {
        return ResponseEntity.ok(tripService.unassignCrew(principal, id, crewId, http));
    }
}
