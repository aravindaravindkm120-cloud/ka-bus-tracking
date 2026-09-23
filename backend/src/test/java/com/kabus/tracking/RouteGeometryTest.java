package com.kabus.tracking;

import com.kabus.tracking.domain.entity.Division;
import com.kabus.tracking.domain.entity.Route;
import com.kabus.tracking.domain.entity.RouteStop;
import com.kabus.tracking.service.RouteGeometryClient;
import com.kabus.tracking.service.RouteGeometryService;
import com.kabus.tracking.web.dto.PassengerDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Route-geometry endpoint tests. The OSRM HTTP client is replaced by a fake so
 * tests never hit the network. Verifies availability, caching, in-flight
 * de-duplication and OSRM-failure handling.
 */
class RouteGeometryTest extends TestSupport {

    @TestConfiguration
    static class RoutingConfig {
        @Bean
        @Primary
        RouteGeometryClient fakeClient() {
            return new FakeGeometryClient();
        }
    }

    @Autowired
    private RouteGeometryClient fakeClient;

    @Autowired
    private RouteGeometryService routeGeometryService;

    @BeforeEach
    void resetFakeClient() {
        FakeGeometryClient fake = (FakeGeometryClient) fakeClient;
        fake.calls.set(0);
        fake.available.set(true);
        fake.farFromRoute.set(false);
    }

    private Route routeWithStops(int stopCount, double lat, double lon) {
        CorporationHelper corp = hub();
        Route r = route(corp.division, "Origin", "Destination");
        for (int i = 1; i <= stopCount; i++) {
            RouteStop s = routeStop(r, i, "Stop " + i);
            s.setLatitude(java.math.BigDecimal.valueOf(lat + i * 0.01));
            s.setLongitude(java.math.BigDecimal.valueOf(lon + i * 0.01));
            routeStopRepository.save(s);
        }
        return r;
    }

    private static final class CorporationHelper {
        final Division division;
        CorporationHelper(Division division) { this.division = division; }
    }

    private CorporationHelper hub() {
        var corp = corporation();
        var div = division(corp);
        depot(div);
        return new CorporationHelper(div);
    }

    @Test
    void unknownRouteReturns404() throws Exception {
        mvc.perform(get("/api/public/routes/999999/geometry"))
                .andExpect(status().isNotFound());
    }

    @Test
    void routeWithFewerThanTwoStopsIsUnavailableWithoutCallingClient() throws Exception {
        Route r = routeWithStops(1, 14.10, 74.10);
        assertThat(((FakeGeometryClient) fakeClient).calls.get()).isZero();

        mvc.perform(get("/api/public/routes/" + r.getId() + "/geometry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reason").value("not-enough-stops"))
                .andExpect(jsonPath("$.geometry").doesNotExist());
        assertThat(((FakeGeometryClient) fakeClient).calls.get()).isZero();
    }

    @Test
    void returnsOsrmPolylineWhenAvailable() throws Exception {
        Route r = routeWithStops(2, 14.10, 74.10);

        mvc.perform(get("/api/public/routes/" + r.getId() + "/geometry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.reason").value("ok"))
                .andExpect(jsonPath("$.source").value("osrm"))
                .andExpect(jsonPath("$.geometry.type").value("LineString"))
                .andExpect(jsonPath("$.geometry.coordinates").isArray())
                .andExpect(jsonPath("$.geometry.coordinates[0][0]").isNumber())
                .andExpect(jsonPath("$.code").value(r.getCode()));
        assertThat(((FakeGeometryClient) fakeClient).calls.get()).isEqualTo(1);
    }

    @Test
    void secondRequestIsServedFromCentralCache() throws Exception {
        Route r = routeWithStops(2, 14.20, 74.20);

        PassengerDtos.RouteGeometryDto first = fetch(r.getId());
        assertThat(first.source()).isEqualTo("osrm");
        assertThat(((FakeGeometryClient) fakeClient).calls.get()).isEqualTo(1);

        PassengerDtos.RouteGeometryDto second = fetch(r.getId());
        assertThat(second.source()).isEqualTo("cache");
        assertThat(second.available()).isTrue();
        assertThat(second.geometry().coordinates()).isEqualTo(first.geometry().coordinates());
        assertThat(((FakeGeometryClient) fakeClient).calls.get()).isEqualTo(1);
    }

    @Test
    void concurrentRequestsForSameStopsTriggerSingleOsrmCall() throws Exception {
        Route r = routeWithStops(3, 14.30, 74.30);
        RouteGeometryService svc = routeGeometryService;
        FakeGeometryClient fake = (FakeGeometryClient) fakeClient;
        Long routeId = r.getId();

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<PassengerDtos.RouteGeometryDto>> futures = java.util.stream.IntStream.range(0, 4)
                    .mapToObj(i -> pool.submit(() -> {
                        start.await();
                        return svc.geometry(routeId);
                    }))
                    .toList();
            start.countDown();
            for (Future<PassengerDtos.RouteGeometryDto> f : futures) {
                PassengerDtos.RouteGeometryDto d = f.get(30, TimeUnit.SECONDS);
                assertThat(d.available()).isTrue();
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(fake.calls.get()).isEqualTo(1);
    }

    @Test
    void osrmFailureMarksRouteUnavailableWithoutFakeGeometry() throws Exception {
        FakeGeometryClient fake = (FakeGeometryClient) fakeClient;
        fake.available.set(false);
        Route r = routeWithStops(2, 14.40, 74.40);

        mvc.perform(get("/api/public/routes/" + r.getId() + "/geometry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reason").value("routing-unavailable"))
                .andExpect(jsonPath("$.geometry").doesNotExist());
        assertThat(fake.calls.get()).isEqualTo(1);
    }

    @Test
    void returnedGeometryUsesGeoJsonLonLatOrder() throws Exception {
        Route r = routeWithStops(2, 14.10, 74.10);

        mvc.perform(get("/api/public/routes/" + r.getId() + "/geometry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                // Stops are (14.11,74.11) and (14.12,74.12). GeoJSON is
                // [longitude, latitude], so index 0 must be the stop's longitude
                // everywhere along the stitched line (stops at indices 0 and 2,
                // offset midpoint at index 1).
                .andExpect(jsonPath("$.geometry.coordinates[0][0]").value(74.11))
                .andExpect(jsonPath("$.geometry.coordinates[0][1]").value(14.11))
                .andExpect(jsonPath("$.geometry.coordinates[2][0]").value(74.12))
                .andExpect(jsonPath("$.geometry.coordinates[2][1]").value(14.12));
    }

    @Test
    void multiStopRouteIsStitchedIntoOneContinuousLineString() throws Exception {
        Route r = routeWithStops(5, 14.50, 74.50);

        PassengerDtos.RouteGeometryDto dto = fetch(r.getId());
        assertThat(dto.available()).isTrue();
        List<List<Double>> coords = dto.geometry().coordinates();
        // 5 stops -> 4 segments, each contributing start+mid+end => 9 points,
        // with each shared junction stop appearing exactly once in the merged line.
        assertThat(coords).hasSize(9);
        assertThat(coords.get(0)).isEqualTo(List.of(74.51, 14.51));
        assertThat(coords.get(8)).isEqualTo(List.of(74.55, 14.55));
        // Junction at index 2 is the second stop; index 3 must NOT repeat it.
        assertThat(coords.get(2)).isNotEqualTo(coords.get(3));
    }

    @Test
    void stopFarFromGeometryMarksRouteInvalidNotFakeLine() throws Exception {
        FakeGeometryClient fake = (FakeGeometryClient) fakeClient;
        fake.farFromRoute.set(true);
        Route r = routeWithStops(2, 15.30, 75.30);

        mvc.perform(get("/api/public/routes/" + r.getId() + "/geometry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reason").value("stop-off-route"))
                .andExpect(jsonPath("$.geometry").doesNotExist());
    }

    @Test
    void stopsEqualToGeometryAreAccepted() throws Exception {
        Route r = routeWithStops(2, 14.60, 74.60);
        PassengerDtos.RouteGeometryDto dto = fetch(r.getId());
        assertThat(dto.available()).isTrue();
        assertThat(dto.reason()).isEqualTo("ok");
        assertThat(((FakeGeometryClient) fakeClient).calls.get()).isEqualTo(1);
    }

    @Test
    void inboundDirectionReversesCoordinatesVector() throws Exception {
        Route r = routeWithStops(2, 14.10, 74.10);
        PassengerDtos.RouteGeometryDto outbound = fetch(r.getId());
        assertThat(outbound.geometry()).isNotNull();

        var result = mvc.perform(get("/api/public/routes/" + r.getId() + "/geometry")
                        .param("direction", "INBOUND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.geometry.type").value("LineString"))
                .andReturn();
        PassengerDtos.RouteGeometryDto inbound = om.readValue(
                result.getResponse().getContentAsString(), PassengerDtos.RouteGeometryDto.class);

        List<List<Double>> a = outbound.geometry().coordinates();
        List<List<Double>> b = inbound.geometry().coordinates();
        assertThat(b).hasSameSizeAs(a);
        assertThat(b.get(0)).isEqualTo(a.get(a.size() - 1));
        assertThat(b.get(b.size() - 1)).isEqualTo(a.get(0));
        // Same physical shape, different travel order: no call to the client.
        assertThat(((FakeGeometryClient) fakeClient).calls.get()).isEqualTo(1);
    }

    @Test
    void unknownDirectionFallsBackToOutboundGeometry() throws Exception {
        Route r = routeWithStops(2, 14.70, 74.70);
        PassengerDtos.RouteGeometryDto base = fetch(r.getId());

        var result = mvc.perform(get("/api/public/routes/" + r.getId() + "/geometry")
                        .param("direction", "SOMEWHERE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andReturn();
        PassengerDtos.RouteGeometryDto fallback = om.readValue(
                result.getResponse().getContentAsString(), PassengerDtos.RouteGeometryDto.class);
        assertThat(fallback.geometry().coordinates()).isEqualTo(base.geometry().coordinates());
    }

    private PassengerDtos.RouteGeometryDto fetch(Long routeId) throws Exception {
        var result = mvc.perform(get("/api/public/routes/" + routeId + "/geometry"))
                .andExpect(status().isOk())
                .andReturn();
        return om.readValue(result.getResponse().getContentAsString(), PassengerDtos.RouteGeometryDto.class);
    }

    /** Replaceable OSRM seam: produces a knock-on polyline without network. */
    static final class FakeGeometryClient implements RouteGeometryClient {
        final AtomicInteger calls = new AtomicInteger();
        final java.util.concurrent.atomic.AtomicBoolean available = new java.util.concurrent.atomic.AtomicBoolean(true);
        final java.util.concurrent.atomic.AtomicBoolean farFromRoute = new java.util.concurrent.atomic.AtomicBoolean(false);

        @Override
        public Optional<RouteResult> route(List<Point> orderedStops) {
            calls.incrementAndGet();
            if (!available.get()) {
                return Optional.empty();
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            double off = farFromRoute.get() ? 10.0 : 0.0;
            List<double[]> coords = new java.util.ArrayList<>();
            for (int i = 0; i < orderedStops.size() - 1; i++) {
                Point a = orderedStops.get(i);
                Point b = orderedStops.get(i + 1);
                double[][] seg = {
                        {a.lon() + off, a.lat() + off},
                        {a.lon() + (b.lon() - a.lon()) / 2 + 0.02 + off, a.lat() + (b.lat() - a.lat()) / 2 + off},
                        {b.lon() + off, b.lat() + off}
                };
                if (coords.isEmpty()) {
                    coords.add(seg[0]);
                }
                coords.add(seg[1]);
                coords.add(seg[2]);
            }
            return Optional.of(new RouteResult(coords, coords.size(), 100.0, 1800.0));
        }
    }
}