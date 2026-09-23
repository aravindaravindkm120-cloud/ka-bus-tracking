package com.kabus.tracking;

import com.kabus.tracking.support.LiveStatusPolicy;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the central live-status thresholds (LIVE <=60s, STALE <=10min, else OFFLINE).
 */
class LiveStatusPolicyTest {

    private LiveStatusPolicy policyWith(int live, int stale) {
        com.kabus.tracking.support.SettingsService settings =
                mock(com.kabus.tracking.support.SettingsService.class);
        when(settings.liveSeconds()).thenReturn(live);
        when(settings.staleSeconds()).thenReturn(stale);
        return new LiveStatusPolicy(settings);
    }

    @Test
    void thresholds_mapToExpectedStatuses() {
        LiveStatusPolicy p = policyWith(60, 600);
        assertEquals(com.kabus.tracking.domain.enums.LiveStatus.LIVE, p.statusForAge(0));
        assertEquals(com.kabus.tracking.domain.enums.LiveStatus.LIVE, p.statusForAge(60));
        assertEquals(com.kabus.tracking.domain.enums.LiveStatus.STALE, p.statusForAge(61));
        assertEquals(com.kabus.tracking.domain.enums.LiveStatus.STALE, p.statusForAge(600));
        assertEquals(com.kabus.tracking.domain.enums.LiveStatus.OFFLINE, p.statusForAge(601));
        assertEquals(com.kabus.tracking.domain.enums.LiveStatus.OFFLINE, p.statusForAge(3600));
    }

    @Test
    void nullCapture_isOffline() {
        LiveStatusPolicy p = policyWith(60, 600);
        assertEquals(com.kabus.tracking.domain.enums.LiveStatus.OFFLINE,
                p.statusFor(null, LocalDateTime.now()));
    }

    @Test
    void customThresholds_areHonored() {
        LiveStatusPolicy p = policyWith(120, 3600);
        assertEquals(com.kabus.tracking.domain.enums.LiveStatus.LIVE, p.statusForAge(100));
        assertEquals(com.kabus.tracking.domain.enums.LiveStatus.STALE, p.statusForAge(130));
        assertEquals(com.kabus.tracking.domain.enums.LiveStatus.OFFLINE, p.statusForAge(4000));
    }
}