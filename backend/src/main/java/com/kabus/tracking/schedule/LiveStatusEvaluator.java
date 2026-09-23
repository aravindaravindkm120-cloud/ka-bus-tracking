package com.kabus.tracking.schedule;

import com.kabus.tracking.domain.entity.GpsSession;
import com.kabus.tracking.domain.entity.LiveLocation;
import com.kabus.tracking.domain.enums.GpsSessionStatus;
import com.kabus.tracking.domain.enums.LiveStatus;
import com.kabus.tracking.domain.repository.GpsSessionRepository;
import com.kabus.tracking.domain.repository.LiveLocationRepository;
import com.kabus.tracking.support.LiveStatusPolicy;
import com.kabus.tracking.ws.LiveLocationBroadcaster;
import com.kabus.tracking.ws.LiveLocationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Central live-status evaluator.
 *
 * <p>Periodically persists LIVE -> STALE -> OFFLINE transitions in the
 * live_locations table (keeping the status column index-friendly) and pushes
 * status-change messages to WebSocket clients. The authoritative thresholds
 * live in system_settings (see SettingsService / LiveStatusPolicy).</p>
 */
@Component
public class LiveStatusEvaluator {

    private static final Logger log = LoggerFactory.getLogger(LiveStatusEvaluator.class);

    private final LiveLocationRepository liveLocationRepository;
    private final GpsSessionRepository gpsSessionRepository;
    private final LiveStatusPolicy liveStatusPolicy;
    private final LiveLocationBroadcaster broadcaster;

    public LiveStatusEvaluator(LiveLocationRepository liveLocationRepository,
                               GpsSessionRepository gpsSessionRepository,
                               LiveStatusPolicy liveStatusPolicy,
                               LiveLocationBroadcaster broadcaster) {
        this.liveLocationRepository = liveLocationRepository;
        this.gpsSessionRepository = gpsSessionRepository;
        this.liveStatusPolicy = liveStatusPolicy;
        this.broadcaster = broadcaster;
    }

    @Scheduled(fixedDelayString = "${app.live-status.evaluator-interval-ms:30000}",
            initialDelayString = "${app.live-status.evaluator-initial-delay-ms:15000}")
    @Transactional
    public void evaluate() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleCutoff = now.minusSeconds(liveStatusPolicy.liveSeconds());
        LocalDateTime offlineCutoff = now.minusSeconds(liveStatusPolicy.staleSeconds());

        Set<Long> affected = new HashSet<>();
        affected.addAll(liveLocationRepository.findBusIdsByStatusAndCapturedBefore(
                LiveStatus.LIVE, staleCutoff));
        affected.addAll(liveLocationRepository.findBusIdsByStatusAndCapturedBefore(
                LiveStatus.STALE, offlineCutoff));

        int staleUpdated = liveLocationRepository.markStale(staleCutoff);
        int offlineUpdated = liveLocationRepository.markOffline(offlineCutoff);

        if (!affected.isEmpty()) {
            broadcastStatusChange(affected);
        }
        if (staleUpdated > 0 || offlineUpdated > 0) {
            log.info("Live status sweep: {} -> STALE, {} -> OFFLINE", staleUpdated, offlineUpdated);
        }

        expireSilentSessions(now);
    }

    private void broadcastStatusChange(Set<Long> busIds) {
        List<LiveLocation> rows = liveLocationRepository.findByBusIdIn(busIds);
        for (LiveLocation l : rows) {
            LiveStatus status = liveStatusPolicy.statusNow(l.getCapturedAt());
            broadcaster.publish(new LiveLocationMessage(
                    l.getBus().getId(),
                    l.getTrip() == null ? null : l.getTrip().getId(),
                    l.getRoute() == null ? null : l.getRoute().getId(),
                    l.getLatitude(), l.getLongitude(),
                    l.getSpeedKmh(), l.getHeading(), l.getAccuracyM(), l.getAltitudeM(),
                    l.getCapturedAt().toInstant(java.time.ZoneOffset.UTC),
                    status));
        }
    }

    /**
     * Sessions with no heartbeat for a long time are force-expired and their
     * live rows are marked OFFLINE. This covers crashed apps, lost signal and
     * battery-killed foreground services.
     */
    private void expireSilentSessions(LocalDateTime now) {
        LocalDateTime heartbeatCutoff = now.minusSeconds(
                Math.max(liveStatusPolicy.staleSeconds(), 300));
        List<GpsSession> silent = gpsSessionRepository.findExpiredSessions(
                GpsSessionStatus.ACTIVE, heartbeatCutoff, now.minusMinutes(30));
        if (silent.isEmpty()) {
            return;
        }
        for (GpsSession session : silent) {
            session.setStatus(GpsSessionStatus.EXPIRED);
            session.setEndedAt(now);
            gpsSessionRepository.save(session);

            liveLocationRepository.findByBusId(session.getBus().getId())
                    .ifPresent(live -> {
                        live.setStatus(LiveStatus.OFFLINE);
                        live.setGpsSession(null);
                        liveLocationRepository.save(live);
                        broadcaster.publish(new LiveLocationMessage(
                                session.getBus().getId(), null, null,
                                live.getLatitude(), live.getLongitude(),
                                live.getSpeedKmh(), live.getHeading(), live.getAccuracyM(), live.getAltitudeM(),
                                Instant.now(), LiveStatus.OFFLINE));
                    });
            log.info("Expired silent GPS session {} for bus {}", session.getSessionKey(), session.getBus().getId());
        }
    }
}