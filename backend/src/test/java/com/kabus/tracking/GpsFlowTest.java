package com.kabus.tracking;

import com.fasterxml.jackson.databind.JsonNode;
import com.kabus.tracking.domain.entity.Bus;
import com.kabus.tracking.domain.entity.Corporation;
import com.kabus.tracking.domain.entity.Crew;
import com.kabus.tracking.domain.entity.CrewAssignment;
import com.kabus.tracking.domain.entity.Depot;
import com.kabus.tracking.domain.entity.Division;
import com.kabus.tracking.domain.entity.Route;
import com.kabus.tracking.domain.entity.RouteStop;
import com.kabus.tracking.domain.entity.Town;
import com.kabus.tracking.domain.entity.Trip;
import com.kabus.tracking.domain.entity.User;
import com.kabus.tracking.domain.enums.RoleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GpsFlowTest extends TestSupport {

    private User driver;
    private User conductor;
    private Trip trip;
    private Bus bus;

    @BeforeEach
    void seedCrew() {
        Corporation corp = corporation();
        Division div = division(corp);
        Depot depot = depot(div);
        Town town = town(depot);
        Route route = route(div, "Bengaluru", "Mangaluru");
        routeStop(route, 1, "Bengaluru");
        routeStop(route, 2, "Mangaluru");

        bus = bus(depot, town);
        trip = trip(route, bus);

        driver = user(uniqueBase("driver"), RoleCode.DRIVER);
        conductor = user(uniqueBase("conductor"), RoleCode.CONDUCTOR);

        Crew d = crew(driver, "DRIVER");
        Crew c = crew(conductor, "CONDUCTOR");
        assignment(trip, d, "DRIVER");
        assignment(trip, c, "CONDUCTOR");
    }

    private String token(User u) throws Exception {
        return loginToken(u.getUsername(), "Pass@123");
    }

    private String startSession(User u) throws Exception {
        MvcResult result = mvc.perform(post("/api/crew/gps/start")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(u))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceId\":\"TEST-PHONE\",\"appVersion\":\"1.0.0\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = om.readTree(result.getResponse().getContentAsString());
        return node.get("sessionKey").asText();
    }

    @Test
    void assignment_isServerDerivedFromActiveAssignment() throws Exception {
        MvcResult result = mvc.perform(get("/api/crew/assignment")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(driver)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.busId").value(bus.getId()))
                .andExpect(jsonPath("$.tripId").value(trip.getId()))
                .andReturn();
        JsonNode node = om.readTree(result.getResponse().getContentAsString());
        if (node.get("tripId").asLong() != trip.getId()) {
            throw new AssertionError("assignment must come from server-side active assignment");
        }
    }

    @Test
    void userWithoutCrewProfile_cannotAccessCrewApi() throws Exception {
        Corporation corp = corporation();
        Division div = division(corp);
        Depot depot = depot(div);
        Town town = town(depot);
        User head = userScoped(uniqueBase("staff"), RoleCode.DEPOT_HEAD, div, depot, town);
        depotHeadProfile(head, depot);
        mvc.perform(get("/api/crew/assignment")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminLoginToken(head, RoleCode.DEPOT_HEAD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void gpsStart_location_end_fullCycle() throws Exception {
        String sessionKey = startSession(driver);

        mvc.perform(post("/api/crew/gps/location")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(driver))
                        .param("sessionKey", sessionKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(locationBody("14.500000", "74.500000", "45.2", "90", "3.5", LocalDateTime.now().toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.busStatus").value("LIVE"));

        // The passenger API immediately reflects the live bus.
        mvc.perform(get("/api/public/buses/" + bus.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liveStatus").value("LIVE"))
                .andExpect(jsonPath("$.latitude").value(14.5));

        mvc.perform(post("/api/crew/gps/end")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(driver))
                        .param("sessionKey", sessionKey))
                .andExpect(status().isOk());

        // After end the bus is OFFLINE to passengers.
        mvc.perform(get("/api/public/buses/" + bus.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liveStatus").value("OFFLINE"));

        // Location uploads after end are rejected.
        mvc.perform(post("/api/crew/gps/location")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(driver))
                        .param("sessionKey", sessionKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(locationBody("14.5", "74.5", "10", "0", "1", LocalDateTime.now().toString())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyOneActiveSessionPerBus_isEnforced() throws Exception {
        String sessionKey = startSession(driver);
        // Second crew on the same bus must NOT be able to open a second stream.
        mvc.perform(post("/api/crew/gps/start")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(conductor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    void crossCrewSessionAccess_isForbidden() throws Exception {
        String sessionKey = startSession(driver);
        // Conductor must not be able to inject into the driver's session.
        mvc.perform(post("/api/crew/gps/location")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(conductor))
                        .param("sessionKey", sessionKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(locationBody("14.5", "74.5", "10", "0", "1", LocalDateTime.now().toString())))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidCoordinates_areRejected() throws Exception {
        String sessionKey = startSession(driver);
        mvc.perform(post("/api/crew/gps/location")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(driver))
                        .param("sessionKey", sessionKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(locationBody("91.0", "74.5", "10", "0", "1", LocalDateTime.now().toString())))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/crew/gps/location")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(driver))
                        .param("sessionKey", sessionKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(locationBody("14.5", "-181.0", "10", "0", "1", LocalDateTime.now().toString())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void futureTimestamp_isRejectedForClockSkew() throws Exception {
        String sessionKey = startSession(driver);
        String future = LocalDateTime.now().plusHours(2).toString();
        mvc.perform(post("/api/crew/gps/location")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(driver))
                        .param("sessionKey", sessionKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(locationBody("14.5", "74.5", "10", "0", "1", future)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crewStatus_reportsActiveSession() throws Exception {
        String sessionKey = startSession(driver);
        mvc.perform(get("/api/crew/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(driver)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gpsStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.assignment.tripId").value(trip.getId()));
    }

    private String locationBody(String lat, String lon, String speed, String heading, String acc, String ts)
            throws com.fasterxml.jackson.core.JsonProcessingException {
        return om.writeValueAsString(Map.of(
                "latitude", new BigDecimal(lat),
                "longitude", new BigDecimal(lon),
                "speed", new BigDecimal(speed),
                "heading", new BigDecimal(heading),
                "accuracy", new BigDecimal(acc),
                "altitude", new BigDecimal("55.0"),
                "timestamp", ts));
    }
}