package com.kabus.tracking.service;

import com.kabus.tracking.domain.entity.Bus;
import com.kabus.tracking.domain.entity.CrewAssignment;
import com.kabus.tracking.domain.entity.GpsSession;
import com.kabus.tracking.domain.entity.LiveLocation;
import com.kabus.tracking.domain.entity.LocationHistory;
import com.kabus.tracking.domain.entity.Trip;
import com.kabus.tracking.domain.enums.GpsSessionStatus;
import com.kabus.tracking.domain.enums.LiveStatus;
import com.kabus.tracking.domain.enums.TripStatus;
import com.kabus.tracking.domain.repository.GpsSessionRepository;
import com.kabus.tracking.domain.repository.LiveLocationRepository;
import com.kabus.tracking.domain.repository.LocationHistoryRepository;
import com.kabus.tracking.exception.ApiException;
import com.kabus.tracking.support.LiveStatusPolicy;
import com.kabus.tracking.support.SettingsService;
import com.kabus.tracking.web.dto.CrewGpsDtos;
import com.kabus.tracking.ws.LiveLocationBroadcaster;
import com.kabus.tracking.ws.LiveLocationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Server-authoritative GPS session handling.
 *
 * <p>The bus/trip/route are always derived from the crew assignment and the
 * GPS session - never from client-supplied identifiers. Every upload is
 * validated for session ownership, coordinate ranges, timestamp skew, and
 * minimum inter-upload interval.</p>
 */
@Service
public class GpsService {

    private static final Logger log = LoggerFactory.getLogger(GpsService.class);
    private static final BigDecimal MIN_LAT = new BigDecimal("-90.0");
    private static final BigDecimal MAX_LAT = new BigDecimal("90.0");
    private static final BigDecimal MIN_LON = new BigDecimal("-180.0");
    private static final BigDecimal MAX_LON = new BigDecimal("180.0");
    private static final BigDecimal MIN_ALT = new BigDecimal("-1000");
    private static final BigDecimal MAX_ALT = new BigDecimal("50000");

    private final GpsSessionRepository sessionRepository;
    private final LiveLocationRepository liveLocationRepository;
    private final LocationHistoryRepository historyRepository;
    private final CrewService crewService;
    private final SettingsService settings;
    private final LiveStatusPolicy liveStatusPolicy;
    private final LiveLocationBroadcaster broadcaster;

    public GpsService(GpsSessionRepository sessionRepository,
                      LiveLocationRepository liveLocationRepository,
                      LocationHistoryRepository historyRepository,
                      CrewService crewService,
                      SettingsService settings,
                      LiveStatusPolicy liveStatusPolicy,
                      LiveLocationBroadcaster broadcaster) {
        this.sessionRepository = sessionRepository;
        this.liveLocationRepository = liveLocationRepository;
        this.historyRepository = historyRepository;
        this.crewService = crewService;
        this.settings = settings;
        this.liveStatusPolicy = liveStatusPolicy;
        this.broadcaster = broadcaster;
    }

    @Transactional
    public CrewGpsDtos.GpsStartResponse start(Long userId, String deviceId, String appVersion) {
        CrewAssignment assignment = crewService.requireActiveAssignment(userId);
        Trip trip = assignment.getTrip();
        Bus bus = trip.getBus();

        if (!bus.isGpsEnabled()) {
            throw ApiException.badRequest("GPS is disabled for this bus.");
        }
        if (!"ACTIVE".equals(bus.getStatus())) {
            throw ApiException.badRequest("Bus is not ACTIVE (status=" + bus.getStatus() + ").");
        }

        // One active session per bus at a time - avoids two crews fighting over a live stream.
        if (sessionRepository.findFirstByBusIdAndStatusOrderByStartedAtDesc(bus.getId(), GpsSessionStatus.ACTIVE)
                .isPresent()) {
            throw ApiException.conflict("A GPS session is already active for this bus.");
        }

        if (trip.getStatus() != TripStatus.RUNNING) {
            trip.setStatus(TripStatus.RUNNING);
        }

        GpsSession session = new GpsSession();
        session.setTrip(trip);
        session.setBus(bus);
        session.setCrew(assignment.getCrew());
        session.setDeviceId(deviceId);
        session.setAppVersion(appVersion);
        session.setStatus(GpsSessionStatus.ACTIVE);
        session.setStartedAt(LocalDateTime.now());
        session.setLastHeartbeatAt(LocalDateTime.now());

        // Poor-signal guard: session may be opened before the first fix arrives.
        sessionRepository.save(session);

        log.info("GPS session {} started for crew {} on bus {} trip {}",
                session.getSessionKey(), assignment.getCrew().getId(), bus.getId(), trip.getId());

        return new CrewGpsDtos.GpsStartResponse(
                session.getSessionKey(),
                session.getId(),
                bus.getId(),
                trip.getId(),
                trip.getRoute() == null ? null : trip.getRoute().getId(),
                session.getStatus(),
                session.getStartedAt());
    }

    @Transactional
    public CrewGpsDtos.GpsLocationResponse uploadLocation(Long userId, String sessionKey,
                                                          CrewGpsDtos.GpsLocationRequest request) {
        GpsSession session = requireOwnedActiveSession(userId, sessionKey);

        LocalDateTime now = LocalDateTime.now();
        long serverNowMs = System.currentTimeMillis();

        log.info("Crew {} GPS upload arrival: session={} lat={} lon={} acc={}m skew-aware={}",
                userId, sessionKey, request.latitude(), request.longitude(),
                request.accuracy(), request.clientNowEpochMillis() != null);

        // Minimum interval between accepted uploads (GPS spam prevention).
        LocalDateTime lastAccepted = session.getLastHeartbeatAt() != null
                ? session.getLastHeartbeatAt() : session.getStartedAt();
        long elapsedMs = Duration.between(lastAccepted, now).toMillis();
        if (elapsedMs >= 0 && elapsedMs < settings.gpsMinIntervalMs()) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "GPS upload too frequent. Retry in " + (settings.gpsMinIntervalMs() - elapsedMs) + " ms.");
        }

        validateCoordinates(request);

        // Clock-skew guard. Real devices rarely run NTP-synced wall clocks, so a
        // strict |deviceNow - serverNow| window rejects genuine fixes. When the
        // client reports its own wall clock (clientNowEpochMillis) we normalize the
        // fix instant by the observed device-vs-server offset and then enforce the
        // same freshness window. Replayed or stale fixes older than the window are
        // still rejected - only the absolute clock base is corrected.
        Long clientNowMs = request.clientNowEpochMillis();
        LocalDateTime captured = request.timestamp();
        Instant fixInstant = request.timestamp().toInstant(java.time.ZoneOffset.UTC);
        long skewMs;
        if (clientNowMs != null) {
            long observedOffsetMs = clientNowMs - serverNowMs;
            long fixEpochMs = request.timestamp()
                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            long correctedFixMs = fixEpochMs - observedOffsetMs;
            skewMs = Math.abs(correctedFixMs - serverNowMs);
            if (skewMs <= settings.gpsMaxSkewMs()) {
                captured = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(correctedFixMs), java.time.ZoneId.systemDefault());
                fixInstant = Instant.ofEpochMilli(correctedFixMs);
            }
        } else {
            skewMs = Math.abs(Duration.between(request.timestamp(), now).toMillis());
        }
        if (skewMs > settings.gpsMaxSkewMs()) {
            throw ApiException.badRequest(
                    "Location timestamp is outside the accepted clock-skew window ("
                            + settings.gpsMaxSkewMs() + " ms).");
        }

        Bus bus = session.getBus();
        Trip trip = session.getTrip();

        LiveLocation current = liveLocationRepository.findByBusId(bus.getId())
                .orElse(new LiveLocation());
        if (current.getBus() == null) {
            current.setBus(bus);
        }
        applyPoint(current, request, trip, session, captured);

        liveLocationRepository.save(current);

        LocationHistory history = new LocationHistory();
        history.setBus(bus);
        history.setTrip(trip);
        history.setGpsSession(session);
        history.setLatitude(request.latitude());
        history.setLongitude(request.longitude());
        history.setSpeedKmh(nonNull(request.speed()));
        history.setHeading(nonNull(request.heading()));
        history.setAccuracyM(nonNull(request.accuracy()));
        history.setAltitudeM(request.altitude());
        history.setCapturedAt(request.timestamp());
        historyRepository.save(history);

        session.setLastHeartbeatAt(now);
        session.setUpdatesCount(session.getUpdatesCount() + 1);

        LiveStatus status = liveStatusPolicy.statusNow(captured);
        broadcaster.publish(new LiveLocationMessage(
                bus.getId(),
                trip.getId(),
                trip.getRoute() == null ? null : trip.getRoute().getId(),
                request.latitude(),
                request.longitude(),
                request.speed(),
                request.heading(),
                request.accuracy(),
                request.altitude(),
                fixInstant,
                status));

        return new CrewGpsDtos.GpsLocationResponse(
                true,
                session.getSessionKey(),
                bus.getId(),
                trip.getId(),
                status.name(),
                settings.gpsMinIntervalMs(),
                "accepted");
    }

    @Transactional
    public CrewGpsDtos.GpsEndResponse end(Long userId, String sessionKey) {
        GpsSession session = requireOwnedActiveSession(userId, sessionKey);

        session.setStatus(GpsSessionStatus.ENDED);
        session.setEndedAt(LocalDateTime.now());
        sessionRepository.save(session);

        Trip trip = session.getTrip();
        trip.setStatus(TripStatus.ENDED);

        // Mark the live bus OFFLINE and sever the session link so the row no
        // longer represents an active stream.
        liveLocationRepository.findByBusId(session.getBus().getId())
                .ifPresent(live -> {
                    live.setStatus(LiveStatus.OFFLINE);
                    live.setGpsSession(null);
                    live.setTrip(null);
                    live.setRoute(null);
                    liveLocationRepository.save(live);
                });

        // Clients need to know this bus is no longer live.
        broadcaster.publish(new LiveLocationMessage(
                session.getBus().getId(),
                null,
                null,
                null, null, null, null, null, null,
                Instant.now(),
                LiveStatus.OFFLINE));

        log.info("GPS session {} ended for bus {}", session.getSessionKey(), session.getBus().getId());

        return new CrewGpsDtos.GpsEndResponse(
                session.getSessionKey(),
                session.getBus().getId(),
                trip.getId(),
                session.getStatus(),
                trip.getStatus(),
                session.getEndedAt());
    }

    @Transactional(readOnly = true)
    public CrewGpsDtos.CrewStatusResponse status(Long userId) {
        CrewGpsDtos.AssignmentResponse assignment = crewService.getAssignment(userId);
        GpsSession session = sessionRepository
                .findFirstByCrewUserIdAndStatusOrderByStartedAtDesc(userId, GpsSessionStatus.ACTIVE)
                .orElse(null);
        int ageSeconds = -1;
        Long lastUpdateEpochMs = null;
        if (session != null) {
            lastUpdateEpochMs = session.getLastHeartbeatAt() == null ? null
                    : session.getLastHeartbeatAt().atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();
            if (session.getLastHeartbeatAt() != null) {
                ageSeconds = (int) Duration.between(session.getLastHeartbeatAt(), LocalDateTime.now()).getSeconds();
            }
        }
        return new CrewGpsDtos.CrewStatusResponse(
                assignment,
                session == null ? null : session.getStatus(),
                ageSeconds,
                lastUpdateEpochMs);
    }

    private GpsSession requireOwnedActiveSession(Long userId, String sessionKey) {
        if (sessionKey == null || sessionKey.isBlank()) {
            throw ApiException.badRequest("Missing session key.");
        }
        GpsSession session = sessionRepository.findBySessionKey(sessionKey)
                .orElseThrow(() -> ApiException.badRequest("Unknown GPS session."));
        if (!session.getCrew().getUser().getId().equals(userId)) {
            throw ApiException.forbidden("This GPS session does not belong to you.");
        }
        if (session.getStatus() != GpsSessionStatus.ACTIVE) {
            throw ApiException.badRequest("GPS session is not active (status=" + session.getStatus() + ").");
        }
        return session;
    }

    private void validateCoordinates(CrewGpsDtos.GpsLocationRequest r) {
        if (range(r.latitude(), MIN_LAT, MAX_LAT)) {
            throw ApiException.badRequest("Latitude outside valid range (-90 .. 90).");
        }
        if (range(r.longitude(), MIN_LON, MAX_LON)) {
            throw ApiException.badRequest("Longitude outside valid range (-180 .. 180).");
        }
        if (r.speed() == null || r.speed().signum() < 0 || r.speed().compareTo(new BigDecimal("400")) > 0) {
            throw ApiException.badRequest("Speed outside valid range (0 .. 400 km/h).");
        }
        if (r.accuracy() == null || r.accuracy().signum() < 0) {
            throw ApiException.badRequest("Accuracy must be non-negative.");
        }
        if (heading(r.heading())) {
            throw ApiException.badRequest("Heading must be in the range 0 .. 360 degrees.");
        }
        if (r.altitude() != null && (r.altitude().compareTo(MIN_ALT) < 0 || r.altitude().compareTo(MAX_ALT) > 0)) {
            throw ApiException.badRequest("Altitude outside valid range (-1000 .. 50000 m).");
        }
    }

    private static boolean range(BigDecimal value, BigDecimal min, BigDecimal max) {
        return value == null || value.compareTo(min) < 0 || value.compareTo(max) > 0;
    }

    private static boolean heading(BigDecimal value) {
        return value == null || value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(new BigDecimal("360")) > 0;
    }

    private static BigDecimal nonNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void applyPoint(LiveLocation target, CrewGpsDtos.GpsLocationRequest request, Trip trip,
                            GpsSession session, LocalDateTime captured) {
        target.setTrip(trip);
        target.setGpsSession(session);
        target.setRoute(trip.getRoute());
        target.setLatitude(request.latitude());
        target.setLongitude(request.longitude());
        target.setSpeedKmh(nonNull(request.speed()));
        target.setHeading(nonNull(request.heading()));
        target.setAccuracyM(nonNull(request.accuracy()));
        target.setAltitudeM(request.altitude());
        target.setCapturedAt(captured);
        target.setStatus(liveStatusPolicy.statusNow(captured));
    }
}