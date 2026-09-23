package com.kabus.tracking;

import com.fasterxml.jackson.databind.JsonNode;
import com.kabus.tracking.domain.entity.Corporation;
import com.kabus.tracking.domain.entity.Depot;
import com.kabus.tracking.domain.entity.Division;
import com.kabus.tracking.domain.entity.Town;
import com.kabus.tracking.domain.entity.User;
import com.kabus.tracking.domain.enums.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * STRICT role-specific login rules:
 * - 401 for invalid credentials,
 * - 403 for valid credentials but the requested role is not this account's,
 * - the JWT carries exactly one role + its scope,
 * - one email cannot be reused to assume another role.
 */
class StrictRoleLoginTest extends TestSupport {

    private User depotHeadWithProfile() {
        Corporation corp = corporation();
        Division div = division(corp);
        Depot depot = depot(div);
        Town town = town(depot);
        User head = userScoped(uniqueBase("head"), RoleCode.DEPOT_HEAD, div, depot, town);
        depotHeadProfile(head, depot);
        return head;
    }

    private String adminLoginRaw(String email, String password, String requestedRole) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "email", email,
                                "password", password,
                                "requestedRole", requestedRole))))
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    @Test
    void correctRole_loginSucceeds_withExactlyThatRole() throws Exception {
        User head = depotHeadWithProfile();
        mvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "email", head.getEmail(),
                                "password", "Pass@123",
                                "requestedRole", "DEPOT_HEAD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.roles", hasItem("DEPOT_HEAD")))
                .andExpect(jsonPath("$.user.roles.length()").value(1))
                .andExpect(jsonPath("$.user.depotId").value(head.getDepot().getId()))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void wrongRequestedRole_isForbidden() throws Exception {
        User head = depotHeadWithProfile();
        for (String wrong : new String[]{"DIVISION_ADMIN", "DIVISION_MANAGER", "TOWN_MANAGER", "SUPER_ADMIN"}) {
            mvc.perform(post("/api/auth/admin/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(Map.of(
                                    "email", head.getEmail(),
                                    "password", "Pass@123",
                                    "requestedRole", wrong))))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void invalidPassword_isUnauthorized() throws Exception {
        User head = depotHeadWithProfile();
        mvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "email", head.getEmail(),
                                "password", "wrong-password",
                                "requestedRole", "DEPOT_HEAD"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownEmail_isUnauthorized_notForbidden() throws Exception {
        mvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "email", "nobody@example.test",
                                "password", "whatever",
                                "requestedRole", "DEPOT_HEAD"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unsupportedRequestedRole_isBadRequest() throws Exception {
        User head = depotHeadWithProfile();
        mvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "email", head.getEmail(),
                                "password", "Pass@123",
                                "requestedRole", "DRIVER"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sameEmail_cannotAssumeAnotherRoleWithACrewOrAdminToken() throws Exception {
        User head = depotHeadWithProfile();
        String depotToken = adminLoginToken(head, RoleCode.DEPOT_HEAD);

        // The same identity cannot talk to SUPER_ADMIN-only features.
        mvc.perform(get("/api/admin/ads/campaigns")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + depotToken))
                .andExpect(status().isForbidden());
        // ... and cannot enter any crew area.
        mvc.perform(get("/api/crew/assignment")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + depotToken))
                .andExpect(status().isForbidden());
        // ... while its own role works fine.
        mvc.perform(get("/api/admin/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + depotToken))
                .andExpect(status().isOk());
    }

    @Test
    void crewLogin_rejectsNonCrewAccount_evenWithValidPassword() throws Exception {
        User head = depotHeadWithProfile();
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "username", head.getUsername(),
                                "password", "Pass@123"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void crewLogin_tokenCarriesOnlyCrewRole() throws Exception {
        User driver = crewUser("driver", "DRIVER");
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "username", driver.getUsername(),
                                "password", "Pass@123",
                                "deviceId", "DEV-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.roles.length()").value(1))
                .andExpect(jsonPath("$.user.roles", hasItem("DRIVER")));
    }

    @Test
    void refresh_keepsTheSameRole_andRevocationBlocksIt() throws Exception {
        User head = depotHeadWithProfile();
        MvcResult login = mvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "email", head.getEmail(),
                                "password", "Pass@123",
                                "requestedRole", "DEPOT_HEAD"))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tokens = om.readTree(login.getResponse().getContentAsString());
        String refresh = tokens.get("refreshToken").asText();

        mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("refreshToken", refresh))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        // Now drop the membership: the refresh that used the (rotated) token would
        // be gone; simulate by dropping the profile directly and refreshing again.
        // (The previous refresh was revoked, so capture a fresh one.)
        refresh = om.readTree(mvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "email", head.getEmail(),
                                "password", "Pass@123",
                                "requestedRole", "DEPOT_HEAD"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("refreshToken").asText();

        jdbc.execute("DELETE FROM depot_heads WHERE user_id = " + head.getId());

        mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("refreshToken", refresh))))
                .andExpect(status().isForbidden());
    }

    @Test
    void revokedMembership_invalidatesExistingAccessToken() throws Exception {
        User head = depotHeadWithProfile();
        String token = adminLoginToken(head, RoleCode.DEPOT_HEAD);
        mvc.perform(get("/api/admin/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        jdbc.execute("DELETE FROM depot_heads WHERE user_id = " + head.getId());

        mvc.perform(get("/api/admin/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}