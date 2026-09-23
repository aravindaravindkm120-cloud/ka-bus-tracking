package com.kabus.tracking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kabus.tracking.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * OSRM-backed {@link RouteGeometryClient}. Requests real road-network
 * geometry ({@code overview=full}, {@code geometries=geojson}) for every
 * consecutive pair of stops and stitches the segments into one LineString.
 *
 * Uses bounded per-request timeouts and a limited number of retries; the
 * caller caches and de-duplicates so the public OSRM demo server is never
 * hammered with repeated identical requests.
 */
@Component
public class OsrmRouteGeometryClient implements RouteGeometryClient {

    private static final Logger log = LoggerFactory.getLogger(OsrmRouteGeometryClient.class);

    private final AppProperties.Routing routing;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public OsrmRouteGeometryClient(AppProperties props) {
        this.routing = props.getRouting();
        this.mapper = new ObjectMapper();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    private static final String UA =
            "ka-bus-tracking/1.0 (passenger route geometry)";

    @Override
    public Optional<RouteResult> route(List<Point> orderedStops) {
        if (orderedStops == null || orderedStops.size() < 2) {
            return Optional.empty();
        }
        if (orderedStops.size() > routing.getMaxStops()) {
            log.warn("Route has {} stops, refusing to request geometry for more than {}",
                    orderedStops.size(), routing.getMaxStops());
            return Optional.empty();
        }

        List<double[]> combined = new ArrayList<>();
        double totalDistanceM = 0;
        double totalDurationSec = 0;
        int combinedPoints = 0;

        for (int i = 0; i < orderedStops.size() - 1; i++) {
            Point a = orderedStops.get(i);
            Point b = orderedStops.get(i + 1);
            Optional<SegmentResult> segment = fetchSegment(a, b);
            if (segment.isEmpty()) {
                // Routing failed: surface it to the caller, never fake a road.
                return Optional.empty();
            }
            SegmentResult s = segment.get();
            for (int k = i == 0 ? 0 : 1; k < s.coordinates().size(); k++) {
                combined.add(s.coordinates().get(k));
            }
            totalDistanceM += s.distanceMeters();
            totalDurationSec += s.durationSeconds();
            combinedPoints += s.points() - (i == 0 ? 0 : 1);
        }

        if (combined.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new RouteResult(combined, combinedPoints, totalDistanceM, totalDurationSec));
    }

    private Optional<SegmentResult> fetchSegment(Point a, Point b) {
        String uri = routing.getBaseUrl()
                + "/route/v1/driving/"
                + lonLat(a) + ";" + lonLat(b)
                + "?overview=full&geometries=geojson&steps=false";
        Exception lastError = null;
        for (int attempt = 0; attempt <= routing.getMaxRetries(); attempt++) {
            try {
                if (attempt > 0) {
                    threadSleep(300L * attempt);
                }
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(uri))
                        .timeout(Duration.ofMillis(routing.getTimeoutMs()))
                        .header("accept", "application/json")
                        .header("user-agent", UA)
                        .GET()
                        .build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return parseSegment(response.body());
                }
                if (response.statusCode() == 429 || response.statusCode() == 503) {
                    // Public demo server is throttling: back off a little more.
                    lastError = new IllegalStateException("OSRM rate-limited HTTP " + response.statusCode());
                    threadSleep(800L * (attempt + 1));
                    continue;
                }
                lastError = new IllegalStateException("OSRM HTTP " + response.statusCode());
            } catch (Exception e) {
                lastError = e;
            }
        }
        log.warn("OSRM route failed between {} and {}: {}", a, b, lastError == null ? "unknown" : lastError.toString());
        return Optional.empty();
    }

    private Optional<SegmentResult> parseSegment(String body) {
        try {
            JsonNode root = mapper.readTree(body);
            if (!"Ok".equals(root.path("code").asText())) {
                log.warn("OSRM returned non-Ok code: {} message: {}", root.path("code").asText(),
                        root.path("message").asText(""));
                return Optional.empty();
            }
            JsonNode route0 = root.path("routes").path(0);
            JsonNode geometry = route0.path("geometry");
            if (!"LineString".equals(geometry.path("type").asText()) || geometry.path("coordinates").isEmpty()) {
                return Optional.empty();
            }
            List<double[]> coords = new ArrayList<>();
            for (JsonNode c : geometry.path("coordinates")) {
                double lon = c.path(0).asDouble();
                double lat = c.path(1).asDouble();
                if (!(lon >= -180 && lon <= 180 && lat >= -90 && lat <= 90)) {
                    return Optional.empty();
                }
                coords.add(new double[]{lon, lat});
            }
            if (coords.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new SegmentResult(
                    coords,
                    coords.size(),
                    route0.path("distance").asDouble(0),
                    route0.path("duration").asDouble(0)));
        } catch (Exception e) {
            log.warn("Could not parse OSRM response: {}", e.toString());
            return Optional.empty();
        }
    }

    private static String lonLat(Point p) {
        return p.lon() + "," + p.lat();
    }

    private static void threadSleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record SegmentResult(List<double[]> coordinates, int points, double distanceMeters, double durationSeconds) {
    }
}