package com.kabus.tracking;

import com.kabus.tracking.domain.entity.Corporation;
import com.kabus.tracking.domain.entity.Depot;
import com.kabus.tracking.domain.entity.Division;
import com.kabus.tracking.domain.entity.Town;
import com.kabus.tracking.domain.entity.User;
import com.kabus.tracking.domain.enums.RoleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The read-only organization picker that feeds the Corporation -&gt; Division -&gt;
 * Depot -&gt; Town dropdowns on every admin "Add" form.
 *
 * <p>These endpoints exist because the organization <em>management</em> endpoints
 * deny scoped roles, yet a depot head adding staff still has to see the names of
 * the levels it may assign. The property under test is therefore twofold: a
 * scoped role must be able to load its own branch, and it must never be able to
 * widen that branch by passing another id as the parent filter.</p>
 */
class OrgPickerScopeTest extends TestSupport {

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

    private String depotHeadToken;
    private String divisionManagerToken;
    private String townManagerToken;

    @BeforeEach
    void buildTwoCorporationsEachWithDepotsAndTowns() throws Exception {
        corpA = corporation();
        divA = division(corpA);
        depotA1 = depot(divA);
        townA1 = town(depotA1);
        depotA2 = depot(divA);
        townA2 = town(depotA2);

        corpB = corporation();
        divB = division(corpB);
        depotB = depot(divB);
        townB = town(depotB);

        // A second division inside corporation A, so "whole division" and "one
        // depot" are genuinely different answers.
        Division divA2 = division(corpA);
        Town townA3 = town(depot(divA2));

        User head = userScoped(uniqueBase("picker-head"), RoleCode.DEPOT_HEAD, divA, depotA1, townA1);
        depotHeadProfile(head, depotA1);
        depotHeadToken = adminLoginToken(head, RoleCode.DEPOT_HEAD);

        User manager = userScoped(uniqueBase("picker-mgr"), RoleCode.DIVISION_MANAGER, divA, null, null);
        divisionManagerProfile(manager, divA);
        divisionManagerToken = adminLoginToken(manager, RoleCode.DIVISION_MANAGER);

        User townBoss = userScoped(uniqueBase("picker-town"), RoleCode.TOWN_MANAGER, divA, depotA1, townA1);
        townManagerProfile(townBoss, townA1);
        townManagerToken = adminLoginToken(townBoss, RoleCode.TOWN_MANAGER);

        assert townA3 != null;
    }

    // ------------------------------------------------------------------
    // DEPOT_HEAD sees exactly one node per level
    // ------------------------------------------------------------------

    @Test
    void depotHead_seesOwnCorporationOnly() throws Exception {
        mvc.perform(get("/api/admin/org-picker/corporations").header(AUTH, bearer(depotHeadToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(corpA.getId()))
                .andExpect(jsonPath("$[0].code").value(corpA.getCode()))
                .andExpect(jsonPath("$[0].name").value(corpA.getName()));
    }

    @Test
    void depotHead_seesOwnDivisionOnly() throws Exception {
        mvc.perform(get("/api/admin/org-picker/divisions")
                        .param("parentId", String.valueOf(corpA.getId()))
                        .header(AUTH, bearer(depotHeadToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(divA.getId()))
                .andExpect(jsonPath("$[0].corporationId").value(corpA.getId()));
    }

    @Test
    void depotHead_seesOwnDepotOnly() throws Exception {
        mvc.perform(get("/api/admin/org-picker/depots")
                        .param("parentId", String.valueOf(divA.getId()))
                        .header(AUTH, bearer(depotHeadToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(depotA1.getId()))
                .andExpect(jsonPath("$[0].divisionId").value(divA.getId()));
    }

    @Test
    void depotHead_seesTownsOfOwnDepotOnly() throws Exception {
        mvc.perform(get("/api/admin/org-picker/towns")
                        .param("parentId", String.valueOf(depotA1.getId()))
                        .header(AUTH, bearer(depotHeadToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(townA1.getId()))
                .andExpect(jsonPath("$[0].depotId").value(depotA1.getId()));
    }

    // ------------------------------------------------------------------
    // The parent filter narrows within scope but can never widen it
    // ------------------------------------------------------------------

    @Test
    void depotHead_cannotWidenViaForeignParent() throws Exception {
        forbidden(get("/api/admin/org-picker/divisions")
                .param("parentId", String.valueOf(corpB.getId())), depotHeadToken);
        forbidden(get("/api/admin/org-picker/depots")
                .param("parentId", String.valueOf(divB.getId())), depotHeadToken);
        forbidden(get("/api/admin/org-picker/towns")
                .param("parentId", String.valueOf(depotB.getId())), depotHeadToken);
    }

    @Test
    void depotHead_cannotSeeSiblingDepotInSameDivision() throws Exception {
        // Their own division is a legitimate parent, so this is not a 403 - but
        // the result must be clamped down to the single depot they may use.
        mvc.perform(get("/api/admin/org-picker/depots")
                        .param("parentId", String.valueOf(divA.getId()))
                        .header(AUTH, bearer(depotHeadToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(depotA1.getId()))
                .andExpect(jsonPath("$[?(@.id == " + depotA2.getId() + ")]").doesNotExist());

        // A sibling depot is out of scope as a parent, so this must be refused.
        forbidden(get("/api/admin/org-picker/towns")
                .param("parentId", String.valueOf(depotA2.getId())), depotHeadToken);
        forbidden(get("/api/admin/org-picker/depots")
                .param("parentId", String.valueOf(depotA2.getId())), depotHeadToken);
    }

    // ------------------------------------------------------------------
    // DIVISION_MANAGER / TOWN_MANAGER
    // ------------------------------------------------------------------

    @Test
    void divisionManager_seesWholeDivision() throws Exception {
        mvc.perform(get("/api/admin/org-picker/corporations").header(AUTH, bearer(divisionManagerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(corpA.getId()));

        mvc.perform(get("/api/admin/org-picker/divisions")
                        .param("parentId", String.valueOf(corpA.getId()))
                        .header(AUTH, bearer(divisionManagerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(divA.getId()));

        mvc.perform(get("/api/admin/org-picker/depots")
                        .param("parentId", String.valueOf(divA.getId()))
                        .header(AUTH, bearer(divisionManagerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.id == " + depotA1.getId() + ")]").exists())
                .andExpect(jsonPath("$[?(@.id == " + depotA2.getId() + ")]").exists());
    }

    @Test
    void divisionManager_cannotSeeOtherCorporations() throws Exception {
        forbidden(get("/api/admin/org-picker/divisions")
                .param("parentId", String.valueOf(corpB.getId())), divisionManagerToken);
        forbidden(get("/api/admin/org-picker/depots")
                .param("parentId", String.valueOf(divB.getId())), divisionManagerToken);
    }

    @Test
    void townManager_seesExactlyOneNodePerLevel() throws Exception {
        mvc.perform(get("/api/admin/org-picker/corporations").header(AUTH, bearer(townManagerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/admin/org-picker/divisions")
                        .param("parentId", String.valueOf(corpA.getId()))
                        .header(AUTH, bearer(townManagerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(divA.getId()));
        mvc.perform(get("/api/admin/org-picker/depots")
                        .param("parentId", String.valueOf(divA.getId()))
                        .header(AUTH, bearer(townManagerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(depotA1.getId()));
        mvc.perform(get("/api/admin/org-picker/towns")
                        .param("parentId", String.valueOf(depotA1.getId()))
                        .header(AUTH, bearer(townManagerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(townA1.getId()));
    }

    // ------------------------------------------------------------------
    // No parent filter at all still stays inside scope
    // ------------------------------------------------------------------

    @Test
    void omittedParent_returnsOnlyInScopeNodes() throws Exception {
        mvc.perform(get("/api/admin/org-picker/divisions").header(AUTH, bearer(depotHeadToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(divA.getId()));

        mvc.perform(get("/api/admin/org-picker/depots").header(AUTH, bearer(depotHeadToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(depotA1.getId()));

        mvc.perform(get("/api/admin/org-picker/towns").header(AUTH, bearer(depotHeadToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(townA1.getId()));
    }

    // ------------------------------------------------------------------
    // SUPER_ADMIN regression: the picker must not narrow the whole-system view
    // ------------------------------------------------------------------

    @Test
    void superAdmin_seesWholeSystem() throws Exception {
        User sup = user(uniqueBase("picker-super"), RoleCode.SUPER_ADMIN);
        superAdminProfile(sup);
        String superToken = adminLoginToken(sup, RoleCode.SUPER_ADMIN);

        mvc.perform(get("/api/admin/org-picker/corporations").header(AUTH, bearer(superToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mvc.perform(get("/api/admin/org-picker/divisions")
                        .param("parentId", String.valueOf(corpA.getId()))
                        .header(AUTH, bearer(superToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mvc.perform(get("/api/admin/org-picker/depots")
                        .param("parentId", String.valueOf(divA.getId()))
                        .header(AUTH, bearer(superToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    // ------------------------------------------------------------------
    // Unauthenticated access stays closed
    // ------------------------------------------------------------------

    @Test
    void picker_requiresAuthentication() throws Exception {
        mvc.perform(get("/api/admin/org-picker/corporations"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/org-picker/depots"))
                .andExpect(status().isUnauthorized());
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