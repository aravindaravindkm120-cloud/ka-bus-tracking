package com.kabus.tracking;

import com.kabus.tracking.domain.entity.Bus;
import com.kabus.tracking.domain.entity.Corporation;
import com.kabus.tracking.domain.entity.Crew;
import com.kabus.tracking.domain.entity.Depot;
import com.kabus.tracking.domain.entity.Division;
import com.kabus.tracking.domain.entity.Notification;
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
 * Division-scoped admin isolation (shared by DIVISION_ADMIN and
 * DIVISION_MANAGER): a division-level admin must see and mutate only its own
 * division's data. Builds two independent division trees (A and B) and asserts
 * read scoping, cross-division read/write rejection (including IDOR via body
 * ids), per-user notifications, forbidden SUPER_ADMIN-only surfaces, and that
 * SUPER_ADMIN still sees the whole system.
 */
abstract class DivisionScopedAdminScopeTest extends TestSupport {

    private static final String AUTH = HttpHeaders.AUTHORIZATION;

    /** The division-scoped admin role under test. */
    protected abstract RoleCode role();

    /** Creates the role-profile row that grants {@code user} division scope. */
    protected abstract void assignProfile(User user, Division division);

    private Corporation corpA;
    private Corporation corpB;
    private Division divA;
    private Division divB;
    private Depot depotA;
    private Depot depotB;
    private Town townA;
    private Town townB;
    private Route routeA;
    private Route routeB;
    private Bus busA;
    private Bus busB;
    private Staff staffA;
    private Staff staffB;
    private Crew crewA;
    private Crew crewB;
    private Trip tripA;
    private Trip tripB;
    private User adminA;
    private User adminB;
    private Notification notifB;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void buildTwoDivisions() throws Exception {
        corpA = corporation();
        divA = division(corpA);
        depotA = depot(divA);
        townA = town(depotA);
        routeA = route(divA, "A-Origin", "A-Destination");
        busA = bus(depotA, townA);
        staffA = staff(depotA);
        crewA = crewOnStaff(staffA, "DRIVER");
        tripA = trip(routeA, busA);
        liveLocation(busA, tripA, routeA, LiveStatus.LIVE);
        adminA = userScoped(uniqueBase("divadmin-a"), role(), divA, depotA, townA);
        assignProfile(adminA, divA);

        corpB = corporation();
        divB = division(corpB);
        depotB = depot(divB);
        townB = town(depotB);
        routeB = route(divB, "B-Origin", "B-Destination");
        busB = bus(depotB, townB);
        staffB = staff(depotB);
        crewB = crewOnStaff(staffB, "CONDUCTOR");
        tripB = trip(routeB, busB);
        liveLocation(busB, tripB, routeB, LiveStatus.LIVE);
        adminB = userScoped(uniqueBase("divadmin-b"), role(), divB, depotB, townB);
        assignProfile(adminB, divB);
        notifB = notification(adminB, "B-only notification");

        tokenA = adminLoginToken(adminA, role());
        tokenB = adminLoginToken(adminB, role());
    }

    // ------------------------------------------------------------------
    // Reads are scoped
    // ------------------------------------------------------------------

    @Test
    void dashboard_countsOnlyOwnDivision() throws Exception {
        mvc.perform(get("/api/admin/dashboard").header(AUTH, bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope.wholeSystem").value(false))
                .andExpect(jsonPath("$.scope.divisionId").value(divA.getId()))
                .andExpect(jsonPath("$.corporations").value(0))
                .andExpect(jsonPath("$.divisions").value(1))
                .andExpect(jsonPath("$.depots").value(1))
                .andExpect(jsonPath("$.towns").value(1))
                .andExpect(jsonPath("$.buses").value(1))
                .andExpect(jsonPath("$.routes").value(1))
                .andExpect(jsonPath("$.staff").value(1))
                .andExpect(jsonPath("$.crew").value(1))
                .andExpect(jsonPath("$.busesLive").value(1));
    }

    @Test
    void tripsList_onlyOwnDivision() throws Exception {
        mvc.perform(get("/api/admin/trips")
                        .param("date", LocalDate.now().toString())
                        .header(AUTH, bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(tripA.getId()))
                .andExpect(jsonPath("$.content[0].divisionId").value(divA.getId()));
    }

    @Test
    void fleetList_onlyOwnDivision() throws Exception {
        mvc.perform(get("/api/admin/fleet/buses").header(AUTH, bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(busA.getId()))
                .andExpect(jsonPath("$.content[0].divisionId").value(divA.getId()));
    }

    @Test
    void staffList_onlyOwnDivision() throws Exception {
        mvc.perform(get("/api/admin/staff").header(AUTH, bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(staffA.getId()));
    }

    @Test
    void routesList_onlyOwnDivision() throws Exception {
        mvc.perform(get("/api/admin/routes").header(AUTH, bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(routeA.getId()));
    }

    @Test
    void crewList_onlyOwnDivision() throws Exception {
        mvc.perform(get("/api/admin/crew").header(AUTH, bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].crewId").value(crewA.getId()));
    }

    @Test
    void liveBuses_onlyOwnDivision() throws Exception {
        mvc.perform(get("/api/admin/live-buses").header(AUTH, bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].busId").value(busA.getId()));
    }

    // ------------------------------------------------------------------
    // Cross-division reads
    // ------------------------------------------------------------------

    @Test
    void crossDivisionReads_areForbidden() throws Exception {
        forbidden(get("/api/admin/fleet/buses/" + busB.getId()), tokenA);
        forbidden(get("/api/admin/routes/" + routeB.getId()), tokenA);
        forbidden(get("/api/admin/staff/" + staffB.getId()), tokenA);
        forbidden(get("/api/admin/trips/" + tripB.getId()), tokenA);
    }

    // ------------------------------------------------------------------
    // Cross-division writes
    // ------------------------------------------------------------------

    @Test
    void crossDivisionBusWrites_areForbidden() throws Exception {
        forbidden(put("/api/admin/fleet/buses/" + busB.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("capacity", 40))), tokenA);
        forbidden(patch("/api/admin/fleet/buses/" + busB.getId() + "/enabled")
                .param("enabled", "false"), tokenA);
    }

    @Test
    void crossDivisionRouteWrites_areForbidden() throws Exception {
        forbidden(put("/api/admin/routes/" + routeB.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Updated", "origin", "O", "destination", "D"))), tokenA);
        forbidden(patch("/api/admin/routes/" + routeB.getId() + "/enabled")
                .param("enabled", "false"), tokenA);
        forbidden(put("/api/admin/routes/" + routeB.getId() + "/stops")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("stops", List.of(Map.of(
                        "stopOrder", 1, "stopName", "S1", "latitude", 13.1, "longitude", 77.1))))), tokenA);
    }

    @Test
    void crossDivisionStaffWrites_areForbidden() throws Exception {
        forbidden(put("/api/admin/staff/" + staffB.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("fullName", "Updated"))), tokenA);
        forbidden(patch("/api/admin/staff/" + staffB.getId() + "/status")
                .param("status", "INACTIVE"), tokenA);
    }

    @Test
    void crossDivisionTripWrites_areForbidden() throws Exception {
        forbidden(patch("/api/admin/trips/" + tripB.getId() + "/status")
                .param("status", "RUNNING"), tokenA);
        forbidden(put("/api/admin/trips/" + tripB.getId() + "/bus")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("busId", busA.getId()))), tokenA);
        forbidden(post("/api/admin/trips/" + tripB.getId() + "/crew")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("crewId", crewA.getId()))), tokenA);
    }

    // ------------------------------------------------------------------
    // IDOR via body ids
    // ------------------------------------------------------------------

    @Test
    void createBusInOtherDivisionDepot_isForbidden() throws Exception {
        // busB carries a real bus number of the foreign depot, so the body is
        // valid and the rejection comes from the scope guard.
        forbidden(post("/api/admin/fleet/buses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "busNumberId", busB.getBusNumber().getId(),
                        "capacity", 40,
                        "depotId", depotB.getId(), "townId", townB.getId()))), tokenA);
    }

    @Test
    void createStaffInOtherDivisionDepot_isForbidden() throws Exception {
        forbidden(post("/api/admin/staff")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("fullName", "Sneaky", "depotId", depotB.getId()))), tokenA);
    }

    @Test
    void createRouteInOtherDivision_isForbidden() throws Exception {
        forbidden(post("/api/admin/routes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "divisionId", divB.getId(), "code", uniqueBase("RTX"),
                        "name", "Sneaky", "origin", "O", "destination", "D"))), tokenA);
    }

    @Test
    void createTripUsingOtherDivisionObjects_isForbidden() throws Exception {
        forbidden(post("/api/admin/trips")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "routeId", routeB.getId(), "busId", busB.getId(),
                        "tripDate", LocalDate.now().toString(),
                        "scheduledDeparture", LocalDateTime.now().toString(),
                        "scheduledArrival", LocalDateTime.now().plusHours(1).toString()))), tokenA);
    }

    @Test
    void createTripMixingDivisions_isRejected() throws Exception {
        // routeA + busB: bus/route must belong to the same division -> 400.
        mvc.perform(post("/api/admin/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "routeId", routeA.getId(), "busId", busB.getId(),
                                "tripDate", LocalDate.now().toString(),
                                "scheduledDeparture", LocalDateTime.now().toString(),
                                "scheduledArrival", LocalDateTime.now().plusHours(1).toString())))
                        .header(AUTH, bearer(tokenA)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void assignOtherDivisionBusOrCrew_isRejected() throws Exception {
        mvc.perform(put("/api/admin/trips/" + tripA.getId() + "/bus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("busId", busB.getId())))
                        .header(AUTH, bearer(tokenA)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/trips/" + tripA.getId() + "/crew")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("crewId", crewB.getId())))
                        .header(AUTH, bearer(tokenA)))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------
    // Staff linking scope + orphan crew (regression hardening)
    // ------------------------------------------------------------------

    @Test
    void cannotLinkOutOfScopeUserToStaff() throws Exception {
        User userB = userScoped(uniqueBase("linkb"), RoleCode.DIVISION_ADMIN, divB, depotB, townB);
        forbidden(post("/api/admin/staff")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "fullName", "Linker", "depotId", depotA.getId(), "userId", userB.getId()))), tokenA);
    }

    @Test
    void cannotAssignCrewWithNoOrgByScopedAdmin() throws Exception {
        User orphan = user(uniqueBase("orphan"), RoleCode.DRIVER);
        Crew orphanCrew = crew(orphan, "DRIVER");
        forbidden(post("/api/admin/trips/" + tripA.getId() + "/crew")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("crewId", orphanCrew.getId()))), tokenA);
    }

    @Test
    void divisionAdmin_canCreateWithinOwnScope() throws Exception {
        MvcResult createdBusNumber = mvc.perform(post("/api/admin/fleet/bus-numbers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "busNumber", uniqueBase("KA-OWN"),
                                "busType", "ORDINARY",
                                "depotId", depotA.getId(), "townId", townA.getId())))
                        .header(AUTH, bearer(tokenA)))
                .andExpect(status().isCreated())
                .andReturn();
        mvc.perform(post("/api/admin/fleet/buses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "busNumberId", busNumberId(createdBusNumber),
                                "capacity", 40,
                                "depotId", depotA.getId(), "townId", townA.getId())))
                        .header(AUTH, bearer(tokenA)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/admin/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "divisionId", divA.getId(), "code", uniqueBase("RT-OWN"),
                                "name", "Own", "origin", "O", "destination", "D")))
                        .header(AUTH, bearer(tokenA)))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------
    // Notifications are per-user
    // ------------------------------------------------------------------

    @Test
    void notifications_areScopedToTheUser() throws Exception {
        mvc.perform(get("/api/admin/notifications").header(AUTH, bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        notification(adminA, "A-only notification");
        mvc.perform(get("/api/admin/notifications").header(AUTH, bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("A-only notification"));

        forbidden(patch("/api/admin/notifications/" + notifB.getId() + "/read"), tokenA);
    }

    // ------------------------------------------------------------------
    // SUPER_ADMIN-only surfaces
    // ------------------------------------------------------------------

    @Test
    void superAdminOnlyEndpoints_areForbiddenForDivisionAdmin() throws Exception {
        forbidden(get("/api/admin/audit-logs"), tokenA);
        forbidden(get("/api/admin/settings"), tokenA);
        forbidden(put("/api/admin/settings/live.threshold.live_seconds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("value", "30"))), tokenA);
        forbidden(get("/api/admin/users"), tokenA);
        forbidden(post("/api/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "username", uniqueBase("newu"), "fullName", "New User",
                        "password", "Pass@123", "role", "DIVISION_ADMIN",
                        "divisionId", divA.getId()))), tokenA);
        forbidden(get("/api/admin/organizations/corporations"), tokenA);
        forbidden(post("/api/admin/organizations/corporations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("code", uniqueBase("CX"), "name", "Sneaky Corp"))), tokenA);
        forbidden(get("/api/admin/organizations/divisions")
                .param("parentId", String.valueOf(corpA.getId())), tokenA);
        forbidden(post("/api/admin/organizations/divisions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "corporationId", corpA.getId(), "code", uniqueBase("DX"), "name", "Sneaky Div"))), tokenA);
        forbidden(get("/api/admin/organizations/depots")
                .param("parentId", String.valueOf(divA.getId())), tokenA);
        forbidden(get("/api/admin/organizations/towns")
                .param("parentId", String.valueOf(depotA.getId())), tokenA);
    }

    // ------------------------------------------------------------------
    // SUPER_ADMIN regression: whole system still visible
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
                .andExpect(jsonPath("$.depots").value(2))
                .andExpect(jsonPath("$.buses").value(2))
                .andExpect(jsonPath("$.routes").value(2));

        mvc.perform(get("/api/admin/fleet/buses/" + busB.getId()).header(AUTH, bearer(superToken)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/trips/" + tripB.getId()).header(AUTH, bearer(superToken)))
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
