package com.kabus.tracking;

import com.kabus.tracking.domain.entity.Bus;
import com.kabus.tracking.domain.entity.Corporation;
import com.kabus.tracking.domain.entity.Crew;
import com.kabus.tracking.domain.entity.Depot;
import com.kabus.tracking.domain.entity.Division;
import com.kabus.tracking.domain.entity.Route;
import com.kabus.tracking.domain.entity.Staff;
import com.kabus.tracking.domain.entity.Town;
import com.kabus.tracking.domain.entity.Trip;
import com.kabus.tracking.domain.entity.User;
import com.kabus.tracking.domain.enums.LiveStatus;
import com.kabus.tracking.domain.enums.RoleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DEPOT_HEAD isolation: a depot head is limited to exactly one depot even when
 * the division has other depots. Verifies list scoping (fleet/staff/trips/live/
 * crew), cross-depot and cross-division read/write rejection, that routes stay
 * division-readable but not depot-mutable, forbidden SUPER_ADMIN surfaces, and
 * that SUPER_ADMIN still sees the whole system.
 */
class DepotHeadScopeTest extends TestSupport {

    private static final String AUTH = HttpHeaders.AUTHORIZATION;

    private Corporation corpA;
    private Corporation corpB;
    private Division divA;
    private Division divB;
    private Depot depotA1;
    private Depot depotA2;
    private Depot depotB;
    private Town townA1;
    private Town townA2;
    private Town townB;
    private Route routeA;
    private Route routeB;
    private Bus busA1;
    private Bus busA2;
    private Bus busB;
    private Staff staffA1;
    private Staff staffA2;
    private Crew crewA1;
    private Crew crewA2;
    private Trip tripA1;
    private Trip tripA2;
    private Trip tripB;
    private User head;
    private String token;

    @BeforeEach
    void buildDivisionWithTwoDepots() throws Exception {
        corpA = corporation();
        divA = division(corpA);
        depotA1 = depot(divA);
        townA1 = town(depotA1);
        depotA2 = depot(divA);
        townA2 = town(depotA2);
        routeA = route(divA, "A-Origin", "A-Destination");

        busA1 = bus(depotA1, townA1);
        staffA1 = staff(depotA1);
        crewA1 = crewOnStaff(staffA1, "DRIVER");
        tripA1 = trip(routeA, busA1);
        liveLocation(busA1, tripA1, routeA, LiveStatus.LIVE);

        busA2 = bus(depotA2, townA2);
        staffA2 = staff(depotA2);
        crewA2 = crewOnStaff(staffA2, "CONDUCTOR");
        tripA2 = trip(routeA, busA2);
        liveLocation(busA2, tripA2, routeA, LiveStatus.LIVE);

        head = userScoped(uniqueBase("depothead-a"), RoleCode.DEPOT_HEAD, divA, depotA1, townA1);
        depotHeadProfile(head, depotA1);
        token = adminLoginToken(head, RoleCode.DEPOT_HEAD);

        corpB = corporation();
        divB = division(corpB);
        depotB = depot(divB);
        townB = town(depotB);
        routeB = route(divB, "B-Origin", "B-Destination");
        busB = bus(depotB, townB);
        tripB = trip(routeB, busB);
    }

    // ------------------------------------------------------------------
    // Reads are scoped to the one depot
    // ------------------------------------------------------------------

    @Test
    void dashboard_isScopedToOwnDepot() throws Exception {
        mvc.perform(get("/api/admin/dashboard").header(AUTH, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope.wholeSystem").value(false))
                .andExpect(jsonPath("$.scope.depotId").value(depotA1.getId()))
                .andExpect(jsonPath("$.corporations").value(0))
                .andExpect(jsonPath("$.depots").value(1))
                .andExpect(jsonPath("$.towns").value(1))
                .andExpect(jsonPath("$.buses").value(1))
                .andExpect(jsonPath("$.staff").value(1))
                .andExpect(jsonPath("$.crew").value(1))
                .andExpect(jsonPath("$.busesLive").value(1));
    }

    @Test
    void fleetList_onlyOwnDepot() throws Exception {
        mvc.perform(get("/api/admin/fleet/buses").header(AUTH, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(busA1.getId()))
                .andExpect(jsonPath("$.content[0].depotId").value(depotA1.getId()));
    }

    @Test
    void staffList_onlyOwnDepot() throws Exception {
        mvc.perform(get("/api/admin/staff").header(AUTH, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(staffA1.getId()));
    }

    @Test
    void tripsList_onlyOwnDepot() throws Exception {
        mvc.perform(get("/api/admin/trips")
                        .param("date", LocalDate.now().toString())
                        .header(AUTH, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(tripA1.getId()))
                .andExpect(jsonPath("$.content[0].depotId").value(depotA1.getId()));
    }

    @Test
    void liveBuses_onlyOwnDepot() throws Exception {
        mvc.perform(get("/api/admin/live-buses").header(AUTH, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].busId").value(busA1.getId()));
    }

    @Test
    void crewList_onlyOwnDepot() throws Exception {
        mvc.perform(get("/api/admin/crew").header(AUTH, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].crewId").value(crewA1.getId()));
    }

    @Test
    void routesList_readsDivisionRoutes() throws Exception {
        mvc.perform(get("/api/admin/routes").header(AUTH, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(routeA.getId()));
    }

    // ------------------------------------------------------------------
    // Cross-depot reads / writes
    // ------------------------------------------------------------------

    @Test
    void crossDepotReads_areForbidden() throws Exception {
        forbidden(get("/api/admin/fleet/buses/" + busA2.getId()), token);
        forbidden(get("/api/admin/staff/" + staffA2.getId()), token);
        forbidden(get("/api/admin/trips/" + tripA2.getId()), token);
    }

    @Test
    void crossDepotWrites_areForbidden() throws Exception {
        forbidden(put("/api/admin/fleet/buses/" + busA2.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("capacity", 40))), token);
        forbidden(patch("/api/admin/fleet/buses/" + busA2.getId() + "/enabled")
                .param("enabled", "false"), token);
        forbidden(put("/api/admin/staff/" + staffA2.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("fullName", "Updated"))), token);
        forbidden(patch("/api/admin/staff/" + staffA2.getId() + "/status")
                .param("status", "INACTIVE"), token);
        forbidden(patch("/api/admin/trips/" + tripA2.getId() + "/status")
                .param("status", "RUNNING"), token);
        forbidden(put("/api/admin/trips/" + tripA2.getId() + "/bus")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("busId", busA1.getId()))), token);
        forbidden(post("/api/admin/trips/" + tripA2.getId() + "/crew")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("crewId", crewA1.getId()))), token);
    }

    // ------------------------------------------------------------------
    // Cross-division and IDOR
    // ------------------------------------------------------------------

    @Test
    void crossDivisionReads_areForbidden() throws Exception {
        forbidden(get("/api/admin/fleet/buses/" + busB.getId()), token);
        forbidden(get("/api/admin/routes/" + routeB.getId()), token);
        forbidden(get("/api/admin/trips/" + tripB.getId()), token);
    }

    @Test
    void createBusOutsideOwnDepot_isForbidden() throws Exception {
        // Referencing a real bus number of the foreign depot keeps the request
        // body valid, so the rejection comes from the scope guard rather than
        // from bean validation.
        forbidden(post("/api/admin/fleet/buses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "busNumberId", busA2.getBusNumber().getId(),
                        "capacity", 40,
                        "depotId", depotA2.getId(), "townId", townA2.getId()))), token);
        forbidden(post("/api/admin/fleet/buses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "busNumberId", busB.getBusNumber().getId(),
                        "capacity", 40,
                        "depotId", depotB.getId(), "townId", townB.getId()))), token);
    }

    @Test
    void createStaffOutsideOwnDepot_isForbidden() throws Exception {
        forbidden(post("/api/admin/staff")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("fullName", "Sneaky", "depotId", depotA2.getId()))), token);
    }

    @Test
    void createRoute_isForbiddenForDepotHead() throws Exception {
        // Routes are division-level; a depot head may read but not mutate them.
        forbidden(post("/api/admin/routes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "divisionId", divA.getId(), "code", uniqueBase("RTX"),
                        "name", "Sneaky", "origin", "O", "destination", "D"))), token);
    }

    @Test
    void depotHead_canCreateWithinOwnDepot() throws Exception {
        // A depot head defines a bus number, then registers a vehicle on it.
        MvcResult createdBusNumber = mvc.perform(post("/api/admin/fleet/bus-numbers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "busNumber", uniqueBase("KA-OWN"),
                                "busType", "ORDINARY",
                                "depotId", depotA1.getId(), "townId", townA1.getId())))
                        .header(AUTH, bearer(token)))
                .andExpect(status().isCreated())
                .andReturn();

        mvc.perform(post("/api/admin/fleet/buses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "busNumberId", busNumberId(createdBusNumber),
                                "capacity", 40,
                                "depotId", depotA1.getId(), "townId", townA1.getId())))
                        .header(AUTH, bearer(token)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/admin/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Own Staff", "depotId", depotA1.getId())))
                        .header(AUTH, bearer(token)))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------
    // SUPER_ADMIN-only surfaces
    // ------------------------------------------------------------------

    @Test
    void superAdminOnlyEndpoints_areForbiddenForDepotHead() throws Exception {
        forbidden(get("/api/admin/audit-logs"), token);
        forbidden(get("/api/admin/settings"), token);
        forbidden(get("/api/admin/users"), token);
        forbidden(get("/api/admin/organizations/corporations"), token);
        forbidden(get("/api/admin/organizations/depots")
                .param("parentId", String.valueOf(divA.getId())), token);
    }

    // ------------------------------------------------------------------
    // SUPER_ADMIN regression
    // ------------------------------------------------------------------

    @Test
    void superAdmin_stillSeesWholeSystem() throws Exception {
        User sup = user(uniqueBase("super"), RoleCode.SUPER_ADMIN);
        superAdminProfile(sup);
        String superToken = adminLoginToken(sup, RoleCode.SUPER_ADMIN);

        mvc.perform(get("/api/admin/dashboard").header(AUTH, bearer(superToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope.wholeSystem").value(true))
                .andExpect(jsonPath("$.corporations").value(2))
                .andExpect(jsonPath("$.divisions").value(2))
                .andExpect(jsonPath("$.depots").value(3))
                .andExpect(jsonPath("$.buses").value(3))
                .andExpect(jsonPath("$.routes").value(2));

        mvc.perform(get("/api/admin/fleet/buses/" + busA2.getId()).header(AUTH, bearer(superToken)))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------

    private String bearer(String token) {
        return "Bearer " + token;
    }

    
    private void forbidden(MockHttpServletRequestBuilder request, String token) throws Exception {
        mvc.perform(request.header(AUTH, bearer(token)))
                .andExpect(status().isForbidden());
    }
}
