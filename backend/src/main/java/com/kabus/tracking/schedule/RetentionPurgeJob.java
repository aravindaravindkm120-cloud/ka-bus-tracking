package com.kabus.tracking.schedule;

import com.kabus.tracking.domain.repository.LocationHistoryRepository;
import com.kabus.tracking.support.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Enforces the location_history retention policy.
 *
 * <p>Raw GPS history is pruned to the configured window
 * (location_history.retention_days, default 30). The policy is runtime
 * configurable and can be disabled entirely - see docs/DATA_RETENTION.md.
 * Live state in live_locations is never pruned by this job.</p>
 */
@Component
public class RetentionPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(RetentionPurgeJob.class);

    private final LocationHistoryRepository historyRepository;
    private final SettingsService settings;

    public RetentionPurgeJob(LocationHistoryRepository historyRepository, SettingsService settings) {
        this.historyRepository = historyRepository;
        this.settings = settings;
    }

    @Scheduled(cron = "${app.retention.purge-cron:0 30 3 * * *}")
    @Transactional
    public void purgeLocationHistory() {
        if (!settings.locationHistoryPurgeEnabled()) {
            return;
        }
        int days = Math.max(0, settings.locationHistoryRetentionDays());
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        int removed = historyRepository.purgeOlderThan(cutoff);
        if (removed > 0) {
            log.info("Purged {} location_history rows older than {} days ({})", removed, days, cutoff);
        }
    }
}