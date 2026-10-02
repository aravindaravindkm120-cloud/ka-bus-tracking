package com.kabus.tracking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kabus.tracking.domain.entity.Bus;
import com.kabus.tracking.domain.entity.BusNumber;
import com.kabus.tracking.domain.entity.Corporation;
import com.kabus.tracking.domain.entity.CorporationAdminProfile;
import com.kabus.tracking.domain.entity.Crew;
import com.kabus.tracking.domain.entity.CrewAssignment;
import com.kabus.tracking.domain.entity.Depot;
import com.kabus.tracking.domain.entity.DepotHeadProfile;
import com.kabus.tracking.domain.entity.Division;
import com.kabus.tracking.domain.entity.DivisionAdminProfile;
import com.kabus.tracking.domain.entity.DivisionManagerProfile;
import com.kabus.tracking.domain.entity.LiveLocation;
import com.kabus.tracking.domain.entity.Notification;
import com.kabus.tracking.domain.entity.Role;
import com.kabus.tracking.domain.entity.Route;
import com.kabus.tracking.domain.entity.RouteStop;
import com.kabus.tracking.domain.entity.Staff;
import com.kabus.tracking.domain.entity.SuperAdminProfile;
import com.kabus.tracking.domain.entity.Town;
import com.kabus.tracking.domain.entity.TownManagerProfile;
import com.kabus.tracking.domain.entity.Trip;
import com.kabus.tracking.domain.entity.User;
import com.kabus.tracking.domain.enums.LiveStatus;
import com.kabus.tracking.domain.enums.RoleCode;
import com.kabus.tracking.domain.enums.TripStatus;
import com.kabus.tracking.domain.repository.BusNumberRepository;
import com.kabus.tracking.domain.repository.BusRepository;
import com.kabus.tracking.domain.repository.CorporationAdminProfileRepository;
import com.kabus.tracking.domain.repository.CorporationRepository;
import com.kabus.tracking.domain.repository.CrewAssignmentRepository;
import com.kabus.tracking.domain.repository.CrewRepository;
import com.kabus.tracking.domain.repository.DepotHeadProfileRepository;
import com.kabus.tracking.domain.repository.DepotRepository;
import com.kabus.tracking.domain.repository.DivisionAdminProfileRepository;
import com.kabus.tracking.domain.repository.DivisionManagerProfileRepository;
import com.kabus.tracking.domain.repository.DivisionRepository;
import com.kabus.tracking.domain.repository.LiveLocationRepository;
import com.kabus.tracking.domain.repository.NotificationRepository;
import com.kabus.tracking.domain.repository.RouteRepository;
import com.kabus.tracking.domain.repository.RouteStopRepository;
import com.kabus.tracking.domain.repository.StaffRepository;
import com.kabus.tracking.domain.repository.RoleRepository;
import com.kabus.tracking.domain.repository.SuperAdminProfileRepository;
import com.kabus.tracking.domain.repository.TownManagerProfileRepository;
import com.kabus.tracking.domain.repository.TownRepository;
import com.kabus.tracking.domain.repository.TripRepository;
import com.kabus.tracking.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared fixture/helpers for integration tests. Each test class extends this
 * and builds its own org tree with unique codes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class TestSupport {

    protected static final AtomicLong SUFFIX = new AtomicLong(System.currentTimeMillis() % 100000);

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper om;
    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired protected UserRepository userRepository;
    @Autowired protected RoleRepository roleRepository;
    @Autowired protected CorporationRepository corporationRepository;
    @Autowired protected DivisionRepository divisionRepository;
    @Autowired protected DepotRepository depotRepository;
    @Autowired protected TownRepository townRepository;
    @Autowired protected RouteRepository routeRepository;
    @Autowired protected RouteStopRepository routeStopRepository;
    @Autowired protected BusRepository busRepository;
    @Autowired protected BusNumberRepository busNumberRepository;
    @Autowired protected TripRepository tripRepository;
    @Autowired protected CrewRepository crewRepository;
    @Autowired protected CrewAssignmentRepository crewAssignmentRepository;
    @Autowired protected LiveLocationRepository liveLocationRepository;
    @Autowired protected StaffRepository staffRepository;
    @Autowired protected NotificationRepository notificationRepository;
    @Autowired protected org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Autowired protected SuperAdminProfileRepository superAdminProfileRepository;
    @Autowired protected CorporationAdminProfileRepository corporationAdminProfileRepository;
    @Autowired protected DivisionAdminProfileRepository divisionAdminProfileRepository;
    @Autowired protected DivisionManagerProfileRepository divisionManagerProfileRepository;
    @Autowired protected DepotHeadProfileRepository depotHeadProfileRepository;
    @Autowired protected TownManagerProfileRepository townManagerProfileRepository;

    @BeforeEach
    void cleanup() {
        // Drop all data between tests in FK-safe order (H2 in-memory, per-test isolation).
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        for (String table : tables(jdbc)) {
            jdbc.execute("TRUNCATE TABLE " + table);
        }
        jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    private java.util.List<String> tables(JdbcTemplate jdbc) {
        return jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = CURRENT_SCHEMA()",
                String.class);
    }

    protected String suffix() {
        return "-" + SUFFIX.incrementAndGet();
    }

    protected String uniqueBase(String prefix) {
        return prefix + SUFFIX.incrementAndGet();
    }

    protected Role role(RoleCode code) {
        return roleRepository.findByCode(code)
                .orElseGet(() -> roleRepository.save(new Role(code, code.name(), "test")));
    }

    protected User user(String username, RoleCode code) {
        Role r = role(code);
        User u = new User();
        u.setUsername(username);
        u.setFullName("Test " + username);
        u.setEmail(username + "@example.test");
        u.setPasswordHash(passwordEncoder.encode("Pass@123"));
        u.getRoles().add(r);
        return userRepository.save(u);
    }

    /** A crew account: user + linked crew profile (role comes from crew_type). */
    protected User crewUser(String base, String crewType) {
        User u = user(uniqueBase(base), RoleCode.valueOf(crewType));
        crew(u, crewType);
        return u;
    }

    protected void superAdminProfile(User u) {
        SuperAdminProfile p = new SuperAdminProfile();
        p.setUser(u);
        superAdminProfileRepository.save(p);
    }

    protected void corporationAdminProfile(User u, Corporation corporation) {
        CorporationAdminProfile p = new CorporationAdminProfile();
        p.setUser(u);
        p.setCorporation(corporation);
        corporationAdminProfileRepository.save(p);
    }

    protected void divisionAdminProfile(User u, Division division) {
        DivisionAdminProfile p = new DivisionAdminProfile();
        p.setUser(u);
        p.setDivision(division);
        divisionAdminProfileRepository.save(p);
    }

    protected void divisionManagerProfile(User u, Division division) {
        DivisionManagerProfile p = new DivisionManagerProfile();
        p.setUser(u);
        p.setDivision(division);
        divisionManagerProfileRepository.save(p);
    }

    protected void depotHeadProfile(User u, Depot depot) {
        DepotHeadProfile p = new DepotHeadProfile();
        p.setUser(u);
        p.setDepot(depot);
        depotHeadProfileRepository.save(p);
    }

    protected void townManagerProfile(User u, Town town) {
        TownManagerProfile p = new TownManagerProfile();
        p.setUser(u);
        p.setTown(town);
        townManagerProfileRepository.save(p);
    }

    protected User userScoped(String username, RoleCode code, Division division, Depot depot, Town town) {
        User u = user(username, code);
        u.setDivision(division);
        u.setDepot(depot);
        u.setTown(town);
        return userRepository.save(u);
    }

    protected Corporation corporation() {
        Corporation c = new Corporation();
        c.setCode(uniqueBase("CORP"));
        c.setName("Test Corporation");
        return corporationRepository.save(c);
    }

    protected Division division(Corporation corp) {
        Division d = new Division();
        d.setCorporation(corp);
        d.setCode(uniqueBase("DIV"));
        d.setName("Test Division");
        return divisionRepository.save(d);
    }

    protected Depot depot(Division division) {
        Depot d = new Depot();
        d.setDivision(division);
        d.setCode(uniqueBase("DPT"));
        d.setName("Test Depot");
        return depotRepository.save(d);
    }

    protected Town town(Depot depot) {
        Town t = new Town();
        t.setDepot(depot);
        t.setCode(uniqueBase("TWN"));
        t.setName("Test Town");
        return townRepository.save(t);
    }

    protected Route route(Division division, String origin, String destination) {
        Route r = new Route();
        r.setDivision(division);
        r.setCode(uniqueBase("RT"));
        r.setName(origin + " - " + destination);
        r.setOrigin(origin);
        r.setDestination(destination);
        r.setDistanceKm(BigDecimal.valueOf(20));
        return routeRepository.save(r);
    }

    protected RouteStop routeStop(Route route, int order, String name) {
        RouteStop s = new RouteStop();
        s.setRoute(route);
        s.setStopOrder(order);
        s.setStopName(name);
        s.setLatitude(new BigDecimal("14.100000"));
        s.setLongitude(new BigDecimal("74.100000"));
        s.setDistanceFromStart(BigDecimal.valueOf(order * 5));
        return routeStopRepository.save(s);
    }

    /** A bus number master for the given depot/town. */
    protected BusNumber busNumber(Depot depot, Town town) {
        return busNumber(depot, town, "KA-RT-" + uniqueBase("").replace("-", ""));
    }

    protected BusNumber busNumber(Depot depot, Town town, String busNumber) {
        BusNumber bn = new BusNumber();
        bn.setDepot(depot);
        bn.setTown(town);
        bn.setDivision(depot.getDivision());
        bn.setCorporation(depot.getDivision().getCorporation());
        bn.setBusNumber(busNumber);
        bn.setBusType("ORDINARY");
        bn.setEnabled(true);
        return busNumberRepository.save(bn);
    }

protected Bus bus(Depot depot, Town town) {
        return bus(busNumber(depot, town), depot, town);
    }

    /** A second vehicle on an existing bus number, so one master can have many. */
    protected Bus bus(BusNumber busNumber, Depot depot, Town town) {
        Bus b = new Bus();
        b.setDepot(depot);
        b.setTown(town);
        b.setBusNumber(busNumber);
        b.setCapacity(40);
        return busRepository.save(b);
    }

    protected Trip trip(Route route, Bus bus) {
        Trip t = new Trip();
        t.setRoute(route);
        t.setBus(bus);
        t.setTripNumber(uniqueBase("TRIP"));
        t.setTripDate(LocalDate.now());
        t.setScheduledDeparture(LocalDateTime.now().minusHours(1));
        t.setScheduledArrival(LocalDateTime.now().plusHours(1));
        t.setStatus(TripStatus.SCHEDULED);
        return tripRepository.save(t);
    }

    protected Crew crew(User owner, String crewType) {
        Crew c = new Crew();
        c.setUser(owner);
        c.setBadgeNo(uniqueBase("CRW"));
        c.setFullName("Crew " + owner.getUsername());
        c.setCrewType(crewType);
        c.setStatus("ACTIVE");
        c.setDutyStatus("OFF_DUTY");
        return crewRepository.save(c);
    }

    protected Staff staff(Depot depot) {
        Staff s = new Staff();
        s.setDepot(depot);
        s.setDivision(depot.getDivision());
        s.setCorporation(depot.getDivision().getCorporation());
        s.setFullName("Staff " + uniqueBase("S"));
        s.setStatus("ACTIVE");
        return staffRepository.save(s);
    }

    /** A crew member whose organizational home comes from a linked Staff record. */
    protected Crew crewOnStaff(Staff staff, String crewType) {
        User u = user(uniqueBase("crew"), RoleCode.valueOf(crewType));
        Crew c = new Crew();
        c.setStaff(staff);
        c.setUser(u);
        c.setBadgeNo(uniqueBase("BDG"));
        c.setFullName(staff.getFullName());
        c.setCrewType(crewType);
        c.setStatus("ACTIVE");
        c.setDutyStatus("OFF_DUTY");
        return crewRepository.save(c);
    }

    protected LiveLocation liveLocation(Bus bus, Trip trip, Route route, LiveStatus status) {
        LiveLocation l = new LiveLocation();
        l.setBus(bus);
        l.setTrip(trip);
        l.setRoute(route);
        l.setLatitude(new BigDecimal("13.000000"));
        l.setLongitude(new BigDecimal("77.000000"));
        l.setSpeedKmh(BigDecimal.valueOf(30));
        l.setHeading(BigDecimal.valueOf(90));
        l.setAccuracyM(BigDecimal.valueOf(5));
        l.setCapturedAt(LocalDateTime.now());
        l.setStatus(status);
        return liveLocationRepository.save(l);
    }

    protected Notification notification(User user, String title) {
        Notification n = new Notification();
        n.setUser(user);
        n.setType("TEST");
        n.setTitle(title);
        n.setBody(title);
        return notificationRepository.save(n);
    }

    protected String json(Object body) throws Exception {
        return om.writeValueAsString(body);
    }

    /** Reads the "id" out of a JSON response body. */
    protected Long busNumberId(MvcResult result) throws Exception {
        return om.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    protected CrewAssignment assignment(Trip trip, Crew crew, String crewType) {
        CrewAssignment a = new CrewAssignment();
        a.setTrip(trip);
        a.setCrew(crew);
        a.setCrewType(crewType);
        a.setStatus("ACTIVE");
        return crewAssignmentRepository.save(a);
    }

    protected String loginToken(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = om.readTree(result.getResponse().getContentAsString());
        return json.get("accessToken").asText();
    }

    /** Strict role-specific admin login (email + requestedRole). */
    protected String adminLoginToken(User user, RoleCode requestedRole) throws Exception {
        return adminLoginToken(user.getEmail(), "Pass@123", requestedRole);
    }

    protected String adminLoginToken(String email, String password, RoleCode requestedRole) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "email", email,
                                "password", password,
                                "requestedRole", requestedRole.name()))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = om.readTree(result.getResponse().getContentAsString());
        return json.get("accessToken").asText();
    }
}