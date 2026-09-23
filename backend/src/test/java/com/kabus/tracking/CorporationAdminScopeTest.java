package com.kabus.tracking;

import com.fasterxml.jackson.databind.JsonNode;
import com.kabus.tracking.domain.entity.Corporation;
import com.kabus.tracking.domain.entity.Division;
import com.kabus.tracking.domain.entity.Route;
import com.kabus.tracking.domain.entity.User;
import com.kabus.tracking.domain.enums.RoleCode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Corporation-scoped DIVISION_ADMIN: exactly one account per corporation.
 * The admin's scope covers the ENTIRE corporation (all its divisions/depots),
 * cannot read another corporation, and cannot mutate organization records.
 */
class CorporationAdminScopeTest extends TestSupport {

    private User corpAdmin(Corporation corp) {
        User u = user(uniqueBase("corpadmin"), RoleCode.DIVISION_ADMIN);
        corporationAdminProfile(u, corp);
        return u;
    }

    @Test
    void corpAdminDashboardCarriesCorporationScope() throws Exception {
        Corporation corp = corporation();
        Division div = division(corp);
        User admin = corpAdmin(corp);

        String token = adminLoginToken(admin, RoleCode.DIVISION_ADMIN);
        mvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("DIVISION_ADMIN"))
                .andExpect(jsonPath("$.scope.wholeSystem").value(false))
                .andExpect(jsonPath("$.scope.corporationId").value(corp.getId()))
                .andExpect(jsonPath("$.scope.divisionId").doesNotExist())
                .andExpect(jsonPath("$.scope.depotId").doesNotExist());
    }

    @Test
    void corpAdminListsOnlyOwnCorporationDivisions() throws Exception {
        Corporation own = corporation();
        Division ownDiv = division(own);
        Corporation other = corporation();
        Division otherDiv = division(other);
        User admin = corpAdmin(own);

        String token = adminLoginToken(admin, RoleCode.DIVISION_ADMIN);
        // Own corporation readable.
        mvc.perform(get("/api/admin/organizations/divisions")
                        .param("parentId", String.valueOf(own.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ownDiv.getId()));
        // Other corporation denied.
        mvc.perform(get("/api/admin/organizations/divisions")
                        .param("parentId", String.valueOf(other.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        // Corporation root list restricts to own corporation.
        JsonNode corps = om.readTree(mvc.perform(get("/api/admin/organizations/corporations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        org.junit.jupiter.api.Assertions.assertEquals(1, corps.size());
    }

    @Test
    void corpAdminListsDepotsWithinOwnCorporationOnly() throws Exception {
        Corporation own = corporation();
        Division ownDiv = division(own);
        Corporation other = corporation();
        Division otherDiv = division(other);
        User admin = corpAdmin(own);

        String token = adminLoginToken(admin, RoleCode.DIVISION_ADMIN);
        mvc.perform(get("/api/admin/organizations/depots")
                        .param("parentId", String.valueOf(ownDiv.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/organizations/depots")
                        .param("parentId", String.valueOf(otherDiv.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void corpAdminCannotMutateOrganizationRecords() throws Exception {
        Corporation corp = corporation();
        User admin = corpAdmin(corp);

        String token = adminLoginToken(admin, RoleCode.DIVISION_ADMIN);
        mvc.perform(post("/api/admin/organizations/divisions")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(om.writeValueAsString(Map.of(
                                "corporationId", corp.getId(), "code", uniqueBase("DIV"), "name", "Nope"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/organizations/depots")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(om.writeValueAsString(Map.of(
                                "divisionId", 1L, "code", uniqueBase("DPT"), "name", "Nope"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void corpAdminCanManageRoutesAcrossOwnCorporation() throws Exception {
        Corporation own = corporation();
        Division ownDiv = division(own);
        Corporation other = corporation();
        Division otherDiv = division(other);
        User admin = corpAdmin(own);
        String token = adminLoginToken(admin, RoleCode.DIVISION_ADMIN);

        // Create a route in an own-corporation division.
        mvc.perform(post("/api/admin/routes")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(om.writeValueAsString(Map.of(
                                "divisionId", ownDiv.getId(),
                                "code", uniqueBase("RT"),
                                "name", "Own - Route",
                                "origin", "A", "destination", "B"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.divisionId").value(ownDiv.getId()));

        // Creating a route in another corporation is denied.
        mvc.perform(post("/api/admin/routes")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(om.writeValueAsString(Map.of(
                                "divisionId", otherDiv.getId(),
                                "code", uniqueBase("RT"),
                                "name", "Other - Route",
                                "origin", "A", "destination", "B"))))
                .andExpect(status().isForbidden());

        // A route in another corporation is not readable.
        Route foreign = route(otherDiv, "X", "Y");
        mvc.perform(get("/api/admin/routes/" + foreign.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        // Route list returns only the corp's own routes.
        JsonNode list = om.readTree(mvc.perform(get("/api/admin/routes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        for (JsonNode node : list.get("content")) {
            org.junit.jupiter.api.Assertions.assertEquals(ownDiv.getId(), node.get("divisionId").asLong());
        }
    }
}