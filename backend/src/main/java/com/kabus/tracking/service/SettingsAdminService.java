package com.kabus.tracking.service;

import com.kabus.tracking.config.AppProperties;
import com.kabus.tracking.domain.entity.SystemSetting;
import com.kabus.tracking.domain.entity.User;
import com.kabus.tracking.domain.repository.SystemSettingRepository;
import com.kabus.tracking.domain.repository.UserRepository;
import com.kabus.tracking.exception.ApiException;
import com.kabus.tracking.security.UserPrincipal;
import com.kabus.tracking.support.SettingsService;
import com.kabus.tracking.web.dto.SettingsDtos;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime system-settings administration. Values live in
 * <code>system_settings</code> and are read through {@link SettingsService};
 * writing a value evicts its cache so the change takes effect immediately.
 * Restricted to SUPER_ADMIN.
 */
@Service
public class SettingsAdminService {

    private record SettingSpec(String key, String type, String description) {
    }

    private static final Map<String, SettingSpec> SPECS = new LinkedHashMap<>();

    static {
        SPECS.put("live.threshold.live_seconds", new SettingSpec(
                "live.threshold.live_seconds", "int", "A bus reporting within this many seconds is LIVE."));
        SPECS.put("live.threshold.stale_seconds", new SettingSpec(
                "live.threshold.stale_seconds", "int", "A bus reporting within this many seconds is STALE."));
        SPECS.put("gps.location.min_interval_ms", new SettingSpec(
                "gps.location.min_interval_ms", "int", "Minimum accepted interval between GPS fixes (ms)."));
        SPECS.put("gps.location.timestamp_max_skew_ms", new SettingSpec(
                "gps.location.timestamp_max_skew_ms", "int", "Maximum accepted GPS timestamp skew (ms)."));
        SPECS.put("location_history.retention_days", new SettingSpec(
                "location_history.retention_days", "int", "Days of location history to retain."));
        SPECS.put("location_history.purge_enabled", new SettingSpec(
                "location_history.purge_enabled", "bool", "Whether automatic location-history purge runs."));
        SPECS.put("ads.default.duration_seconds", new SettingSpec(
                "ads.default.duration_seconds", "int", "Default ad display duration (seconds)."));
        SPECS.put("ads.default.frequency_seconds", new SettingSpec(
                "ads.default.frequency_seconds", "int", "Default ad display frequency (seconds)."));
    }

    private final SystemSettingRepository systemSettingRepository;
    private final SettingsService settingsService;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final AppProperties props;

    public SettingsAdminService(SystemSettingRepository systemSettingRepository,
                                SettingsService settingsService,
                                UserRepository userRepository,
                                AuditService auditService,
                                AppProperties props) {
        this.systemSettingRepository = systemSettingRepository;
        this.settingsService = settingsService;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public List<SettingsDtos.SettingResponse> list(UserPrincipal principal) {
        requireSuperAdmin(principal);
        Map<String, SystemSetting> stored = new LinkedHashMap<>();
        for (SystemSetting setting : systemSettingRepository.findAll()) {
            stored.put(setting.getSettingKey(), setting);
        }

        List<SettingsDtos.SettingResponse> out = new ArrayList<>();
        for (SettingSpec spec : SPECS.values()) {
            SystemSetting row = stored.remove(spec.key());
            String value = row != null ? row.getSettingValue() : defaultFor(spec.key());
            out.add(toResponse(spec.key(), value, spec.type(), spec.description(), defaultFor(spec.key()), row));
        }
        // Surface any additional keys that exist in the DB but are not in the registry.
        for (SystemSetting row : stored.values()) {
            out.add(toResponse(row.getSettingKey(), row.getSettingValue(), "string",
                    row.getDescription(), null, row));
        }
        return out;
    }

    @Transactional
    public SettingsDtos.SettingResponse update(UserPrincipal principal, String key, String value,
                                               HttpServletRequest http) {
        requireSuperAdmin(principal);
        SettingSpec spec = SPECS.get(key);
        if (spec == null) {
            throw ApiException.badRequest("Unknown setting: " + key);
        }
        String normalized = validate(spec, value);

        User actor = userRepository.findById(principal.getUserId()).orElse(null);
        settingsService.update(key, normalized, actor);

        auditService.record(principal.getUserId(), "SETTING_UPDATE", "SETTING", null,
                Map.of("key", key, "value", normalized),
                http == null ? null : http.getRemoteAddr(),
                http == null ? null : http.getHeader("User-Agent"));

        return new SettingsDtos.SettingResponse(key, normalized, spec.type(), spec.description(),
                defaultFor(key), actor == null ? null : actor.getId(),
                actor == null ? null : actor.getUsername(), LocalDateTime.now());
    }

    private String validate(SettingSpec spec, String value) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest("Value must not be blank.");
        }
        String trimmed = value.trim();
        switch (spec.type()) {
            case "int" -> {
                try {
                    int parsed = Integer.parseInt(trimmed);
                    if (parsed < 0) {
                        throw ApiException.badRequest("Value must be zero or positive.");
                    }
                } catch (NumberFormatException e) {
                    throw ApiException.badRequest("Value must be an integer.");
                }
            }
            case "bool" -> {
                if (!trimmed.equalsIgnoreCase("true") && !trimmed.equalsIgnoreCase("false")) {
                    throw ApiException.badRequest("Value must be true or false.");
                }
                trimmed = trimmed.toLowerCase();
            }
            default -> {
            }
        }
        return trimmed;
    }

    private String defaultFor(String key) {
        return switch (key) {
            case "live.threshold.live_seconds" -> String.valueOf(props.getLiveStatus().getLiveSeconds());
            case "live.threshold.stale_seconds" -> String.valueOf(props.getLiveStatus().getStaleSeconds());
            case "gps.location.min_interval_ms" -> String.valueOf((long) props.getGps().getMinIntervalMs());
            case "gps.location.timestamp_max_skew_ms" -> String.valueOf((long) props.getGps().getTimestampMaxSkewMs());
            case "location_history.retention_days" -> String.valueOf(props.getRetention().getLocationHistoryDays());
            case "location_history.purge_enabled" -> "true";
            case "ads.default.duration_seconds" -> "5";
            case "ads.default.frequency_seconds" -> "180";
            default -> null;
        };
    }

    private SettingsDtos.SettingResponse toResponse(String key, String value, String type, String description,
                                                    String defaultValue, SystemSetting row) {
        return new SettingsDtos.SettingResponse(
                key,
                value,
                type,
                description,
                defaultValue,
                row == null || row.getUpdatedBy() == null ? null : row.getUpdatedBy().getId(),
                row == null || row.getUpdatedBy() == null ? null : row.getUpdatedBy().getUsername(),
                row == null ? null : row.getUpdatedAt());
    }

    private void requireSuperAdmin(UserPrincipal principal) {
        if (principal == null || !principal.isSuperAdmin()) {
            throw ApiException.forbidden("Only SUPER_ADMIN may manage settings.");
        }
    }
}
