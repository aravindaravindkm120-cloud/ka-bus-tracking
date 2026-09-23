package com.kabus.tracking.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.kabus.tracking.config.AppProperties;
import com.kabus.tracking.domain.entity.Route;
import com.kabus.tracking.domain.entity.RouteStop;
import com.kabus.tracking.domain.repository.RouteRepository;
import com.kabus.tracking.domain.repository.RouteStopRepository;
import com.kabus.tracking.exception.ApiException;
import com.kabus.tracking.support.Geo;
import com.kabus.tracking.web.dto.PassengerDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central route-geometry service.
 *
 * The geometry for a route depends only on its ordered stops, so the cache key
 * is a fingerprint of those stops ("stops version"). While an OSRM request is
 * in flight, identical requests for the same stops are de-duplicated onto the
 * same in-flight future, so 1,000 passengers view the same route with a single
 * OSRM request. Successful geometry is retained so a later OSRM outage keeps
 * serving the previously calculated path.
 */
@Service
public class RouteGeometryService {

    private static final Logger log = LoggerFactory.getLogger(RouteGeometryService.class);

    private final RouteRepository routeRepository;
    private final RouteStopRepository routeStopRepository;
    private final RouteGeometryClient geometryClient;
    private final double validationRadiusKm;

    private final Cache<String, CachedGeometry> cache;
    private final ConcurrentHashMap<String, CompletableFuture<GeometryResult>> inflight = new ConcurrentHashMap<>();

    public RouteGeometryService(RouteRepository routeRepository,
                                RouteStopRepository routeStopRepository,
                                RouteGeometryClient geometryClient,
                                AppProperties props) {
        this.routeRepository = routeRepository;
        this.routeStopRepository = routeStopRepository;
        this.geometryClient = geometryClient;
        // How far a stop may sit from the generated road geometry before the
        // route is treated as invalid (stops can legitimately be a little
        // beside the road, but never tens of kilometres away).
        this.validationRadiusKm = Math.max(0.5, props.getRouting().getValidationRadiusKm());
        this.cache = Caffeine.newBuilder()
                .maximumSize(5000)
                .expireAfterWrite(java.time.Duration.ofDays(7))
                .build();
    }

    @Transactional(readOnly = true)
    public PassengerDtos.RouteGeometryDto geometry(Long routeId) {
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> ApiException.notFound("Route not found: " + routeId));
        List<RouteStop> stops = routeStopRepository.findByRouteIdOrderByStopOrderAsc(routeId);
        String key = stopsFingerprint(route, stops);

        if (stops.size() < 2) {
            return toDto(route, key, "cache", false, "not-enough-stops", null, null, null, null);
        }

        CachedGeometry hit = cache.getIfPresent(key);
        if (hit != null) {
            return toDto(route, key, "cache", true, "ok",
                    hit.coordinates(), hit.points(), hit.distanceKm(), hit.durationSec());
        }

        CompletableFuture<GeometryResult> joined = inflight.putIfAbsent(key, new CompletableFuture<>());
        if (joined != null) {
            return await(route, key, joined);
        }

        CompletableFuture<GeometryResult> mine = inflight.get(key);
        GeometryResult result;
        try {
            result = compute(stops);
        } catch (Exception e) {
            log.warn("Route geometry failed for route {} ({}-{}): {}",
                    routeId, route.getCode(), stops.size(), e.toString());
            result = GeometryResult.unavailable("routing-unavailable", null, 0, 0, 0);
        }
        mine.complete(result);
        inflight.remove(key);

        if (result.available()) {
            cache.put(key, new CachedGeometry(result.coordinates(), result.points(),
                    result.distanceKm(), result.durationSec()));
        }
        return toDto(route, key, "osrm", result.available(), result.reason(),
                result.coordinates(), result.points(),
                result.distanceKm(), result.durationSec());
    }

    /**
     * Direction-aware geometry. The physical road path is the same in both
     * directions, so OUTBOUND reuses the cached geometry as-is and INBOUND
     * serves exactly that cached path with its coordinates reversed (travel in
     * the opposite order over the same road).
     */
    @Transactional(readOnly = true)
    public PassengerDtos.RouteGeometryDto geometry(Long routeId, String direction) {
        PassengerDtos.RouteGeometryDto base = geometry(routeId);
        if (!"INBOUND".equalsIgnoreCase(direction) || base.geometry() == null) {
            return base;
        }
        List<List<Double>> reversed = new ArrayList<>(base.geometry().coordinates());
        for (int i = 0, j = reversed.size() - 1; i < j; i++, j--) {
            List<Double> tmp = reversed.get(i);
            reversed.set(i, reversed.get(j));
            reversed.set(j, tmp);
        }
        PassengerDtos.GeometryLineStringDto line =
                new PassengerDtos.GeometryLineStringDto("LineString", reversed);
        return new PassengerDtos.RouteGeometryDto(
                base.routeId(), base.code(), base.stopsKey(), base.source(), base.available(),
                base.reason(), line, base.distanceKm(), base.durationSec(), base.computedAt());
    }

    /**
     * Admin preview: validate/compute geometry for a proposed ordered stop
     * list before it is saved. Never substitutes fake geometry; when OSRM is
     * unavailable the caller is told so explicitly.
     */
    public GeometryPreview preview(List<RouteGeometryClient.Point> points) {
        if (points == null || points.size() < 2) {
            return new GeometryPreview(false, "not-enough-stops", null, null, null);
        }
        Optional<RouteGeometryClient.RouteResult> result = geometryClient.route(points);
        if (result.isEmpty()) {
            return new GeometryPreview(false, "routing-unavailable", null, null, null);
        }
        RouteGeometryClient.RouteResult r = result.get();
        List<List<Double>> coordinates = new ArrayList<>(r.coordinates().size());
        for (double[] c : r.coordinates()) {
            coordinates.add(List.of(c[0], c[1]));
        }
        if (!validateStopsOnRoute(points, coordinates)) {
            return new GeometryPreview(false, "stop-off-route", null, null, null);
        }
        return new GeometryPreview(true, "ok", coordinates, r.distanceMeters() / 1000.0, r.durationSeconds());
    }

    /** Result of a geometry preview (no route persisted). */
    public record GeometryPreview(boolean available, String reason, List<List<Double>> coordinates,
                                  Double distanceKm, Double durationSec) {
    }

    private GeometryResult compute(List<RouteStop> stops) {
        List<RouteGeometryClient.Point> points = new ArrayList<>(stops.size());
        for (RouteStop s : stops) {
            if (s.getLatitude() == null || s.getLongitude() == null) {
                return GeometryResult.unavailable("missing-stop-coordinates", null, 0, 0, 0);
            }
            points.add(new RouteGeometryClient.Point(s.getLatitude().doubleValue(), s.getLongitude().doubleValue()));
        }
        Optional<RouteGeometryClient.RouteResult> result = geometryClient.route(points);
        if (result.isEmpty()) {
            return GeometryResult.unavailable("routing-unavailable", null, 0, 0, 0);
        }
        RouteGeometryClient.RouteResult r = result.get();
        List<List<Double>> coordinates = new ArrayList<>(r.coordinates().size());
        for (double[] c : r.coordinates()) {
            coordinates.add(List.of(c[0], c[1]));
        }
        if (!validateStopsOnRoute(points, coordinates)) {
            return GeometryResult.unavailable("stop-off-route", null, 0, 0, 0);
        }
        return GeometryResult.ok(coordinates, r.points(), r.distanceMeters() / 1000.0, r.durationSeconds());
    }

    /**
     * Ensures every ordered stop is close to the generated road geometry.
     * OSRM snaps each waypoint, so after stitching each stop has its snapped
     * position on the line; if a stop is far from the whole line the source
     * data is wrong and the geometry must not be served.
     */
    private boolean validateStopsOnRoute(List<RouteGeometryClient.Point> points,
                                         List<List<Double>> coordinates) {
        for (int i = 0; i < points.size(); i++) {
            RouteGeometryClient.Point p = points.get(i);
            double best = Double.POSITIVE_INFINITY;
            for (List<Double> lonLat : coordinates) {
                double d = Geo.distanceKm(p.lat(), p.lon(), lonLat.get(1), lonLat.get(0));
                if (d < best) {
                    best = d;
                }
            }
            if (best > validationRadiusKm) {
                log.warn("Stop {} at ({},{}) is {} km from the generated geometry of route; rejecting",
                        i + 1, p.lat(), p.lon(), Math.round(best * 10) / 10.0);
                return false;
            }
        }
        return true;
    }

    private PassengerDtos.RouteGeometryDto await(Route route, String key,
                                                 CompletableFuture<GeometryResult> future) {
        GeometryResult result;
        try {
            result = future.get();
        } catch (Exception e) {
            log.warn("De-duplicated route geometry wait failed for route {}: {}", route.getId(), e.toString());
            result = GeometryResult.unavailable("routing-unavailable", null, 0, 0, 0);
        }
        return toDto(route, key, "osrm", result.available(), result.reason(),
                result.coordinates(), result.points(), result.distanceKm(), result.durationSec());
    }

    private PassengerDtos.RouteGeometryDto toDto(Route route, String stopsKey, String source,
                                                 boolean available, String reason,
                                                 List<List<Double>> coordinates, Integer points,
                                                 Double distanceKm, Double durationSec) {
        Long routeId = route.getId();
        String code = route.getCode();
        PassengerDtos.GeometryLineStringDto geometry =
                available && coordinates != null
                        ? new PassengerDtos.GeometryLineStringDto("LineString", coordinates)
                        : null;
        return new PassengerDtos.RouteGeometryDto(
                routeId, code, stopsKey, source, available, reason,
                geometry, distanceKm, durationSec, LocalDateTime.now());
    }

    private static String stopsFingerprint(Route route, List<RouteStop> stops) {
        StringBuilder sb = new StringBuilder();
        sb.append(route.getId()).append('|').append(route.getCode()).append('|');
        for (RouteStop s : stops) {
            sb.append(s.getStopOrder()).append('~')
                    .append(s.getStopName()).append('~')
                    .append(s.getLatitude()).append('~')
                    .append(s.getLongitude()).append('|');
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return Integer.toUnsignedString(sb.toString().hashCode());
        }
    }

    private record CachedGeometry(List<List<Double>> coordinates, int points, double distanceKm, double durationSec) {
    }

    private record GeometryResult(boolean available, String reason, List<List<Double>> coordinates,
                                  int points, double distanceKm, double durationSec) {

        static GeometryResult ok(List<List<Double>> coords, int points, double km, double sec) {
            return new GeometryResult(true, "ok", coords, points, km, sec);
        }

        static GeometryResult unavailable(String reason, List<List<Double>> coords, int points, double km, double sec) {
            return new GeometryResult(false, reason, coords, points, km, sec);
        }
    }
}