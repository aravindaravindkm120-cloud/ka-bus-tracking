package com.kabus.tracking;

import com.kabus.tracking.domain.entity.Corporation;
import com.kabus.tracking.domain.entity.Depot;
import com.kabus.tracking.domain.entity.Division;
import com.kabus.tracking.domain.entity.Town;
import com.kabus.tracking.domain.entity.User;
import com.kabus.tracking.domain.enums.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization with strict role-specific login: admins are limited to their
 * server-side scope, crew cannot reach admin endpoints, admins cannot reach
 * crew endpoints, and an account without a role-profile row cannot assume an
 * admin role.
 */
class AuthorizationScopeTest extends TestSupport {

    @Test
    void superAdmin_seesWholeSystem() throws Exception {
        User admin = user(uniqueBase("super"), RoleCode.SUPER_ADMIN);
        superAdminProfile(admin);
        mvc.perform(get("/api/admin/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminLoginToken(admin, RoleCode.SUPER_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$.scope.wholeSystem").value(true));
    }

    @Test
    void divisionAdmin_isScopedToOwnDivision() throws Exception {
        Corporation corp = corporation();
        Division div = division(corp);
        Depot depot = depot(div);
        Town town = town(depot);
        User admin = userScoped(uniqueBase("divadmin"), RoleCode.DIVISION_ADMIN, div, depot, town);
        divisionAdminProfile(admin, div);

        mvc.perform(get("/api/admin/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminLoginToken(admin, RoleCode.DIVISION_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope.wholeSystem").value(false))
                .andExpect(jsonPath("$.scope.divisionId").value(div.getId()))
                .andExpect(jsonPath("$.scope.divisionIds", hasItem(div.getId().intValue())));
    }

    @Test
    void depotHead_seesOnlyOwnDepot() throws Exception {
        Corporation corp = corporation();
        Division div = division(corp);
        Depot depot = depot(div);
        Town town = town(depot);
        User head = userScoped(uniqueBase("depothead"), RoleCode.DEPOT_HEAD, div, depot, town);
        depotHeadProfile(head, depot);

        mvc.perform(get("/api/admin/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminLoginToken(head, RoleCode.DEPOT_HEAD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope.wholeSystem").value(false))
                .andExpect(jsonPath("$.scope.depotId").value(depot.getId()));
    }

    @Test
    void crew_cannotReachAdminEndpoints() throws Exception {
        User driver = crewUser("driver", "DRIVER");
        mvc.perform(get("/api/admin/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginToken(driver.getUsername(), "Pass@123")))
                .andExpect(status().isForbidden());
    }

    @Test
    void admins_cannotReachCrewEndpoints() throws Exception {
        User admin = user(uniqueBase("super"), RoleCode.SUPER_ADMIN);
        superAdminProfile(admin);
        mvc.perform(get("/api/crew/assignment")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminLoginToken(admin, RoleCode.SUPER_ADMIN)))
                .andExpect(status().isForbidden());
    }

    @Test
    void tripsEndpoint_mapsLazyRelationsWithinTransaction() throws Exception {
        // Regression: DTO mapping must happen inside the transaction (open-in-view
        // is false), so lazy relationships on Trip (route, bus) resolve cleanly.
        Corporation corp = corporation();
        Division div = division(corp);
        Depot depot = depot(div);
        Town town = town(depot);
        com.kabus.tracking.domain.entity.Route r = route(div, "Sira", "Tumakuru");
        com.kabus.tracking.domain.entity.Bus b = bus(depot, town);
        com.kabus.tracking.domain.entity.Trip trip = trip(r, b);
        User admin = user(uniqueBase("tripsuper"), RoleCode.SUPER_ADMIN);
        superAdminProfile(admin);

        mvc.perform(get("/api/admin/trips")
                        .param("date", trip.getTripDate().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminLoginToken(admin, RoleCode.SUPER_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(trip.getId()))
                .andExpect(jsonPath("$.content[0].tripNumber").value(trip.getTripNumber()))
                .andExpect(jsonPath("$.content[0].routeName").value("Sira - Tumakuru"))
                .andExpect(jsonPath("$.content[0].busRegistrationNo").value(b.getRegistrationNo()))
                .andExpect(jsonPath("$.content[0].status").value("SCHEDULED"));
    }

    @Test
    void userWithNoProfile_cannotAssumeAnAdminRole() throws Exception {
        User u = new com.kabus.tracking.domain.entity.User();
        u.setUsername(uniqueBase("norole"));
        u.setFullName("No Role");
        u.setEmail(u.getUsername() + "@example.test");
        u.setPasswordHash(passwordEncoder.encode("Pass@123"));
        u.getRoles().clear();
        userRepository.save(u);

        // Valid credentials but no membership in the requested role table -> 403.
        mvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "email", u.getEmail(),
                                "password", "Pass@123",
                                "requestedRole", "SUPER_ADMIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void divisionAdmin_dataIsLimitedToDivisionDepots() throws Exception {
        Corporation corp = corporation();
        Division div = division(corp);
        Depot depot = depot(div);
        Town town = town(depot);
        User admin = userScoped(uniqueBase("divadmin2"), RoleCode.DIVISION_ADMIN, div, depot, town);
        divisionAdminProfile(admin, div);

        // Two buses: one in-division, one in an unrelated division.
        bus(depot, town);
        Corporation otherCorp = corporation();
        Division otherDiv = division(otherCorp);
        Depot otherDepot = depot(otherDiv);
        Town otherTown = town(otherDepot);
        bus(otherDepot, otherTown);

        mvc.perform(get("/api/admin/live-buses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminLoginToken(admin, RoleCode.DIVISION_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
    }
}