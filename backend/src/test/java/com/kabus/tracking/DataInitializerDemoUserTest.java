package com.kabus.tracking;

import com.kabus.tracking.config.AppProperties;
import com.kabus.tracking.config.DataInitializer;
import com.kabus.tracking.domain.entity.Corporation;
import com.kabus.tracking.domain.entity.Crew;
import com.kabus.tracking.domain.entity.Depot;
import com.kabus.tracking.domain.entity.Division;
import com.kabus.tracking.domain.entity.Town;
import com.kabus.tracking.domain.entity.User;
import com.kabus.tracking.domain.enums.RoleCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for the demo-user bootstrap idempotency fix.
 *
 * <p>The seed used to bail out whenever more than one user existed, but
 * {@code ensureSuperAdmin()} always runs first and creates the SUPER_ADMIN, so
 * that guard made the demo accounts unreachable on any database where the
 * SUPER_ADMIN already existed - which is exactly the production case. The
 * initializer is invoked directly here so each scenario is deterministic.</p>
 */
class DataInitializerDemoUserTest extends TestSupport {

    private static final List<String> DEMO_USERNAMES =
            List.of("divisionadmin", "divisionmanager", "depothead", "townmanager", "driver", "conductor");

    @Autowired
    DataInitializer initializer;
    @Autowired
    AppProperties props;
    @Autowired
    PasswordEncoder demoPasswordEncoder;

    private String previousProfile;

    @BeforeEach
    void seedOrganization() {
        // TestSupport truncates every table before each test; build the org tree
        // that ensureDemoUsersIfEnabled() resolves its scope from.
        Corporation corporation = corporation();
        Division division = division(corporation);
        Depot depot = depot(division);
        Town town = town(depot);

        // Canonical badges used by the initializer for crew linkage.
        seededCrew("CRW-DRV-001", "Driver Ramesh", "DRIVER");
        seededCrew("CRW-CND-001", "Conductor Suresh", "CONDUCTOR");

        previousProfile = System.getProperty("spring.profiles.active");
        System.setProperty("spring.profiles.active", "dev");
    }

    private void seededCrew(String badge, String name, String crewType) {
        Crew crew = new Crew();
        crew.setBadgeNo(badge);
        crew.setFullName(name);
        crew.setCrewType(crewType);
        crew.setStatus("ACTIVE");
        crew.setDutyStatus("OFF_DUTY");
        crewRepository.save(crew);
    }

    @AfterEach
    void restoreProfile() {
        if (previousProfile == null) {
            System.clearProperty("spring.profiles.active");
        } else {
            System.setProperty("spring.profiles.active", previousProfile);
        }
    }

    private void runInitializer() {
        initializer.run(new DefaultApplicationArguments(new String[0]));
    }

    private User demo(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new AssertionError("Expected demo account: " + username));
    }

    @Test
    @DisplayName("demo accounts are created even though the SUPER_ADMIN already exists")
    void createsDemoUsersAlongsideSuperAdmin() {
        runInitializer();

        assertThat(userRepository.findByUsername(props.getSeed().getAdminUsername())).isPresent();
        for (String username : DEMO_USERNAMES) {
            assertThat(userRepository.findByUsername(username))
                    .as("demo account %s must exist", username)
                    .isPresent();
        }
    }

    @Test
    @DisplayName("existing SUPER_ADMIN password is never re-encoded or modified")
    void leavesSuperAdminUntouched() {
        runInitializer();

        User admin = userRepository.findByUsername(props.getSeed().getAdminUsername()).orElseThrow();
        String hashAfterFirstRun = admin.getPasswordHash();

        runInitializer();

        User adminAfterSecondRun = userRepository.findByUsername(props.getSeed().getAdminUsername()).orElseThrow();
        assertThat(adminAfterSecondRun.getId()).isEqualTo(admin.getId());
        assertThat(adminAfterSecondRun.getPasswordHash())
                .as("SUPER_ADMIN hash must be byte-identical after a second initializer run")
                .isEqualTo(hashAfterFirstRun);
        assertThat(demoPasswordEncoder.matches(props.getSeed().getAdminPassword(), hashAfterFirstRun)).isTrue();
    }

    @Test
    @DisplayName("running the initializer three times creates no duplicate users or memberships")
    void isIdempotentAcrossRuns() {
        runInitializer();
        long countAfterFirst = userRepository.count();

        runInitializer();
        runInitializer();

        assertThat(userRepository.count())
                .as("user count must be stable across repeated runs")
                .isEqualTo(countAfterFirst);
        assertThat(userRepository.count()).isEqualTo(7L); // 1 SUPER_ADMIN + 6 demo

        // Exactly one membership row per admin role, never duplicated.
        assertThat(corporationAdminProfileRepository.findByUserId(demo("divisionadmin").getId())).isPresent();
        assertThat(divisionManagerProfileRepository.findByUserId(demo("divisionmanager").getId())).isPresent();
        assertThat(depotHeadProfileRepository.findByUserId(demo("depothead").getId())).isPresent();
        assertThat(townManagerProfileRepository.findByUserId(demo("townmanager").getId())).isPresent();
    }

    @Test
    @DisplayName("each demo account gets a unique email")
    void assignsUniqueEmails() {
        runInitializer();

        List<String> emails = userRepository.findAll().stream()
                .map(User::getEmail)
                .filter(email -> email != null && email.endsWith("@kabus.dev"))
                .distinct()
                .toList();

        assertThat(emails).hasSize(DEMO_USERNAMES.size());
        assertThat(emails).allSatisfy(email -> assertThat(email).matches("^[a-z]+@kabus\\.dev$"));
    }

    @Test
    @DisplayName("DIVISION_ADMIN membership is recorded in corporation_admins")
    void divisionAdminUsesCorporationAdmins() {
        runInitializer();

        var corpProfile = corporationAdminProfileRepository.findByUserId(demo("divisionadmin").getId());

        assertThat(corpProfile).isPresent();
        assertThat(corpProfile.get().getCorporation()).isNotNull();
    }

    @Test
    @DisplayName("manager, depot head and town manager memberships bind their scope")
    void createsScopedMemberships() {
        runInitializer();

        assertThat(divisionManagerProfileRepository.findByUserId(demo("divisionmanager").getId()))
                .isPresent();
        assertThat(depotHeadProfileRepository.findByUserId(demo("depothead").getId()))
                .isPresent();
        assertThat(townManagerProfileRepository.findByUserId(demo("townmanager").getId()))
                .isPresent();
    }

    @Test
    @DisplayName("crew users link through crew.user_id and keep their crew_type")
    void linksCrewAccountsByBadge() {
        runInitializer();

Crew driver = crewRepository.findByBadgeNo("CRW-DRV-001").orElseThrow();
        Crew conductor = crewRepository.findByBadgeNo("CRW-CND-001").orElseThrow();

        assertThat(driver.getCrewType()).isEqualTo("DRIVER");
        assertThat(conductor.getCrewType()).isEqualTo("CONDUCTOR");

        assertThat(driver.getUser()).isNotNull();
        assertThat(driver.getUser().getId()).isEqualTo(demo("driver").getId());
        assertThat(conductor.getUser()).isNotNull();
        assertThat(conductor.getUser().getId()).isEqualTo(demo("conductor").getId());

        // Crew login resolves the role from crew_type, not from user_roles.
        assertThat(crewRepository.findByUserId(demo("driver").getId()))
                .get()
                .extracting(Crew::getCrewType)
                .isEqualTo("DRIVER");
        assertThat(crewRepository.findByUserId(demo("conductor").getId()))
                .get()
                .extracting(Crew::getCrewType)
                .isEqualTo("CONDUCTOR");
    }

@Test
    @DisplayName("a demo crew account never steals a crew badge owned by another user")
    void doesNotStealCrewBadgeOwnedBySomeoneElse() {
        // Production-shaped precondition: CRW-DRV-001 already belongs to a real
        // crew member, so the demo driver must get its own record.
        Crew existing = crewRepository.findByBadgeNo("CRW-DRV-001").orElseThrow();
        User realCrewMember = user("ramesh.kumar", RoleCode.DRIVER);
        existing.setUser(realCrewMember);
        crewRepository.save(existing);

        runInitializer();

        User demoDriver = demo("driver");
        Crew ownerAfter = crewRepository.findByBadgeNo("CRW-DRV-001").orElseThrow();
        assertThat(ownerAfter.getUser().getId())
                .as("the real crew member must keep the badge")
                .isEqualTo(realCrewMember.getId());

        Crew demoCrew = crewRepository.findByUserId(demoDriver.getId()).orElseThrow();
        assertThat(demoCrew.getBadgeNo()).isNotEqualTo("CRW-DRV-001");
        assertThat(demoCrew.getCrewType()).isEqualTo("DRIVER");

        // And the demo driver can actually authenticate as crew.
        assertThat(crewRepository.findByUserId(demoDriver.getId())).isPresent();
    }

    @Test
    @DisplayName("a demo crew record is created when the preferred badge does not exist at all")
    void createsCrewRecordWhenBadgeMissing() {
        jdbc.update("DELETE FROM crew WHERE badge_no = ?", "CRW-DRV-001");
        jdbc.update("DELETE FROM crew WHERE badge_no = ?", "CRW-CND-001");

        runInitializer();

        Crew demoDriver = crewRepository.findByUserId(demo("driver").getId()).orElseThrow();
        Crew demoConductor = crewRepository.findByUserId(demo("conductor").getId()).orElseThrow();
        assertThat(demoDriver.getBadgeNo()).startsWith("CRW-DRV-001");
        assertThat(demoDriver.getCrewType()).isEqualTo("DRIVER");
        assertThat(demoConductor.getBadgeNo()).startsWith("CRW-CND-001");
        assertThat(demoConductor.getCrewType()).isEqualTo("CONDUCTOR");
    }

    @Test
    @DisplayName("a demo crew account never gets a second crew record across restarts")
    void neverCreatesASecondCrewRecord() {
        // Production-shaped precondition: the preferred badge is taken by a real
        // crew member, which forces the demo record to be created with a suffix.
        Crew existing = crewRepository.findByBadgeNo("CRW-DRV-001").orElseThrow();
        existing.setUser(user("ramesh.kumar", RoleCode.DRIVER));
        crewRepository.save(existing);

        runInitializer();
        runInitializer();
        runInitializer();

        User demoDriver = demo("driver");
        List<Crew> forDemoDriver = crewRepository.findAll().stream()
                .filter(c -> c.getUser() != null && c.getUser().getId().equals(demoDriver.getId()))
                .toList();
        // crew login resolves the role via findByUserId, which needs a unique row.
        assertThat(forDemoDriver).hasSize(1);
        assertThat(crewRepository.findByUserId(demoDriver.getId())).isPresent();

        List<Crew> forDemoConductor = crewRepository.findAll().stream()
                .filter(c -> c.getUser() != null && c.getUser().getId().equals(demo("conductor").getId()))
                .toList();
        assertThat(forDemoConductor).hasSize(1);
    }

    @Test
    @DisplayName("crew linkage is not duplicated when the initializer runs again")
    void crewLinkageIsStableAcrossRuns() {
        runInitializer();
        Long driverCrewId = crewRepository.findByBadgeNo("CRW-DRV-001").orElseThrow().getId();

        runInitializer();

assertThat(crewRepository.findByBadgeNo("CRW-DRV-001").orElseThrow().getId()).isEqualTo(driverCrewId);
        assertThat(crewRepository.findByBadgeNo("CRW-DRV-001").orElseThrow().getUser().getId())
                .isEqualTo(demo("driver").getId());
        assertThat(crewRepository.findByBadgeNo("CRW-CND-001").orElseThrow().getUser().getId())
                .isEqualTo(demo("conductor").getId());
    }

    @Test
    @DisplayName("demo passwords verify against the stored BCrypt hashes")
    void demoPasswordsAreEncoded() {
        runInitializer();

        assertThat(demoPasswordEncoder.matches("DivAdmin@123", demo("divisionadmin").getPasswordHash())).isTrue();
        assertThat(demoPasswordEncoder.matches("DivMgr@123", demo("divisionmanager").getPasswordHash())).isTrue();
        assertThat(demoPasswordEncoder.matches("DepotHead@123", demo("depothead").getPasswordHash())).isTrue();
        assertThat(demoPasswordEncoder.matches("TownMgr@123", demo("townmanager").getPasswordHash())).isTrue();
        assertThat(demoPasswordEncoder.matches("Driver@123", demo("driver").getPasswordHash())).isTrue();
        assertThat(demoPasswordEncoder.matches("Conductor@123", demo("conductor").getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("no demo account is granted SUPER_ADMIN membership")
    void demoUsersHaveNoSuperAdminMembership() {
        runInitializer();

        for (String username : DEMO_USERNAMES) {
            assertThat(superAdminProfileRepository.findByUserId(demo(username).getId()))
                    .as("%s must not be a super admin", username)
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("only a deleted demo account is recreated; the others are left alone")
    void recreatesOnlyMissingAccounts() {
runInitializer();
        User depotHeadBefore = demo("depothead");
        User managerBefore = demo("divisionmanager");

        // Remove the membership row first; the profile table FK blocks a bare
        // users delete. JdbcTemplate avoids needing a surrounding transaction.
        jdbc.update("DELETE FROM depot_heads WHERE user_id = ?", depotHeadBefore.getId());
        jdbc.update("DELETE FROM user_roles WHERE user_id = ?", depotHeadBefore.getId());
        jdbc.update("DELETE FROM users WHERE id = ?", depotHeadBefore.getId());
        assertThat(userRepository.findByUsername("depothead")).isEmpty();

        runInitializer();

        assertThat(userRepository.findByUsername("depothead")).isPresent();
        assertThat(userRepository.count()).isEqualTo(7L);
        assertThat(demo("divisionmanager").getId())
                .as("surviving accounts must keep their original identity")
                .isEqualTo(managerBefore.getId());
    }
}

