package com.kabus.tracking;

import com.kabus.tracking.domain.entity.BusNumber;
import com.kabus.tracking.domain.entity.Depot;
import com.kabus.tracking.domain.entity.Town;
import com.kabus.tracking.domain.entity.User;
import com.kabus.tracking.domain.enums.RoleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The bus number master, and the rule that a fleet vehicle references an
 * existing bus number rather than carrying the number itself.
 *
 * <p>The hierarchy on {@code bus_numbers} is deliberately redundant: a bus
 * number belongs to one depot, and therefore to exactly one division and one
 * corporation. Storing all four keeps the list endpoints simple and lets the
 * same index serve every scope, at the cost of the service having to keep them
 * consistent - which is what the consistency tests below pin down.</p>
 */
class BusNumberMasterTest extends TestSupport {

    private static final String AUTH = HttpHeaders.AUTHORIZATION;

    private Depot depotA;
    private Town townA;
    private Depot depotB;
    private Town townB;

    private String superToken;

    @BeforeEach
    void setUpFleet() throws Exception {
        User sa = user(uniqueBase("bnsuper"), RoleCode.SUPER_ADMIN);
        superAdminProfile(sa);
        superToken = adminLoginToken(sa, RoleCode.SUPER_ADMIN);

        depotA = depot(division(corporation()));
        townA = town(depotA);
        depotB = depot(depotA.getDivision());
        townB = town(depotB);
    }

    // ------------------------------------------------------------------
    // Hierarchy consistency
    // ------------------------------------------------------------------

    @Test
    void creatingABusNumberDerivesDivisionAndCorporationFromTheDepot() throws Exception {
        Long id = createBusNumber("KA-100", depotA.getId(), townA.getId());

        mvc.perform(get("/api/admin/fleet/bus-numbers/" + id).header(AUTH, bearer(superToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.busNumber").value("KA-100"))
                .andExpect(jsonPath("$.busType").value("ORDINARY"))
                .andExpect(jsonPath("$.depotId").value(depotA.getId()))
                .andExpect(jsonPath("$.townId").value(townA.getId()))
                .andExpect(jsonPath("$.divisionId").value(depotA.getDivision().getId()))
                .andExpect(jsonPath("$.corporationId").value(depotA.getDivision().getCorporation().getId()));
    }

    @Test
    void busNumberIsUppercasedAndTrimmed() throws Exception {
        Long id = createBusNumber("  ka-lower-9  ", depotA.getId(), townA.getId());

        mvc.perform(get("/api/admin/fleet/bus-numbers/" + id).header(AUTH, bearer(superToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.busNumber").value("KA-LOWER-9"));
    }

    @Test
    void townMustBelongToTheChosenDepot() throws Exception {
        mvc.perform(post("/api/admin/fleet/bus-numbers")
                        .header(AUTH, bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "busNumber", "KA-200",
                                "busType", "ORDINARY",
                                "depotId", depotA.getId(),
                                "townId", townB.getId()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sameBusNumberMayExistInTwoDifferentDepots() throws Exception {
        createBusNumber("KA-300", depotA.getId(), townA.getId());
        // Bus numbers are unique per depot, not system-wide.
        Long second = createBusNumber("KA-300", depotB.getId(), townB.getId());

        mvc.perform(get("/api/admin/fleet/bus-numbers/" + second).header(AUTH, bearer(superToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.depotId").value(depotB.getId()));
    }

    @Test
    void duplicateBusNumberInTheSameDepotIsRejected() throws Exception {
        createBusNumber("KA-400", depotA.getId(), townA.getId());

        mvc.perform(post("/api/admin/fleet/bus-numbers")
                        .header(AUTH, bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "busNumber", "KA-400",
                                "busType", "EXPRESS",
                                "depotId", depotA.getId(),
                                "townId", townA.getId()))))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------
    // Bus types stay inside the documented set
    // ------------------------------------------------------------------

    @Test
    void inventedBusTypesAreRejected() throws Exception {
        mvc.perform(post("/api/admin/fleet/bus-numbers")
                        .header(AUTH, bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "busNumber", "KA-500",
                                "busType", "SUPER-DELUXE",
                                "depotId", depotA.getId(),
                                "townId", townA.getId()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void everyDocumentedBusTypeIsAccepted() throws Exception {
        int n = 0;
        for (String type : new String[]{"ORDINARY", "EXPRESS", "RAJADHARSHA"}) {
            mvc.perform(post("/api/admin/fleet/bus-numbers")
                            .header(AUTH, bearer(superToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "busNumber", "KA-600-" + n,
                                    "busType", type,
                                    "depotId", depotA.getId(),
                                    "townId", townA.getId()))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.busType").value(type));
            n++;
        }
    }

    // ------------------------------------------------------------------
    // Fleet vehicles reference the master
    // ------------------------------------------------------------------

    @Test
    void fleetVehicleReportsItsBusNumberAndType() throws Exception {
        Long busNumberId = createBusNumber("KA-700", depotA.getId(), townA.getId());

        mvc.perform(post("/api/admin/fleet/buses")
                        .header(AUTH, bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "busNumberId", busNumberId,
                                "capacity", 45,
                                "depotId", depotA.getId(),
                                "townId", townA.getId()))))
                .andExpect(status().isCreated())
                // registrationNo/busType keep their historical JSON names but are
                // now served from the master.
                .andExpect(jsonPath("$.registrationNo").value("KA-700"))
                .andExpect(jsonPath("$.busType").value("ORDINARY"))
                .andExpect(jsonPath("$.busNumberId").value(busNumberId));
    }

    @Test
    void vehicleCannotBeCreatedOnAnUnknownBusNumber() throws Exception {
        mvc.perform(post("/api/admin/fleet/buses")
                        .header(AUTH, bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "busNumberId", 987654321L,
                                "capacity", 40,
                                "depotId", depotA.getId(),
                                "townId", townA.getId()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void vehicleCannotUseABusNumberFromAnotherDepot() throws Exception {
        Long foreign = createBusNumber("KA-800", depotB.getId(), townB.getId());

        mvc.perform(post("/api/admin/fleet/buses")
                        .header(AUTH, bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "busNumberId", foreign,
                                "capacity", 40,
                                "depotId", depotA.getId(),
                                "townId", townA.getId()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void vehicleMustBeRegisteredInTheTownItsBusNumberServes() throws Exception {
        Town otherTown = town(depotA);
        Long busNumberId = createBusNumber("KA-850", depotA.getId(), townA.getId());

        mvc.perform(post("/api/admin/fleet/buses")
                        .header(AUTH, bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "busNumberId", busNumberId,
                                "capacity", 40,
                                "depotId", depotA.getId(),
                                "townId", otherTown.getId()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void vehicleCannotBeCreatedOnADisabledBusNumber() throws Exception {
        Long busNumberId = createBusNumber("KA-900", depotA.getId(), townA.getId());
        mvc.perform(patch("/api/admin/fleet/bus-numbers/" + busNumberId + "/enabled")
                        .param("enabled", "false")
                        .header(AUTH, bearer(superToken)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/admin/fleet/buses")
                        .header(AUTH, bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "busNumberId", busNumberId,
                                "capacity", 40,
                                "depotId", depotA.getId(),
                                "townId", townA.getId()))))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------
    // In-use protection
    // ------------------------------------------------------------------

    @Test
    void busNumberInUseCannotBeDisabledOrDeleted() throws Exception {
        Long busNumberId = createBusNumber("KA-950", depotA.getId(), townA.getId());
        bus(busNumberRepository.findById(busNumberId).orElseThrow(), depotA, townA);

        mvc.perform(patch("/api/admin/fleet/bus-numbers/" + busNumberId + "/enabled")
                        .param("enabled", "false")
                        .header(AUTH, bearer(superToken)))
                .andExpect(status().isConflict());

        mvc.perform(delete("/api/admin/fleet/bus-numbers/" + busNumberId)
                        .header(AUTH, bearer(superToken)))
                .andExpect(status().isConflict());
    }

    @Test
    void unusedBusNumberCanBeDisabledAndDeleted() throws Exception {
        Long busNumberId = createBusNumber("KA-980", depotA.getId(), townA.getId());

        mvc.perform(patch("/api/admin/fleet/bus-numbers/" + busNumberId + "/enabled")
                        .param("enabled", "false")
                        .header(AUTH, bearer(superToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mvc.perform(delete("/api/admin/fleet/bus-numbers/" + busNumberId)
                        .header(AUTH, bearer(superToken)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/admin/fleet/bus-numbers/" + busNumberId).header(AUTH, bearer(superToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void vehicleCountIsReportedOnTheBusNumber() throws Exception {
        Long busNumberId = createBusNumber("KA-990", depotA.getId(), townA.getId());
        BusNumber bn = busNumberRepository.findById(busNumberId).orElseThrow();
        bus(bn, depotA, townA);
        bus(bn, depotA, townA);

        mvc.perform(get("/api/admin/fleet/bus-numbers/" + busNumberId).header(AUTH, bearer(superToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleCount").value(2));
    }

    // ------------------------------------------------------------------
    // Dropdowns
    // ------------------------------------------------------------------

    @Test
    void byDepotReturnsOnlyEnabledNumbersForThatDepot() throws Exception {
        Long enabled = createBusNumber("KA-1000", depotA.getId(), townA.getId());
        Long disabled = createBusNumber("KA-1001", depotA.getId(), townA.getId());
        Long otherDepot = createBusNumber("KA-1002", depotB.getId(), townB.getId());

        mvc.perform(patch("/api/admin/fleet/bus-numbers/" + disabled + "/enabled")
                        .param("enabled", "false")
                        .header(AUTH, bearer(superToken)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/admin/fleet/bus-numbers/by-depot/" + depotA.getId())
                        .header(AUTH, bearer(superToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(enabled))
                .andExpect(jsonPath("$[?(@.id == " + disabled + ")]").doesNotExist())
                .andExpect(jsonPath("$[?(@.id == " + otherDepot + ")]").doesNotExist());
    }

    @Test
    void searchMatchesNumberAndType() throws Exception {
        createBusNumber("KA-7777", depotA.getId(), townA.getId());
        mvc.perform(post("/api/admin/fleet/bus-numbers")
                        .header(AUTH, bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "busNumber", "ZZ-1111",
                                "busType", "RAJADHARSHA",
                                "depotId", depotA.getId(),
                                "townId", townA.getId()))))
                .andExpect(status().isCreated());

        // By type: only the RAJADHARSHA one matches.
        mvc.perform(get("/api/admin/fleet/bus-numbers").param("search", "RAJADHARSHA")
                        .param("size", "100").header(AUTH, bearer(superToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].busNumber").value("ZZ-1111"));

        // By number.
        mvc.perform(get("/api/admin/fleet/bus-numbers").param("search", "KA-7777")
                        .header(AUTH, bearer(superToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].busNumber").value("KA-7777"));
    }

    @Test
    void fleetSearchStillMatchesOnBusNumber() throws Exception {
        BusNumber bn = busNumber(depotA, townA, "KA-SEARCH-1");
        bus(bn, depotA, townA);

        mvc.perform(get("/api/admin/fleet/buses").param("search", "KA-SEARCH-1")
                        .header(AUTH, bearer(superToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].registrationNo").value("KA-SEARCH-1"));
    }

    // ------------------------------------------------------------------

    private Long createBusNumber(String busNumber, Long depotId, Long townId) throws Exception {
        String body = mvc.perform(post("/api/admin/fleet/bus-numbers")
                        .header(AUTH, bearer(superToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "busNumber", busNumber,
                                "busType", "ORDINARY",
                                "depotId", depotId,
                                "townId", townId))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return om.readTree(body).get("id").asLong();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
