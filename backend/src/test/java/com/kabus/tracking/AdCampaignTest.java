package com.kabus.tracking;

import com.fasterxml.jackson.databind.JsonNode;
import com.kabus.tracking.domain.entity.AdPlacement;
import com.kabus.tracking.domain.entity.User;
import com.kabus.tracking.domain.enums.RoleCode;
import com.kabus.tracking.domain.repository.AdPlacementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdCampaignTest extends TestSupport {

    @Autowired
    private AdPlacementRepository placementRepository;

    private User admin;
    private String adminToken;

    @BeforeEach
    void setup() throws Exception {
        AdPlacement placement = new AdPlacement();
        placement.setCode("PASSENGER_WEB_HOME");
        placement.setName("Passenger web home slot");
        placement.setDurationSeconds(5);
        placement.setFrequencySeconds(30);
        placement.setEnabled(true);
        placementRepository.save(placement);

        admin = user(uniqueBase("super"), RoleCode.SUPER_ADMIN);
        superAdminProfile(admin);
        adminToken = adminLoginToken(admin, RoleCode.SUPER_ADMIN);
    }

    private User nonSuper() {
        User du = user(uniqueBase("divadmin"), RoleCode.DIVISION_ADMIN);
        divisionAdminProfile(du, division(corporation()));
        return du;
    }

    @Test
    void onlySuperAdmin_canManageCampaigns() throws Exception {
        String driverToken = loginToken(crewUser("driver", "DRIVER").getUsername(), "Pass@123");
        mvc.perform(get("/api/admin/ads/campaigns")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverToken))
                .andExpect(status().isForbidden());

        String divToken = adminLoginToken(nonSuper(), RoleCode.DIVISION_ADMIN);
        mvc.perform(post("/api/admin/ads/campaigns")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + divToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(campaignBody()))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/admin/ads/placements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + divToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void serveAndImpression_fullFlow() throws Exception {
        JsonNode created = om.readTree(mvc.perform(post("/api/admin/ads/campaigns")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(campaignBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Test Campaign"))
                .andReturn().getResponse().getContentAsString());
        long campaignId = created.get("id").asLong();

        // Public serving
        mvc.perform(get("/api/public/ad/serve").param("placement", "PASSENGER_WEB_HOME"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Test Campaign"))
                .andExpect(jsonPath("$.durationSeconds").value(5));

        // Impression with a genuine view duration
        mvc.perform(post("/api/public/ad/impression")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "adId", campaignId + ":PASSENGER_WEB_HOME",
                                "placement", "PASSENGER_WEB_HOME",
                                "deviceId", "dev-X",
                                "durationViewedMs", 5000,
                                "clicked", false))))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/admin/ads/campaigns/" + campaignId + "/stats")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.impressions").value(1));
    }

    @Test
    void zeroDurationView_doesNotCountAsImpression() throws Exception {
        JsonNode created = om.readTree(mvc.perform(post("/api/admin/ads/campaigns")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(campaignBody()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        long campaignId = created.get("id").asLong();

        mvc.perform(post("/api/public/ad/impression")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "adId", campaignId + ":PASSENGER_WEB_HOME",
                                "placement", "PASSENGER_WEB_HOME",
                                "durationViewedMs", 0,
                                "clicked", false))))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/admin/ads/campaigns/" + campaignId + "/stats")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.impressions").value(0));
    }

    @Test
    void tamperedAdId_isIgnored() throws Exception {
        JsonNode created = om.readTree(mvc.perform(post("/api/admin/ads/campaigns")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(campaignBody()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        long campaignId = created.get("id").asLong();

        // adId placement does not match requested placement -> silently ignored
        mvc.perform(post("/api/public/ad/impression")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "adId", campaignId + ":CREW_APP_HOME",
                                "placement", "PASSENGER_WEB_HOME",
                                "durationViewedMs", 5000,
                                "clicked", false))))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/admin/ads/campaigns/" + campaignId + "/stats")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.impressions").value(0));
    }

    private String campaignBody() throws com.fasterxml.jackson.core.JsonProcessingException {
        return om.writeValueAsString(Map.of(
                "title", "Test Campaign",
                "imageUrl", "https://example.in/ads/test.png",
                "targetUrl", "https://example.in",
                "placement", "PASSENGER_WEB_HOME",
                "startAt", LocalDateTime.now().minusMinutes(5).toString(),
                "endAt", LocalDateTime.now().plusDays(30).toString(),
                "enabled", true,
                "priority", 5,
                "maxImpressions", 10000));
    }
}