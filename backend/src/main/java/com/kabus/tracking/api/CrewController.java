package com.kabus.tracking.api;

import com.kabus.tracking.security.UserPrincipal;
import com.kabus.tracking.service.AdService;
import com.kabus.tracking.service.CrewService;
import com.kabus.tracking.service.GpsService;
import com.kabus.tracking.service.NotificationService;
import com.kabus.tracking.web.dto.AdDtos;
import com.kabus.tracking.web.dto.CrewGpsDtos;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Crew endpoints (DRIVER / CONDUCTOR only). The server always derives the
 * bus, trip and route from the crew's active assignment - never from input.
 */
@RestController
@RequestMapping("/api/crew")
public class CrewController {

    private final CrewService crewService;
    private final GpsService gpsService;
    private final NotificationService notificationService;
    private final AdService adService;

    public CrewController(CrewService crewService,
                          GpsService gpsService,
                          NotificationService notificationService,
                          AdService adService) {
        this.crewService = crewService;
        this.gpsService = gpsService;
        this.notificationService = notificationService;
        this.adService = adService;
    }

    @GetMapping("/assignment")
    public ResponseEntity<CrewGpsDtos.AssignmentResponse> assignment(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(crewService.getAssignment(principal.getUserId()));
    }

    @PostMapping("/gps/start")
    public ResponseEntity<CrewGpsDtos.GpsStartResponse> startGps(@AuthenticationPrincipal UserPrincipal principal,
                                                                 @RequestBody(required = false) CrewGpsDtos.GpsStartRequest request,
                                                                 HttpServletRequest http) {
        String deviceId = request != null ? request.deviceId() : null;
        String appVersion = request != null ? request.appVersion() : null;
        return ResponseEntity.ok(gpsService.start(principal.getUserId(), deviceId, appVersion));
    }

    @PostMapping("/gps/location")
    public ResponseEntity<CrewGpsDtos.GpsLocationResponse> location(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String sessionKey,
            @Valid @RequestBody CrewGpsDtos.GpsLocationRequest request) {
        return ResponseEntity.ok(gpsService.uploadLocation(principal.getUserId(), sessionKey, request));
    }

    @PostMapping("/gps/end")
    public ResponseEntity<CrewGpsDtos.GpsEndResponse> endGps(@AuthenticationPrincipal UserPrincipal principal,
                                                             @RequestParam String sessionKey) {
        return ResponseEntity.ok(gpsService.end(principal.getUserId(), sessionKey));
    }

    @GetMapping("/status")
    public ResponseEntity<CrewGpsDtos.CrewStatusResponse> status(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(gpsService.status(principal.getUserId()));
    }

    @GetMapping("/notifications")
    public ResponseEntity<Page<com.kabus.tracking.domain.entity.Notification>> notifications(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(notificationService.forUser(
                principal.getUserId(), PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50))));
    }

    @GetMapping("/ad")
    public ResponseEntity<AdDtos.ServeAdResponse> ad() {
        // CREW_APP_HOME only - never shown over GPS/Start/End controls; the
        // Android app enforces the non-overlap, this endpoint serves the slot.
        return ResponseEntity.ok(adService.serve("CREW_APP_HOME"));
    }
}