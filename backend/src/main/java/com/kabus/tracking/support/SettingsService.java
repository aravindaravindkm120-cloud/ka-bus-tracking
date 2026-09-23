package com.kabus.tracking.support;

import com.kabus.tracking.config.AppProperties;
import com.kabus.tracking.domain.entity.SystemSetting;
import com.kabus.tracking.domain.entity.User;
import com.kabus.tracking.domain.repository.SystemSettingRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Central configuration stored in <code>system_settings</code>.
 *
 * <p>This is the single source of truth for live-status thresholds, GPS
 * acceptance rules and retention policy. Values fall back to sensible
 * defaults and can be overridden at runtime in the database.</p>
 */
@Service
public class SettingsService {

    private final SystemSettingRepository settingsRepository;
    private final AppProperties props;

    public SettingsService(SystemSettingRepository settingsRepository, AppProperties props) {
        this.settingsRepository = settingsRepository;
        this.props = props;
    }

    @Cacheable(cacheNames = "settings", key = "#key")
    public String get(String key, String fallback) {
        Optional<SystemSetting> setting = settingsRepository.findBySettingKey(key);
        return setting.map(SystemSetting::getSettingValue).orElse(fallback);
    }

    public int getInt(String key, int fallback) {
        try {
            return Integer.parseInt(get(key, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public boolean getBool(String key, boolean fallback) {
        return Boolean.parseBoolean(get(key, String.valueOf(fallback)));
    }

    /** A bus whose last update is at most this many seconds ago is LIVE. */
    public int liveSeconds() {
        return getInt("live.threshold.live_seconds", props.getLiveStatus().getLiveSeconds());
    }

    /** A bus whose last update is at most this many seconds ago is STALE. Older = OFFLINE. */
    public int staleSeconds() {
        return getInt("live.threshold.stale_seconds", props.getLiveStatus().getStaleSeconds());
    }

    public int gpsMinIntervalMs() {
        return getInt("gps.location.min_interval_ms", (int) props.getGps().getMinIntervalMs());
    }

    public int gpsMaxSkewMs() {
        return getInt("gps.location.timestamp_max_skew_ms", (int) props.getGps().getTimestampMaxSkewMs());
    }

    public int locationHistoryRetentionDays() {
        return getInt("location_history.retention_days", props.getRetention().getLocationHistoryDays());
    }

    public boolean locationHistoryPurgeEnabled() {
        return getBool("location_history.purge_enabled", true);
    }

    public int adDefaultDurationSeconds() {
        return getInt("ads.default.duration_seconds", 5);
    }

    public int adDefaultFrequencySeconds() {
        return getInt("ads.default.frequency_seconds", 180);
    }

    /**
     * Minimum minutes a passenger needs between the arrival of one connecting
     * leg and the departure of the next. Overridable at runtime via the
     * {@code search.min_transfer_minutes} system setting (env default
     * SEARCH_MIN_TRANSFER_MINUTES). Only enforced when scheduled trip data is
     * actually available for both legs.
     */
    public int searchMinTransferMinutes() {
        return getInt("search.min_transfer_minutes", props.getSearch().getMinTransferMinutes());
    }

    /** Upsert a setting and evict its cached value so the change is immediate. */
    @CacheEvict(cacheNames = "settings", key = "#key")
    @Transactional
    public void update(String key, String value, User updatedBy) {
        SystemSetting setting = settingsRepository.findBySettingKey(key).orElseGet(() -> {
            SystemSetting created = new SystemSetting();
            created.setSettingKey(key);
            return created;
        });
        setting.setSettingValue(value);
        setting.setUpdatedBy(updatedBy);
        settingsRepository.save(setting);
    }
}