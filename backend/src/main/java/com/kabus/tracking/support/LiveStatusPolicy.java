package com.kabus.tracking.support;

import com.kabus.tracking.domain.enums.LiveStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Central live-status thresholds.
 *
 * <p>LIVE   : last update within 60s.
 * STALE  : last update older than 60s and within 10 minutes.
 * OFFLINE: last update older than 10 minutes, or the GPS session ended.
 *
 * <p>Thresholds are configurable at runtime via system_settings and are the
 * single authority used by REST responses, WebSocket broadcasts and the
 * scheduled persistence job.</p>
 */
@Component
public class LiveStatusPolicy {

    private final SettingsService settings;

    public LiveStatusPolicy(SettingsService settings) {
        this.settings = settings;
    }

    public LiveStatus statusFor(LocalDateTime capturedAt, LocalDateTime now) {
        if (capturedAt == null || now == null) {
            return LiveStatus.OFFLINE;
        }
        long ageSeconds = java.time.Duration.between(capturedAt, now).getSeconds();
        return statusForAge(ageSeconds);
    }

    public LiveStatus statusForAge(long ageSeconds) {
        if (ageSeconds <= settings.liveSeconds()) {
            return LiveStatus.LIVE;
        }
        if (ageSeconds <= settings.staleSeconds()) {
            return LiveStatus.STALE;
        }
        return LiveStatus.OFFLINE;
    }

    public LiveStatus statusNow(LocalDateTime capturedAt) {
        return statusFor(capturedAt, LocalDateTime.now());
    }

    public int liveSeconds() {
        return settings.liveSeconds();
    }

    public int staleSeconds() {
        return settings.staleSeconds();
    }
}