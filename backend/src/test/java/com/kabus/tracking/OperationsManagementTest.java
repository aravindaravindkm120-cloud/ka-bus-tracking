package com.kabus.tracking;

import com.fasterxml.jackson.databind.JsonNode;
import com.kabus.tracking.domain.entity.Bus;
import com.kabus.tracking.domain.entity.Corporation;
import com.kabus.tracking.domain.entity.Crew;
import com.kabus.tracking.domain.entity.Depot;
import com.kabus.tracking.domain.entity.Division;
import com.kabus.tracking.domain.entity.Route;
import com.kabus.tracking.domain.entity.Staff;
import com.kabus.tracking.domain.entity.Town;
import com.kabus.tracking.domain.entity.User;
import com.kabus.tracking.domain.enums.RoleCode;
import com.kabus.tracking.service.RouteGeometryClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PHASE 4-7 + 9: fleet, staff, routes/stops, trips/assignments, audit logs and
 * settings. The OSRM client is replaced by a deterministic fake so no network
 * is used.
 */
class OperationsManagementTest extends TestSupport {

    @TestConfiguration
    static class RoutingConfig {
        @Bean
        @Primary
        RouteGeometryClient fakeRouting() {
            return new StraightLineClient();
        }
    }

    /** Straight-line geometry through the stops: always "on route". */
    static final class StraightLineClient implements RouteGeometryClient {
        @Override
        public Optional<RouteResult> route(List<Point> orderedStops) {
            if (orderedStops.size() < 2) {
                return Optional.empty();
            }
            List<double[]> coords = new ArrayList<>();
            for (Point p : orderedStops) {
                coords.add(new double[]{p.lon(), p.lat()});
            }
            return Optional.of(new RouteResult(coords, coords.size(), 1000.0, 600.0));
        }
    }

    private String superToken;
    private String superUsername;
    private Corporation corp;
    private Division div;
    private Depot depot;
    private Town town;

    @BeforeEach
    void setUp() throws Exception {
        User sa = user(uniqueBase("opsa"), RoleCode.SUPER_ADMIN);
        superAdminProfile(sa);
        superUsername = sa.getUsername();
        superToken = adminLoginToken(sa, RoleCode.SUPER_ADMIN);

        corp = corporation();
        div = division(corp);
        depot = depot(div);
        town = town(depot);
    }

    // ------------------------------------------------------------------
    // Fleet
    // ------------------------------------------------------------------

    @Test
    void fleetCreateListAndDuplicate() throws Exception {
        JsonNode bus = createBus(superToken, "KA01AB" + suffix().replace("-", ""), depot.getId(), town.getId());
        assertNotNull(bus.get("id"));

        mvc.perform(get("/api/admin/fleet/buses").param("search", bus.get("registrationNo").asText())
                        .header("Authorization", "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].registrationNo").value(bus.get("registrationNo").asText()));

        // The same bus number may not be defined twice for one depot -> 409
        mvc.perform(post("/api/admin/fleet/bus-numbers")
                        .header("Authorization", "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(busNumberBody(bus.get("registrationNo").asText(), depot.getId(), town.getId())))
                .andExpect(status().isConflict());
    }

    @Test
    void fleetRejectsTownFromAnotherDepot() throws Exception {
        Depot otherDepot = depot(div);
        Town otherTown = town(otherDepot);
        Long busNumberId = createBusNumber(superToken, "KA02CD" + suffix().replace("-", ""),
                depot.getId(), town.getId());
        mvc.perform(post("/api/admin/fleet/buses")
                        .header("Authorization", "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(busBody(busNumberId, depot.getId(), otherTown.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fleetIsScopedForDivisionAdmin() throws Exception {
        String token = divisionAdminToken();

        createBus(token, "KA03EF" + suffix().replace("-", ""), depot.getId(), town.getId());

        // A bus in an unrelated division is invisible.
        Corporation otherCorp = corporation();
        Division otherDiv = division(otherCorp);
        Depot otherDepot = depot(otherDiv);
        Town otherTown = town(otherDepot);
        createBus(superToken, "KA04GH" + suffix().replace("-", ""), otherDepot.getId(), otherTown.getId());

        MvcResult result = mvc.perform(get("/api/admin/fleet/buses").param("size", "100")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode content = om.readTree(result.getResponse().getContentAsString()).get("content");
        for (JsonNode row : content) {
            org.junit.jupiter.api.Assertions.assertEquals(div.getId().longValue(),
                    row.get("divisionId").asLong(), "division admin must only see own-division buses");
        }
    }

    @Test
    void fleetCannotBeCreatedOutsideDivisionScope() throws Exception {
        String token = divisionAdminToken();
        Corporation otherCorp = corporation();
        Division otherDiv = division(otherCorp);
        Depot otherDepot = depot(otherDiv);
        Town otherTown = town(otherDepot);

        // The bus number itself is created as super admin, so the scoped admin's
        // rejection below comes from the fleet endpoint, not from setup.
        Long busNumberId = createBusNumber(superToken, "KA05IJ" + suffix().replace("-", ""),
                otherDepot.getId(), otherTown.getId());
        mvc.perform(post("/api/admin/fleet/buses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(busBody(busNumberId, otherDepot.getId(), otherTown.getId())))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // Staff
    // ------------------------------------------------------------------

    @Test
    void staffCreateAndDuplicateEmpCode() throws Exception {
        String code = "EMP" + suffix().replace("-", "");
        JsonNode staff = createStaff(superToken, code);
        assertNotNull(staff.get("id"));

        mvc.perform(post("/api/admin/staff")
                        .header("Authorization", "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staffBody("Another " + code, code, depot.getId())))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------
    // Routes + stops
    // ------------------------------------------------------------------

    @Test
    void routeCreateWithStopsUsesRealGeometrySeam() throws Exception {
        JsonNode route = createRoute(superToken, "R" + suffix().replace("-", ""));
        Long routeId = route.get("id").asLong();

        mvc.perform(put("/api/admin/routes/" + routeId + "/stops")
                        .header("Authorization", "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("stops", List.of(
                                stop(1, "Origin", "14.100000", "74.100000"),
                                stop(2, "Destination", "14.200000", "74.200000"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stopCount").value(2))
                .andExpect(jsonPath("$.stops[0].stopName").value("Origin"));
    }

    @Test
    void routeDuplicateCodeRejected() throws Exception {
        String code = "RD" + suffix().replace("-", "");
        createRouteWithCode(superToken, code).andExpect(status().isCreated());
        createRouteWithCode(superToken, code).andExpect(status().isConflict());
    }

    @Test
    void depotHeadCannotCreateDivisionRoute() throws Exception {
        Corporation c = corporation();
        Division d = division(c);
        Depot dep = depot(d);
        Town t = town(dep);
        User head = userScoped(uniqueBase("head"), RoleCode.DEPOT_HEAD, d, dep, t);
        depotHeadProfile(head, dep);
        String token = adminLoginToken(head, RoleCode.DEPOT_HEAD);

        createRouteWithCode(token, "RH" + suffix().replace("-", ""))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // Trips + assignments
    // ------------------------------------------------------------------

    @Test
    void tripCreateAssignBusAndCrew() throws Exception {
        Route route = route(div, "Sira", "Tumakuru");
        Bus bus = bus(depot, town);
        Crew crew = crewWithStaff();

        JsonNode trip = createTrip(superToken, route.getId(), bus.getId());
        Long tripId = trip.get("id").asLong();

        // assign a bus
        mvc.perform(put("/api/admin/trips/" + tripId + "/bus")
                        .header("Authorization", "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("busId", bus.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.busAssignments[?(@.status=='ACTIVE')]").exists());

        // assign a crew
        mvc.perform(post("/api/admin/trips/" + tripId + "/crew")
                        .header("Authorization", "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("crewId", crew.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.crewAssignments[?(@.status=='ACTIVE')]").exists());

        // status transition
        mvc.perform(patch("/api/admin/trips/" + tripId + "/status").param("status", "RUNNING")
                        .header("Authorization", "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void tripRejectsBusFromAnotherDivision() throws Exception {
        Route route = route(div, "A", "B");
        Corporation otherCorp = corporation();
        Division otherDiv = division(otherCorp);
        Depot otherDepot = depot(otherDiv);
        Town otherTown = town(otherDepot);
        Bus otherBus = bus(otherDepot, otherTown);

        mvc.perform(post("/api/admin/trips")
                        .header("Authorization", "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tripBody(route.getId(), otherBus.getId())))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------
    // Audit logs + settings
    // ------------------------------------------------------------------

    @Test
    void auditLogsAreSuperAdminOnlyAndRecordActor() throws Exception {
        createBus(superToken, "KA09ZZ" + suffix().replace("-", ""), depot.getId(), town.getId());
        awaitAudit("FLEET_BUS_CREATE");

        String divToken = divisionAdminToken();
        mvc.perform(get("/api/admin/audit-logs").header("Authorization", "Bearer " + divToken))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/admin/audit-logs").param("action", "FLEET_BUS_CREATE")
                        .header("Authorization", "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("FLEET_BUS_CREATE"))
                .andExpect(jsonPath("$.content[0].username").value(superUsername));
    }

    @Test
    void settingsAreSuperAdminOnlyAndValidated() throws Exception {
        String divToken = divisionAdminToken();
        mvc.perform(get("/api/admin/settings").header("Authorization", "Bearer " + divToken))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/admin/settings").header("Authorization", "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.key=='live.threshold.live_seconds')]").exists());

        mvc.perform(put("/api/admin/settings/live.threshold.live_seconds")
                        .header("Authorization", "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("value", "45"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("45"));

        mvc.perform(put("/api/admin/settings/live.threshold.live_seconds")
                        .header("Authorization", "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("value", "not-a-number"))))
                .andExpect(status().isBadRequest());

        mvc.perform(put("/api/admin/settings/unknown.key")
                        .header("Authorization", "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("value", "1"))))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String divisionAdminToken() throws Exception {
        User admin = userScoped(uniqueBase("opdiv"), RoleCode.DIVISION_ADMIN, div, depot, town);
        divisionAdminProfile(admin, div);
        return adminLoginToken(admin, RoleCode.DIVISION_ADMIN);
    }

    /** Creates a bus number and registers one vehicle on it. */
    private JsonNode createBus(String token, String busNumber, Long depotId, Long townId) throws Exception {
        return createBus(token, createBusNumber(token, busNumber, depotId, townId), depotId, townId);
    }

    private JsonNode createBus(String token, Long busNumberId, Long depotId, Long townId) throws Exception {
        MvcResult result = mvc.perform(post("/api/admin/fleet/buses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(busBody(busNumberId, depotId, townId)))
                .andExpect(status().isCreated())
                .andReturn();
        return om.readTree(result.getResponse().getContentAsString());
    }

    private Long createBusNumber(String token, String busNumber, Long depotId, Long townId) throws Exception {
        MvcResult result = mvc.perform(post("/api/admin/fleet/bus-numbers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(busNumberBody(busNumber, depotId, townId)))
                .andExpect(status().isCreated())
                .andReturn();
        return om.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String busNumberBody(String busNumber, Long depotId, Long townId) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("busNumber", busNumber);
        m.put("busType", "ORDINARY");
        m.put("depotId", depotId);
        m.put("townId", townId);
        return om.writeValueAsString(m);
    }

    private String busBody(Long busNumberId, Long depotId, Long townId) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("busNumberId", busNumberId);
        m.put("capacity", 45);
        m.put("depotId", depotId);
        m.put("townId", townId);
        return om.writeValueAsString(m);
    }

    private JsonNode createStaff(String token, String empCode) throws Exception {
        MvcResult result = mvc.perform(post("/api/admin/staff")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staffBody("Staff " + empCode, empCode, depot.getId())))
                .andExpect(status().isCreated())
                .andReturn();
        return om.readTree(result.getResponse().getContentAsString());
    }

    private String staffBody(String fullName, String empCode, Long depotId) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fullName", fullName);
        m.put("empCode", empCode);
        m.put("designation", "Driver");
        m.put("depotId", depotId);
        return om.writeValueAsString(m);
    }

    private JsonNode createRoute(String token, String codePrefix) throws Exception {
        MvcResult result = createRouteWithCode(token, codePrefix).andExpect(status().isCreated()).andReturn();
        return om.readTree(result.getResponse().getContentAsString());
    }

    private ResultActions createRouteWithCode(String token, String code) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("divisionId", div.getId());
        m.put("code", code);
        m.put("name", "Route " + code);
        m.put("origin", "Sira");
        m.put("destination", "Tumakuru");
        return mvc.perform(post("/api/admin/routes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(m)));
    }

    private JsonNode createTrip(String token, Long routeId, Long busId) throws Exception {
        MvcResult result = mvc.perform(post("/api/admin/trips")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tripBody(routeId, busId)))
                .andExpect(status().isCreated())
                .andReturn();
        return om.readTree(result.getResponse().getContentAsString());
    }

    private String tripBody(Long routeId, Long busId) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("routeId", routeId);
        m.put("busId", busId);
        m.put("tripDate", LocalDate.now().toString());
        m.put("scheduledDeparture", LocalDateTime.now().plusHours(1).withNano(0).toString());
        m.put("scheduledArrival", LocalDateTime.now().plusHours(3).withNano(0).toString());
        return om.writeValueAsString(m);
    }

    private Map<String, Object> stop(int order, String name, String lat, String lon) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("stopOrder", order);
        m.put("stopName", name);
        m.put("latitude", lat);
        m.put("longitude", lon);
        return m;
    }

    private Crew crewWithStaff() {
        Staff staff = new Staff();
        staff.setDepot(depot);
        staff.setDivision(div);
        staff.setCorporation(corp);
        staff.setFullName("Driver One");
        staff.setStatus("ACTIVE");
        staffRepository.save(staff);

        User crewUser = user(uniqueBase("drv"), RoleCode.DRIVER);
        Crew crew = new Crew();
        crew.setStaff(staff);
        crew.setUser(crewUser);
        crew.setBadgeNo(uniqueBase("BDG"));
        crew.setFullName("Driver One");
        crew.setCrewType("DRIVER");
        crew.setStatus("ACTIVE");
        crew.setDutyStatus("OFF_DUTY");
        return crewRepository.save(crew);
    }

    private void awaitAudit(String action) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE action = ?", Integer.class, action);
            if (count != null && count > 0) {
                return;
            }
            Thread.sleep(100);
        }
        fail("Expected an audit row for action=" + action);
    }
}
