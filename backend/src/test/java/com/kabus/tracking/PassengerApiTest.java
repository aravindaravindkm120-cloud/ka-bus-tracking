package com.kabus.tracking;

import com.kabus.tracking.domain.entity.Bus;
import com.kabus.tracking.domain.entity.Corporation;
import com.kabus.tracking.domain.entity.Depot;
import com.kabus.tracking.domain.entity.Division;
import com.kabus.tracking.domain.entity.Route;
import com.kabus.tracking.domain.entity.Town;
import com.kabus.tracking.domain.entity.Trip;
import com.kabus.tracking.domain.enums.TripStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PassengerApiTest extends TestSupport {

    private Route route;

    @BeforeEach
    void seedRoute() {
        Corporation corp = corporation();
        Division div = division(corp);
        Depot depot = depot(div);
        town(depot);
        route = route(div, "Bengaluru", "Mangaluru");
        routeStop(route, 1, "Bengaluru");
        routeStop(route, 2, "Mangaluru");
    }

    @Test
    void publicEndpoints_requireNoAuth() throws Exception {
        mvc.perform(get("/api/public/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liveThresholdSeconds").value(60))
                .andExpect(jsonPath("$.mapBusCap").value(200));

        mvc.perform(get("/api/public/routes").param("term", "Bengaluru"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mvc.perform(get("/api/public/search").param("from", "Bengaluru").param("to", "Mangaluru"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").isNumber());

        mvc.perform(get("/api/public/buses/nearby")
                        .param("latitude", "14.5").param("longitude", "74.5"))
                .andExpect(status().isOk());
    }

    @Test
    void search_findsBusOnRunningTripForThatRoute() throws Exception {
        Corporation corp = corporation();
        Division div = division(corp);
        Depot depot = depot(div);
        Town town = town(depot);
        Bus b = bus(depot, town);
        Trip t = trip(route, b);
        t.setStatus(TripStatus.RUNNING);
        tripRepository.save(t);

        mvc.perform(get("/api/public/search").param("from", "Bengaluru").param("to", "Mangaluru"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.direction").value("OUTBOUND"))
                .andExpect(jsonPath("$.buses[0].busId").value(b.getId()))
                .andExpect(jsonPath("$.buses[0].direction").value("OUTBOUND"))
                .andExpect(jsonPath("$.buses[0].origin").value("Bengaluru"))
                .andExpect(jsonPath("$.buses[0].destination").value("Mangaluru"));
    }

    @Test
    void reverseSearch_doesNotReturnOutboundOnlyBuses() throws Exception {
        Corporation corp = corporation();
        Division div = division(corp);
        Depot depot = depot(div);
        Town town = town(depot);
        Bus b = bus(depot, town);
        Trip t = trip(route, b);
        t.setStatus(TripStatus.RUNNING);
        tripRepository.save(t);

        // Honnavar -> Karwar style reversal: only an OUTBOUND trip exists, so the
        // reverse corridor must NOT fabricate a bus. The corridor (and its
        // INBOUND direction) is still returned so the map can show the route.
        mvc.perform(get("/api/public/search").param("from", "Mangaluru").param("to", "Bengaluru"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.buses", hasSize(0)))
                .andExpect(jsonPath("$.direction").value("INBOUND"))
                .andExpect(jsonPath("$.routes", hasSize(1)))
                .andExpect(jsonPath("$.routes[0].direction").value("INBOUND"))
                .andExpect(jsonPath("$.routes[0].stops[0].name").value("Mangaluru"));
    }

    @Test
    void reverseSearch_findsInboundTripWithOrientedTerminals() throws Exception {
        Corporation corp = corporation();
        Division div = division(corp);
        Depot depot = depot(div);
        Town town = town(depot);
        Bus b = bus(depot, town);
        Trip t = trip(route, b);
        t.setStatus(TripStatus.RUNNING);
        t.setDirection("INBOUND");
        tripRepository.save(t);

        mvc.perform(get("/api/public/search").param("from", "Mangaluru").param("to", "Bengaluru"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.direction").value("INBOUND"))
                .andExpect(jsonPath("$.buses[0].busId").value(b.getId()))
                .andExpect(jsonPath("$.buses[0].direction").value("INBOUND"))
                .andExpect(jsonPath("$.buses[0].origin").value("Mangaluru"))
                .andExpect(jsonPath("$.buses[0].destination").value("Bengaluru"));
    }

    @Test
    void search_inboundTripIsNotReturnedForForwardCorridor() throws Exception {
        Corporation corp = corporation();
        Division div = division(corp);
        Depot depot = depot(div);
        Town town = town(depot);
        Bus b = bus(depot, town);
        Trip t = trip(route, b);
        t.setStatus(TripStatus.RUNNING);
        t.setDirection("INBOUND");
        tripRepository.save(t);

        mvc.perform(get("/api/public/search").param("from", "Bengaluru").param("to", "Mangaluru"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void routeDetails_inboundDirectionReorientsStopsAndTerminals() throws Exception {
        mvc.perform(get("/api/public/routes/" + route.getId())
                        .param("direction", "INBOUND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.direction").value("INBOUND"))
                .andExpect(jsonPath("$.origin").value("Mangaluru"))
                .andExpect(jsonPath("$.destination").value("Bengaluru"))
                .andExpect(jsonPath("$.stops", hasSize(2)))
                .andExpect(jsonPath("$.stops[0].name").value("Mangaluru"))
                .andExpect(jsonPath("$.stops[0].order").value(1))
                .andExpect(jsonPath("$.stops[1].name").value("Bengaluru"))
                .andExpect(jsonPath("$.stops[1].order").value(2));

        // Default (OUTBOUND) keeps the stored order.
        mvc.perform(get("/api/public/routes/" + route.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.direction").value("OUTBOUND"))
                .andExpect(jsonPath("$.origin").value("Bengaluru"))
                .andExpect(jsonPath("$.stops[0].name").value("Bengaluru"));
    }

    @Test
    void favoriteLifecycle_deviceBased() throws Exception {
        mvc.perform(post("/api/public/favorites")
                        .header("X-Device-Id", "dev-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "itemType", "ROUTE",
                                "itemId", route.getId(),
                                "label", "Bengaluru - Mangaluru",
                                "latitude", 14.6,
                                "longitude", 74.6))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favoriteId").isNumber())
                .andExpect(jsonPath("$.label").value("Bengaluru - Mangaluru"));

        mvc.perform(get("/api/public/favorites").header("X-Device-Id", "dev-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        // duplicate favorite
        mvc.perform(post("/api/public/favorites")
                        .header("X-Device-Id", "dev-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "itemType", "ROUTE",
                                "itemId", route.getId(),
                                "label", "Bengaluru - Mangaluru",
                                "latitude", 14.6,
                                "longitude", 74.6))))
                .andExpect(status().isConflict());

        mvc.perform(delete("/api/public/favorites/ROUTE/" + route.getId()).header("X-Device-Id", "dev-1"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/public/favorites").header("X-Device-Id", "dev-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void favorites_requireDeviceId() throws Exception {
        mvc.perform(post("/api/public/favorites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "itemType", "ROUTE",
                                "itemId", route.getId(),
                                "label", "R",
                                "latitude", 14.6,
                                "longitude", 74.6))))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/public/favorites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void recentSearchLifecycle_deviceBased() throws Exception {
        mvc.perform(post("/api/public/searches")
                        .header("X-Device-Id", "dev-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("from", "Bengaluru", "to", "Mangaluru"))))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/public/searches").header("X-Device-Id", "dev-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].from").value("Bengaluru"));

        // different device sees nothing
        mvc.perform(get("/api/public/searches").header("X-Device-Id", "other-device"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void busDetails_returnsRouteStopsForRunningTrip() throws Exception {
        Corporation corp = corporation();
        Division div = division(corp);
        Depot depot = depot(div);
        Town town = town(depot);
        Bus b = bus(depot, town);
        Trip t = trip(route, b);
        t.setStatus(TripStatus.RUNNING);
        tripRepository.save(t);

        mvc.perform(get("/api/public/buses/" + b.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationNo").value(b.getRegistrationNo()))
                .andExpect(jsonPath("$.tripId").value(t.getId()))
                .andExpect(jsonPath("$.route.stops", hasSize(2)));
    }

    @Test
    void unknownBus_returns404() throws Exception {
        mvc.perform(get("/api/public/buses/999999"))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------------
    // Stop autocomplete (/api/public/stops) + corridor search behaviours
    // ---------------------------------------------------------------------

    /** Karwar -> Honnavar with real waypoint stops between. */
    private void seedCoastRoute() {
        Corporation corp = corporation();
        Division div = division(corp);
        Depot depot = depot(div);
        town(depot);
        Route coast = route(div, "Karwar", "Honnavar");
        routeStop(coast, 1, "Karwar Bus Stand");
        routeStop(coast, 2, "Kadwad");
        routeStop(coast, 3, "Kumta");
        routeStop(coast, 4, "Gokarna Road");
        routeStop(coast, 5, "Honnavar");
    }

    @Test
    void stopSuggestions_findWaypointAndTerminalStops_bySubstring() throws Exception {
        seedCoastRoute();

        // A waypoint stop that is NOT a route terminal (the old /routes
        // autocomplete never matched these).
        mvc.perform(get("/api/public/stops").param("term", "adwad"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Kadwad"))
                .andExpect(jsonPath("$[0].latitude").isNumber())
                .andExpect(jsonPath("$[0].longitude").isNumber());

        // Terminal stops are suggested too, with coordinates.
        mvc.perform(get("/api/public/stops").param("term", "onnava"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Honnavar"));

        // Case-insensitive.
        mvc.perform(get("/api/public/stops").param("term", "KADWAD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Kadwad"));
    }

    @Test
    void stopSuggestions_normalizeWhitespaceAndRespectLimit() throws Exception {
        seedCoastRoute();

        // "gokarna  road" (doubled space) still matches "Gokarna Road".
        mvc.perform(get("/api/public/stops").param("term", "gokarna  road"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Gokarna Road"));

        // Limit is honoured and results are de-duplicated (Karwar appears both
        // as a stop and as a route terminal).
        mvc.perform(get("/api/public/stops").param("term", "k").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].name").value("Kadwad"));
    }

    @Test
    void stopSuggestions_ignoreDisabledRoutes() throws Exception {
        Corporation corp = corporation();
        Division div = division(corp);
        Depot depot = depot(div);
        town(depot);
        Route r = route(div, "Karwar", "Honnavar");
        r.setEnabled(false);
        routeRepository.save(r);
        routeStop(r, 1, "Karwar");
        routeStop(r, 2, "Kadwad");

        mvc.perform(get("/api/public/stops").param("term", "kadwad"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void routeSuggestions_stillMatchOnlyRouteFields_notWaypointStops() throws Exception {
        seedCoastRoute();

        // Documents the fix: /routes* remains terminal/code/name matching while
        // /stops is the authoritative waypoint-stop autocomplete.
        mvc.perform(get("/api/public/routes").param("term", "kadwad"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(get("/api/public/routes").param("term", "Karwar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void search_waypointSearch_returnsCanonicalNamesAndCorridorSlice() throws Exception {
        seedCoastRoute();

        mvc.perform(get("/api/public/search")
                        .param("from", "Kadwad").param("to", "Gokarna Road"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("Kadwad"))
                .andExpect(jsonPath("$.to").value("Gokarna Road"))
                .andExpect(jsonPath("$.direction").value("OUTBOUND"))
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.routes", hasSize(1)))
                .andExpect(jsonPath("$.routes[0].direction").value("OUTBOUND"))
                .andExpect(jsonPath("$.routes[0].origin").value("Karwar"))
                .andExpect(jsonPath("$.routes[0].destination").value("Honnavar"))
                .andExpect(jsonPath("$.routes[0].stops", hasSize(3)))
                .andExpect(jsonPath("$.routes[0].stops[0].name").value("Kadwad"))
                .andExpect(jsonPath("$.routes[0].stops[1].name").value("Kumta"))
                .andExpect(jsonPath("$.routes[0].stops[2].name").value("Gokarna Road"));
    }

    @Test
    void search_reverseWaypointSearch_returnsInboundCorridorSlicedInTravelOrder() throws Exception {
        seedCoastRoute();

        mvc.perform(get("/api/public/search")
                        .param("from", "honnavar").param("to", "kadwad"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("Honnavar"))
                .andExpect(jsonPath("$.to").value("Kadwad"))
                .andExpect(jsonPath("$.direction").value("INBOUND"))
                .andExpect(jsonPath("$.routes", hasSize(1)))
                .andExpect(jsonPath("$.routes[0].direction").value("INBOUND"))
                .andExpect(jsonPath("$.routes[0].origin").value("Honnavar"))
                .andExpect(jsonPath("$.routes[0].destination").value("Karwar"))
                .andExpect(jsonPath("$.routes[0].stops", hasSize(4)))
                .andExpect(jsonPath("$.routes[0].stops[0].name").value("Honnavar"))
                .andExpect(jsonPath("$.routes[0].stops[3].name").value("Kadwad"));
    }

    @Test
    void search_normalizesCaseAndWhitespace_toOfficialNames() throws Exception {
        seedCoastRoute();

        mvc.perform(get("/api/public/search")
                        .param("from", " KADWAD ").param("to", "gokarna   road"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("Kadwad"))
                .andExpect(jsonPath("$.to").value("Gokarna Road"))
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.routes", hasSize(1)));
    }

    @Test
    void search_waypointCorridor_findsBusRunningBetweenThem() throws Exception {
        Corporation corp = corporation();
        Division div = division(corp);
        Depot depot = depot(div);
        Town town = town(depot);
        Route coast = route(div, "Karwar", "Honnavar");
        routeStop(coast, 1, "Karwar Bus Stand");
        routeStop(coast, 2, "Kadwad");
        routeStop(coast, 3, "Kumta");
        routeStop(coast, 4, "Gokarna Road");
        routeStop(coast, 5, "Honnavar");
        Bus b = bus(depot, town);
        Trip t = trip(coast, b);
        t.setStatus(TripStatus.RUNNING);
        tripRepository.save(t);

        mvc.perform(get("/api/public/search")
                        .param("from", "Kadwad").param("to", "Gokarna Road"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.buses[0].busId").value(b.getId()))
                .andExpect(jsonPath("$.buses[0].origin").value("Karwar"))
                .andExpect(jsonPath("$.buses[0].destination").value("Honnavar"))
                .andExpect(jsonPath("$.buses[0].direction").value("OUTBOUND"));
    }
}