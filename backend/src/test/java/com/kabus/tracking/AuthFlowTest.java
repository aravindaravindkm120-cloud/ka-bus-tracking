package com.kabus.tracking;

import com.fasterxml.jackson.databind.JsonNode;
import com.kabus.tracking.domain.entity.User;
import com.kabus.tracking.domain.enums.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthFlowTest extends TestSupport {

    @Test
    void login_returnsTokensAndRoles() throws Exception {
        User u = crewUser("driver", "DRIVER");
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(
                                Map.of("username", u.getUsername(), "password", "Pass@123", "deviceId", "DEV-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value(u.getUsername()))
                .andExpect(jsonPath("$.user.roles", hasItem("DRIVER")));
    }

    @Test
    void wrongPassword_isRejected() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(
                                Map.of("username", uniqueBase("driver"), "password", "wrong"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_me_refresh_logout_flow() throws Exception {
        User u = crewUser("driver", "DRIVER");
        String fullLogin = om.writeValueAsString(
                Map.of("username", u.getUsername(), "password", "Pass@123", "deviceId", "DEV-2"));
        JsonNode login = om.readTree(mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(fullLogin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        String access = login.get("accessToken").asText();
        String refresh = login.get("refreshToken").asText();

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value(u.getUsername()))
                .andExpect(jsonPath("$.user.roles", hasItem("DRIVER")));

        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        MvcResult refreshed = mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("refreshToken", refresh))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andReturn();
        String newRefresh = om.readTree(refreshed.getResponse().getContentAsString())
                .get("refreshToken").asText();

        // Refresh rotation: the old token is revoked, a second use must fail.
        mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("refreshToken", refresh))))
                .andExpect(status().isUnauthorized());

        // logout revokes the new token
        mvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("refreshToken", newRefresh))))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("refreshToken", newRefresh))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginResponse_isBearerJwt() throws Exception {
        User u = crewUser("driver", "DRIVER");
        JsonNode login = om.readTree(mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(
                                Map.of("username", u.getUsername(), "password", "Pass@123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        String access = login.get("accessToken").asText();
        String[] parts = access.split("\\.");
        if (parts.length != 3) {
            throw new AssertionError("JWT must have 3 dot-separated parts, got " + access);
        }
        java.util.Base64.getUrlDecoder().decode(parts[1]);
    }
}