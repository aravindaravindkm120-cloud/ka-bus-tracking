package com.kabus.tracking;

import com.fasterxml.jackson.databind.JsonNode;
import com.kabus.tracking.domain.entity.User;
import com.kabus.tracking.domain.enums.RoleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Corporation management: create (with normalization + defaults), duplicate
 * handling (409), listing, and enable/disable toggle.
 */
class CorporationManagementTest extends TestSupport {

    private String superToken;

    @BeforeEach
    void setUp() throws Exception {
        User sa = user(uniqueBase("csa"), RoleCode.SUPER_ADMIN);
        superAdminProfile(sa);
        superToken = adminLoginToken(sa, RoleCode.SUPER_ADMIN);
    }

    @Test
    void createCorporationNormalizesAndDefaults() throws Exception {
        JsonNode created = om.readTree(mvc.perform(post("/api/admin/organizations/corporations")
                        .header("Authorization", "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "code", "testrtc",
                                "name", "Test Transport Corporation"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.code").value("TESTRTC"))
                .andExpect(jsonPath("$.state").value("Karnataka"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andReturn().getResponse().getContentAsString());
        assertTrue(created.get("id").asLong() > 0);
    }

    @Test
    void createCorporationDuplicateReturns409() throws Exception {
        String common = "KSRTC";
        mvc.perform(post("/api/admin/organizations/corporations")
                        .header("Authorization", "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpBody(common, "First duplicate test")))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/admin/organizations/corporations")
                        .header("Authorization", "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpBody(common, "Second duplicate test")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("already in use")));
    }

    @Test
    void listCorporationsIsSortedAndComplete() throws Exception {
        mvc.perform(post("/api/admin/organizations/corporations")
                        .header("Authorization", "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpBody("BMTC", "Bangalore Metropolitan Transport Corporation")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/admin/organizations/corporations")
                        .header("Authorization", "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpBody("NWKRTC", "North Western Karnataka Road Transport Corporation")))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/admin/organizations/corporations")
                        .header("Authorization", "Bearer " + superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("BMTC"))
                .andExpect(jsonPath("$[1].code").value("NWKRTC"));
    }

    @Test
    void toggleCorporationDisables() throws Exception {
        JsonNode created = om.readTree(mvc.perform(post("/api/admin/organizations/corporations")
                        .header("Authorization", "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpBody("KKRTC", "Kalyana Karnataka Road Transport Corporation")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        long id = created.get("id").asLong();

        mvc.perform(patch("/api/admin/organizations/corporations/{id}/enabled", id)
                        .header("Authorization", "Bearer " + superToken)
                        .param("enabled", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void createCorporationRejectsBlankCode() throws Exception {
        mvc.perform(post("/api/admin/organizations/corporations")
                        .header("Authorization", "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpBody(" ", "Name only")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonSuperAdminCannotCreateCorporation() throws Exception {
        User divUser = user(uniqueBase("cdv"), RoleCode.DIVISION_ADMIN);
        divisionAdminProfile(divUser, division(corporation()));
        String divToken = adminLoginToken(divUser, RoleCode.DIVISION_ADMIN);

        mvc.perform(post("/api/admin/organizations/corporations")
                        .header("Authorization", "Bearer " + divToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpBody("BMTC", "Bangalore Metropolitan Transport Corporation")))
                .andExpect(status().isForbidden());
    }

    @Test
    void createCorporationDefaultsStateWhenBlank() throws Exception {
        mvc.perform(post("/api/admin/organizations/corporations")
                        .header("Authorization", "Bearer " + superToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "code", "TSTRC",
                                "name", "State Default Check",
                                "state", ""))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("Karnataka"));
    }

    private String corpBody(String code, String name) throws Exception {
        return om.writeValueAsString(Map.of("code", code, "name", name));
    }
}