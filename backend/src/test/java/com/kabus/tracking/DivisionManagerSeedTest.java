package com.kabus.tracking;

import com.fasterxml.jackson.databind.JsonNode;
import com.kabus.tracking.domain.entity.Corporation;
import com.kabus.tracking.domain.entity.Division;
import com.kabus.tracking.domain.entity.User;
import com.kabus.tracking.domain.enums.RoleCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Division seeding: one DIVISION_MANAGER per division. The Super Admin division
 * list must surface the assigned Division Manager (name/email/status) per
 * division, and a seeded manager must be able to log in with the correct
 * division scope. The seeded DEV-ONLY password hash must BCrypt-verify.
 */
class DivisionManagerSeedTest extends TestSupport {

    private String superToken() throws Exception {
        User sa = user(uniqueBase("dms"), RoleCode.SUPER_ADMIN);
        superAdminProfile(sa);
        return adminLoginToken(sa, RoleCode.SUPER_ADMIN);
    }

    private User seededDivisionManager(Division division) {
        User manager = user(uniqueBase("seedmgr"), RoleCode.DIVISION_MANAGER);
        manager.setDivision(division);
        userRepository.save(manager);
        divisionManagerProfile(manager, division);
        return manager;
    }

    @Test
    void divisionListingIncludesAssignedManager() throws Exception {
        String token = superToken();

        Corporation corp = corporation();
        Division withManager = division(corp);
        User manager = seededDivisionManager(withManager);

        mvc.perform(get("/api/admin/organizations/divisions")
                        .param("parentId", String.valueOf(corp.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(withManager.getId()))
                .andExpect(jsonPath("$[0].adminId").isNumber())
                .andExpect(jsonPath("$[0].adminName").value(manager.getFullName()))
                .andExpect(jsonPath("$[0].adminEmail").value(manager.getEmail()))
                .andExpect(jsonPath("$[0].adminStatus").value("ACTIVE"))
                .andExpect(jsonPath("$[0].adminRole").value("DIVISION_MANAGER"));
    }

    @Test
    void divisionWithoutManagerHasNullAdminFields() throws Exception {
        String token = superToken();
        Corporation corp = corporation();
        Division noManager = division(corp);

        JsonNode list = om.readTree(mvc.perform(get("/api/admin/organizations/divisions")
                        .param("parentId", String.valueOf(corp.getId()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertTrue(list.size() == 1);
        JsonNode d = list.get(0);
        // Division without an assigned manager: personnel fields are absent (null) in the payload.
        // adminRole is always present and identifies the role an assigned admin would hold.
        assertTrue((d.get("adminId") == null || d.get("adminId").isNull()));
        assertTrue((d.get("adminName") == null || d.get("adminName").isNull()));
        assertTrue((d.get("adminEmail") == null || d.get("adminEmail").isNull()));
        assertTrue((d.get("adminStatus") == null || d.get("adminStatus").isNull()));
        org.junit.jupiter.api.Assertions.assertEquals("DIVISION_MANAGER", d.get("adminRole").asText());
        assertTrue(d.get("id").asLong() == noManager.getId());
    }

    @Test
    void seededDivisionManagerLoginResolvesCorrectScope() throws Exception {
        Corporation corp = corporation();
        Division div = division(corp);
        User manager = seededDivisionManager(div);

        String token = adminLoginToken(manager, RoleCode.DIVISION_MANAGER);
        mvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope.wholeSystem").value(false))
                .andExpect(jsonPath("$.scope.divisionId").value(div.getId()));
    }

    @Test
    void seededDevOnlyPasswordHashBcryptVerifies() {
        // The exact hash seeded for the Division Manager and Depot Head accounts.
        assertTrue(passwordEncoder.matches("KsrDivision@123",
                "$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO"));
    }
}