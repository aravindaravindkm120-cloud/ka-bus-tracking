package com.kabus.tracking.api;

import com.kabus.tracking.domain.entity.Notification;
import com.kabus.tracking.security.UserPrincipal;
import com.kabus.tracking.service.AdminService;
import com.kabus.tracking.service.NotificationService;
import com.kabus.tracking.web.dto.AdminDtos;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Admin endpoints. Access is limited to the admin roles by SecurityConfig;
 * every data query is additionally constrained to the caller's server-side
 * organizational scope.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final NotificationService notificationService;

    public AdminController(AdminService adminService, NotificationService notificationService) {
        this.adminService = adminService;
        this.notificationService = notificationService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<AdminDtos.DashboardResponse> dashboard(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(adminService.dashboard(principal));
    }

    @GetMapping("/live-buses")
    public ResponseEntity<List<AdminDtos.LiveBusItem>> liveBuses(@AuthenticationPrincipal UserPrincipal principal,
                                                                 @RequestParam(defaultValue = "200") int limit) {
        return ResponseEntity.ok(adminService.liveBuses(principal, limit));
    }

    @GetMapping("/crew")
    public ResponseEntity<List<AdminDtos.CrewItem>> crew(@AuthenticationPrincipal UserPrincipal principal,
                                                         @RequestParam(defaultValue = "200") int limit) {
        return ResponseEntity.ok(adminService.crew(principal, limit));
    }

    @GetMapping("/notifications")
    public ResponseEntity<Page<Notification>> notifications(@AuthenticationPrincipal UserPrincipal principal,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(notificationService.forUser(
                principal.getUserId(), org.springframework.data.domain.PageRequest.of(
                        Math.max(page, 0), Math.min(Math.max(size, 1), 50))));
    }

    @PatchMapping("/notifications/{id}/read")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        notificationService.markRead(principal.getUserId(), id);
        return ResponseEntity.noContent().build();
    }
}