package com.kabus.tracking;

import com.fasterxml.jackson.databind.JsonNode;
import com.kabus.tracking.domain.entity.Corporation;
import com.kabus.tracking.domain.entity.Depot;
import com.kabus.tracking.domain.entity.Division;
import com.kabus.tracking.domain.entity.Town;
import com.kabus.tracking.domain.entity.User;
import com.kabus.tracking.domain.enums.RoleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PHASE 3: SUPER_ADMIN admin-user + role management.
 *
 * <p>Verifies role/scope correctness, server-side authorization (403 for other
 * admin roles, 401 unauthenticated), immediate revocation on disable / role
 * change, secure password reset and audit logging with the real actor.</p>
 */
class AdminUserManagementTest extends TestSupport {

    private static final String PASSWORD = "Secret@123";

    private String superToken;
    private Long superId;
    private Corporation corp;
    private Division div;
    private Depot depot;
    private Town town;

    @BeforeEach
    void setUpAdmin() throws Exception {
        User sa = user(uniqueBase("sa"), RoleCode.SUPER_ADMIN);
        superAdminProfile(sa);
        superId = sa.getId();
        superToken = adminLoginToken(sa, RoleCode.SUPER_ADMIN);

        corp = corporation();
        div = division(corp);
        depot = depot(div);
        town = town(depot);
    }

    // ------------------------------------------------------------------
    // Happy path: one account per managed role, correct scope binding
    // ------------------------------------------------------------------

    @Test
    void createsEachManagedRoleWithCorrectScope() throws Exception {
        String divAdmin = uniqueBase("divadmin");
        JsonNode a = create(divAdmin, "DIVISION_ADMIN", div.getId(), null, null);
        assertEquals("DIVISION_ADMIN", a.get("role").asText());
        assertEquals(div.getId(), a.get("divisionId").asLong());
        assertEquals(div.getName(), a.get("divisionName").asText());
        assertTrue(divisionAdminProfileRepository.findByUserId(a.get("id").asLong()).isPresent());
        assertNotNull(adminLoginToken(divAdmin + "@managed.test", PASSWORD, RoleCode.DIVISION_ADMIN));

        String divManager = uniqueBase("divmanager");
        JsonNode b = create(divManager, "DIVISION_MANAGER", div.getId(), null, null);
        assertEquals("DIVISION_MANAGER", b.get("role").asText());
        assertTrue(divisionManagerProfileRepository.findByUserId(b.get("id").asLong()).isPresent());

        String depotHead = uniqueBase("depothead");
        JsonNode c = create(depotHead, "DEPOT_HEAD", null, depot.getId(), null);
        assertEquals("DEPOT_HEAD", c.get("role").asText());
        assertEquals(depot.getId(), c.get("depotId").asLong());
        assertEquals(depot.getName(), c.get("depotName").asText());
        assertTrue(depotHeadProfileRepository.findByUserId(c.get("id").asLong()).isPresent());

        String townManager = uniqueBase("townmanager");
        JsonNode d = create(townManager, "TOWN_MANAGER", null, null, town.getId());
        assertEquals("TOWN_MANAGER", d.get("role").asText());
        assertEquals(town.getId(), d.get("townId").asLong());
        assertEquals(town.getName(), d.get("townName").asText());
        assertTrue(townManagerProfileRepository.findByUserId(d.get("id").asLong()).isPresent());
    }

    @Test
    void neverExposesPasswordOrHash() throws Exception {
        String uname = uniqueBase("nopw");
        JsonNode body = create(uname, "TOWN_MANAGER", null, null, town.getId());
        assertFalse(body.has("password"), "response must not contain password");
        assertFalse(body.has("passwordHash"), "response must not contain passwordHash");
        assertFalse(body.toString().contains(PASSWORD), "response must not echo the plaintext password");
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    @Test
    void rejectsMissingOrInvalidScope() throws Exception {
        postUser(uniqueBase("x"), "DIVISION_ADMIN", null, null, null)
                .andExpect(status().isBadRequest());

        postUser(uniqueBase("x"), "DEPOT_HEAD", div.getId(), null, null)
                .andExpect(status().isBadRequest());

        postUser(uniqueBase("x"), "TOWN_MANAGER", null, depot.getId(), null)
                .andExpect(status().isBadRequest());

        postUser(uniqueBase("x"), "TOWN_MANAGER", null, null, 9_999_999L)
                .andExpect(status().isBadRequest());

        postUser(uniqueBase("x"), "DIVISION_MANAGER", 9_999_999L, null, null)
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDuplicateUsername() throws Exception {
        String uname = uniqueBase("dup");
        create(uname, "TOWN_MANAGER", null, null, town.getId());
        postUser(uname, "TOWN_MANAGER", null, null, town.getId())
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsSuperAdminRole() throws Exception {
        postUser(uniqueBase("bad"), "SUPER_ADMIN", null, null, null)
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnknownRole() throws Exception {
        postUser(uniqueBase("bad"), "HACKER", null, null, null)
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsWeakPassword() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", uniqueBase("weak"));
        body.put("fullName", "Weak Password");
        body.put("password", "short");
        body.put("role", "TOWN_MANAGER");
        body.put("townId", town.getId());
        mvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------
    // Authorization
    // ------------------------------------------------------------------

    @Test
    void otherAdminRolesCannotManageUsers() throws Exception {
        User divAdmin = user(uniqueBase("other"), RoleCode.DIVISION_ADMIN);
        divisionAdminProfile(divAdmin, div);
        String token = adminLoginToken(divAdmin, RoleCode.DIVISION_ADMIN);

        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueBase("nope"), "TOWN_MANAGER", null, null, town.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueBase("nope"), "TOWN_MANAGER", null, null, town.getId())))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // Disable / role change / password
    // ------------------------------------------------------------------

    @Test
    void disablingAccountBlocksLoginAndInvalidatesAccessToken() throws Exception {
        String uname = uniqueBase("disable");
        JsonNode created = create(uname, "TOWN_MANAGER", null, null, town.getId());
        Long id = created.get("id").asLong();
        String token = adminLoginToken(uname + "@managed.test", PASSWORD, RoleCode.TOWN_MANAGER);

        mvc.perform(patch("/api/admin/users/" + id + "/enabled")
                        .param("enabled", "false")
                        .header("Authorization", "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        // The previously-issued token no longer authenticates (filter re-checks enabled).
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        // Fresh login is rejected too.
        mvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "email", uname + "@managed.test",
                                "password", PASSWORD,
                                "requestedRole", "TOWN_MANAGER"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void roleChangeInvalidatesOldMembership() throws Exception {
        String uname = uniqueBase("switch");
        JsonNode created = create(uname, "DIVISION_ADMIN", div.getId(), null, null);
        Long id = created.get("id").asLong();
        String oldToken = adminLoginToken(uname + "@managed.test", PASSWORD, RoleCode.DIVISION_ADMIN);

        Map<String, Object> change = new LinkedHashMap<>();
        change.put("role", "TOWN_MANAGER");
        change.put("townId", town.getId());
        mvc.perform(patch("/api/admin/users/" + id + "/role")
                        .header("Authorization", "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(change)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TOWN_MANAGER"))
                .andExpect(jsonPath("$.townId").value(town.getId().intValue()));

        assertFalse(divisionAdminProfileRepository.findByUserId(id).isPresent(),
                "old role profile must be removed");
        assertTrue(townManagerProfileRepository.findByUserId(id).isPresent());

        // Old access token is dead (membership no longer exists).
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());

        // Old role can no longer be assumed; the new role can.
        mvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "email", uname + "@managed.test",
                                "password", PASSWORD,
                                "requestedRole", "DIVISION_ADMIN"))))
                .andExpect(status().isForbidden());
        assertNotNull(adminLoginToken(uname + "@managed.test", PASSWORD, RoleCode.TOWN_MANAGER));
    }

    @Test
    void passwordResetReplacesCredential() throws Exception {
        String uname = uniqueBase("reset");
        JsonNode created = create(uname, "TOWN_MANAGER", null, null, town.getId());
        Long id = created.get("id").asLong();
        String newPassword = "Rotated@456";

        MvcResult res = mvc.perform(patch("/api/admin/users/" + id + "/password")
                        .header("Authorization", "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("password", newPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andReturn();
        assertFalse(res.getResponse().getContentAsString().contains(newPassword),
                "reset response must not echo the new password");

        // Old password fails, new password works.
        mvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "email", uname + "@managed.test",
                                "password", PASSWORD,
                                "requestedRole", "TOWN_MANAGER"))))
                .andExpect(status().isUnauthorized());
        assertNotNull(adminLoginToken(uname + "@managed.test", newPassword, RoleCode.TOWN_MANAGER));
    }

    @Test
    void updateChangesDetailsButNotRole() throws Exception {
        String uname = uniqueBase("edit");
        JsonNode created = create(uname, "TOWN_MANAGER", null, null, town.getId());
        Long id = created.get("id").asLong();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fullName", "Renamed Manager");
        body.put("email", "renamed" + suffix() + "@managed.test");
        body.put("phone", "8888888888");
        mvc.perform(put("/api/admin/users/" + id)
                        .header("Authorization", "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Renamed Manager"))
                .andExpect(jsonPath("$.role").value("TOWN_MANAGER"));
    }

    // ------------------------------------------------------------------
    // Audit trail
    // ------------------------------------------------------------------

    @Test
    void mutationsAreAuditedWithRealActor() throws Exception {
        String uname = uniqueBase("audit");
        JsonNode created = create(uname, "TOWN_MANAGER", null, null, town.getId());
        Long id = created.get("id").asLong();

        mvc.perform(patch("/api/admin/users/" + id + "/enabled")
                        .param("enabled", "false")
                        .header("Authorization", "Bearer " + superToken))
                .andExpect(status().isOk());

        awaitAudit("ADMIN_USER_CREATE", superId);
        awaitAudit("ADMIN_USER_DISABLE", superId);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private JsonNode create(String username, String role, Long divisionId, Long depotId, Long townId)
            throws Exception {
        MvcResult result = postUser(username, role, divisionId, depotId, townId)
                .andExpect(status().isCreated())
                .andReturn();
        return om.readTree(result.getResponse().getContentAsString());
    }

    private org.springframework.test.web.servlet.ResultActions postUser(
            String username, String role, Long divisionId, Long depotId, Long townId) throws Exception {
        return mvc.perform(post("/api/admin/users")
                .header("Authorization", "Bearer " + superToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(username, role, divisionId, depotId, townId)));
    }

    private String body(String username, String role, Long divisionId, Long depotId, Long townId)
            throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("username", username);
        m.put("fullName", "Managed " + username);
        m.put("email", username + "@managed.test");
        m.put("phone", "9999999999");
        m.put("password", PASSWORD);
        m.put("role", role);
        if (divisionId != null) {
            m.put("divisionId", divisionId);
        }
        if (depotId != null) {
            m.put("depotId", depotId);
        }
        if (townId != null) {
            m.put("townId", townId);
        }
        return om.writeValueAsString(m);
    }

    private void awaitAudit(String action, Long actorId) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        Integer count = 0;
        while (System.currentTimeMillis() < deadline) {
            count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE action = ? AND user_id = ?",
                    Integer.class, action, actorId);
            if (count != null && count > 0) {
                return;
            }
            Thread.sleep(100);
        }
        fail("Expected an audit row for action=" + action + " with actor=" + actorId + " (found " + count + ")");
    }
}
