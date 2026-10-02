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
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TOWN_MANAGER strict town isolation: a town manager is limited to exactly one
 * town even when another town shares the same depot (the old short-circuit in
 * {@code ScopeGuard.requireWithin} granted them division-wide access). Verifies
 * town-scoped reads on dashboard/fleet/staff/trips/live/crew, 403 on every
 * cross-town read/write (including crew and bus assignment where the crew/bus
 * belongs to another town of the same depot), that routes stay
 * division-readable but never town-mutable, staff with no town attached are not
 * town-manageable, and that SUPER_ADMIN still sees the whole system.
 */
class TownManagerStrictScopeTest extends TestSupport {

    private static final String AUTH = HttpHeaders.AUTHORIZATION;

    private Division divA;
    private Depot depotA;
    private Town townA1;
    private Town townA2;
    private Route routeA;
    private Bus busA1;
    private Bus busA2;
    private Staff staffA1;
    private Staff staffA2;
    private Crew crewA1;
    private Crew crewA2;
    private Trip tripA1;
    private Trip tripA2;

    private Division divB;
    private Depot depotB;
    private Town townB;
    private Route routeB;
    private Bus busB;
    private Trip tripB;

    private User manager;
    private String token;

    @BeforeEach
    void buildDepotWithTwoTowns() throws Exception {
        Corporation corpA = corporation();
        divA = division(corpA);
        depotA = depot(divA);
        townA1 = town(depotA);
        townA2 = town(depotA);
        routeA = route(divA, "A-Origin", "A-Destination");

        busA1 = bus(depotA, townA1);
        staffA1 = staffInTown(depotA, townA1);
        crewA1 = crewOnStaff(staffA1, "DRIVER");
        tripA1 = trip(routeA, busA1);
        liveLocation(busA1, tripA1, routeA, LiveStatus.LIVE);

        busA2 = bus(depotA, townA2);
        staffA2 = staffInTown(depotA, townA2);
        crewA2 = crewOnStaff(staffA2, "CONDUCTOR");
        tripA2 = trip(routeA, busA2);

        manager = userScoped(uniqueBase("town-manager-a"), RoleCode.TOWN_MANAGER, divA, depotA, townA1);
        townManagerProfile(manager, townA1);
        token = adminLoginToken(manager, RoleCode.TOWN_MANAGER);

        Corporation corpB = corporation();
        divB = division(corpB);
        depotB = depot(divB);
        townB = town(depotB);
        routeB = route(divB, "B-Origin", "B-Destination");
        busB = bus(depotB, townB);
        tripB = trip(routeB, busB);
    }

    private Staff staffInTown(Depot depot, Town town) {
        Staff s = staff(depot);
        s.setTown(town);
        return staffRepository.save(s);
    }

    // ------------------------------------------------------------------
    // Reads are scoped to the one town
    // ------------------------------------------------------------------

    @Test
    void dashboard_isScopedToOwnTown() throws Exception {
        mvc.perform(get("/api/admin/dashboard").header(AUTH, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope.wholeSystem").value(false))
                .andExpect(jsonPath("$.scope.depotId").value(depotA.getId()))
                .andExpect(jsonPath("$.scope.townId").value(townA1.getId()))
                .andExpect(jsonPath("$.depots").value(1))
                .andExpect(jsonPath("$.towns").value(1))
                .andExpect(jsonPath("$.buses").value(1))
                .andExpect(jsonPath("$.staff").value(1))
                .andExpect(jsonPath("$.crew").value(1))
                .andExpect(jsonPath("$.routes").value(1))
                .andExpect(jsonPath("$.busesLive").value(1));
    }

    @Test
    void fleetList_onlyOwnTown() throws Exception {
        mvc.perform(get("/api/admin/fleet/buses").header(AUTH, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(busA1.getId()))
                .andExpect(jsonPath("$.content[0].townId").value(townA1.getId()));
    }

    @Test
    void staffList_onlyOwnTown() throws Exception {
        mvc.perform(get("/api/admin/staff").header(AUTH, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(staffA1.getId()));
    }

    @Test
    void tripsList_onlyOwnTown() throws Exception {
        mvc.perform(get("/api/admin/trips")
                        .param("date", LocalDate.now().toString())
                        .header(AUTH, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(tripA1.getId()));
    }

    @Test
    void liveBuses_onlyOwnTown() throws Exception {
        mvc.perform(get("/api/admin/live-buses").header(AUTH, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].busId").value(busA1.getId()));
    }

    @Test
    void crewList_onlyOwnTown() throws Exception {
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
    // Cross-town reads / writes (same depot, different town)
    // ------------------------------------------------------------------

    @Test
    void crossTownReads_areForbidden() throws Exception {
        forbidden(get("/api/admin/fleet/buses/" + busA2.getId()), token);
        forbidden(get("/api/admin/staff/" + staffA2.getId()), token);
        forbidden(get("/api/admin/trips/" + tripA2.getId()), token);
    }

    @Test
    void crossTownWrites_areForbidden() throws Exception {
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
    // Assignment is town-cross-checked
    // ------------------------------------------------------------------

    @Test
    void assignCrew_crossTownSameDepot_isForbidden() throws Exception {
        // The old depot-only check let this through: crew shares the trip bus depot.
        forbidden(post("/api/admin/trips/" + tripA1.getId() + "/crew")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("crewId", crewA2.getId()))), token);
    }

    @Test
    void assignCrew_ownTown_isAllowed() throws Exception {
        mvc.perform(post("/api/admin/trips/" + tripA1.getId() + "/crew")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("crewId", crewA1.getId())))
                        .header(AUTH, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.crewAssignments", hasSize(1)))
                .andExpect(jsonPath("$.crewAssignments[0].crewId").value(crewA1.getId()));
    }

    @Test
    void assignBus_crossTown_isForbidden() throws Exception {
        forbidden(put("/api/admin/trips/" + tripA1.getId() + "/bus")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("busId", busA2.getId()))), token);
    }

    @Test
    void assignBus_ownTown_isAllowed() throws Exception {
        mvc.perform(put("/api/admin/trips/" + tripA1.getId() + "/bus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("busId", busA1.getId())))
                        .header(AUTH, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.busId").value(busA1.getId()));
    }

    // ------------------------------------------------------------------
    // Creates and cross-town moves
    // ------------------------------------------------------------------

    @Test
    void createBus_ownTown_isAllowed() throws Exception {
        MvcResult createdBusNumber = mvc.perform(post("/api/admin/fleet/bus-numbers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "busNumber", uniqueBase("KA-OWN"),
                                "busType", "ORDINARY",
                                "depotId", depotA.getId(), "townId", townA1.getId())))
                        .header(AUTH, bearer(token)))
                .andExpect(status().isCreated())
                .andReturn();
        mvc.perform(post("/api/admin/fleet/buses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "busNumberId", busNumberId(createdBusNumber),
                                "capacity", 40,
                                "depotId", depotA.getId(), "townId", townA1.getId())))
                        .header(AUTH, bearer(token)))
                .andExpect(status().isCreated());
    }

    @Test
    void createBus_otherTown_isForbidden() throws Exception {
        // busA2 and busB each own a bus number of a town outside this manager's
        // scope, so the body is valid and the scope guard must reject it.
        forbidden(post("/api/admin/fleet/buses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "busNumberId", busA2.getBusNumber().getId(),
                        "capacity", 40,
                        "depotId", depotA.getId(), "townId", townA2.getId()))), token);
        forbidden(post("/api/admin/fleet/buses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "busNumberId", busB.getBusNumber().getId(),
                        "capacity", 40,
                        "depotId", depotB.getId(), "townId", townB.getId()))), token);
    }

    @Test
    void moveBusToOtherTown_isForbidden() throws Exception {
        forbidden(put("/api/admin/fleet/buses/" + busA1.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("capacity", 40, "townId", townA2.getId()))), token);
    }

    @Test
    void createStaff_ownTown_isAllowed() throws Exception {
        mvc.perform(post("/api/admin/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Own Staff", "depotId", depotA.getId(),
                                "townId", townA1.getId())))
                        .header(AUTH, bearer(token)))
                .andExpect(status().isCreated());
    }

    @Test
    void createStaff_otherTownOrNoTown_isForbidden() throws Exception {
        // Another town of the same depot.
        forbidden(post("/api/admin/staff")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("fullName", "Sneaky", "depotId", depotA.getId(),
                        "townId", townA2.getId()))), token);
        // No town attached: the staff member cannot be located inside the scope.
        forbidden(post("/api/admin/staff")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("fullName", "NoTown", "depotId", depotA.getId()))), token);
    }

    @Test
    void moveStaffToOtherTown_isForbidden() throws Exception {
        forbidden(put("/api/admin/staff/" + staffA1.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("fullName", "Updated", "townId", townA2.getId()))), token);
    }

    @Test
    void createRoute_isForbiddenForTownManager() throws Exception {
        // Routes are division-level; a town manager may read but not mutate them.
        forbidden(post("/api/admin/routes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "divisionId", divA.getId(), "code", uniqueBase("RTX"),
                        "name", "Sneaky", "origin", "O", "destination", "D"))), token);
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
                .andExpect(jsonPath("$.buses").value(3))
                .andExpect(jsonPath("$.staff").value(2))
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