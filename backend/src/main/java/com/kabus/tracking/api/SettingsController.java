package com.kabus.tracking.api;

import com.kabus.tracking.security.UserPrincipal;
import com.kabus.tracking.service.SettingsAdminService;
import com.kabus.tracking.web.dto.SettingsDtos;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** SUPER_ADMIN runtime system settings. */
@RestController
@RequestMapping("/api/admin/settings")
public class SettingsController {

    private final SettingsAdminService settingsAdminService;

    public SettingsController(SettingsAdminService settingsAdminService) {
        this.settingsAdminService = settingsAdminService;
    }

    @GetMapping
    public ResponseEntity<List<SettingsDtos.SettingResponse>> list(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(settingsAdminService.list(principal));
    }

    @PutMapping("/{key}")
    public ResponseEntity<SettingsDtos.SettingResponse> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String key,
            @Valid @RequestBody SettingsDtos.UpdateSettingRequest req,
            HttpServletRequest http) {
        return ResponseEntity.ok(settingsAdminService.update(principal, key, req.value(), http));
    }
}
