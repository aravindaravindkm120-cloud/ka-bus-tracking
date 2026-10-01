package com.kabus.tracking.config;

import com.kabus.tracking.domain.entity.Corporation;
import com.kabus.tracking.domain.entity.CorporationAdminProfile;
import com.kabus.tracking.domain.entity.Crew;
import com.kabus.tracking.domain.entity.Depot;
import com.kabus.tracking.domain.entity.DepotHeadProfile;
import com.kabus.tracking.domain.entity.Division;
import com.kabus.tracking.domain.entity.DivisionManagerProfile;
import com.kabus.tracking.domain.entity.Role;
import com.kabus.tracking.domain.entity.SuperAdminProfile;
import com.kabus.tracking.domain.entity.Town;
import com.kabus.tracking.domain.entity.TownManagerProfile;
import com.kabus.tracking.domain.entity.User;
import com.kabus.tracking.domain.enums.RoleCode;
import com.kabus.tracking.domain.repository.CorporationAdminProfileRepository;
import com.kabus.tracking.domain.repository.CorporationRepository;
import com.kabus.tracking.domain.repository.CrewRepository;
import com.kabus.tracking.domain.repository.DepotHeadProfileRepository;
import com.kabus.tracking.domain.repository.DepotRepository;
import com.kabus.tracking.domain.repository.DivisionManagerProfileRepository;
import com.kabus.tracking.domain.repository.DivisionRepository;
import com.kabus.tracking.domain.repository.RoleRepository;
import com.kabus.tracking.domain.repository.SuperAdminProfileRepository;
import com.kabus.tracking.domain.repository.TownManagerProfileRepository;
import com.kabus.tracking.domain.repository.TownRepository;
import com.kabus.tracking.domain.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * Bootstraps the role catalog, the SUPER_ADMIN login + its super_admins
 * membership row, and - only when {@code SEED_DEMO_USERS=true} or the dev
 * profile is active - development accounts for every role. Role membership is
 * stored strictly in the role profile tables / crew table; no account ever
 * gains a role through the generic user_roles table.
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final DivisionRepository divisionRepository;
    private final DepotRepository depotRepository;
    private final TownRepository townRepository;
    private final CorporationRepository corporationRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final CrewRepository crewRepository;
    private final SuperAdminProfileRepository superAdminProfileRepository;
    private final CorporationAdminProfileRepository corporationAdminProfileRepository;
    private final DivisionManagerProfileRepository divisionManagerProfileRepository;
    private final DepotHeadProfileRepository depotHeadProfileRepository;
    private final TownManagerProfileRepository townManagerProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;

    public DataInitializer(DivisionRepository divisionRepository,
                           DepotRepository depotRepository,
                           TownRepository townRepository,
                           CorporationRepository corporationRepository,
                           RoleRepository roleRepository,
                           UserRepository userRepository,
                           CrewRepository crewRepository,
                           SuperAdminProfileRepository superAdminProfileRepository,
                           CorporationAdminProfileRepository corporationAdminProfileRepository,
                           DivisionManagerProfileRepository divisionManagerProfileRepository,
                           DepotHeadProfileRepository depotHeadProfileRepository,
                           TownManagerProfileRepository townManagerProfileRepository,
                           PasswordEncoder passwordEncoder,
                           AppProperties props) {
        this.divisionRepository = divisionRepository;
        this.depotRepository = depotRepository;
        this.townRepository = townRepository;
        this.corporationRepository = corporationRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.crewRepository = crewRepository;
        this.superAdminProfileRepository = superAdminProfileRepository;
        this.corporationAdminProfileRepository = corporationAdminProfileRepository;
        this.divisionManagerProfileRepository = divisionManagerProfileRepository;
        this.depotHeadProfileRepository = depotHeadProfileRepository;
        this.townManagerProfileRepository = townManagerProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensureRoles();
        ensureSuperAdmin();
        ensureDemoUsersIfEnabled();
    }

    private void ensureRoles() {
        Map<RoleCode, String[]> definitions = Map.of(
                RoleCode.SUPER_ADMIN, new String[]{"Super Admin", "Whole-system access"},
                RoleCode.DIVISION_ADMIN, new String[]{"Division Admin", "Admin of assigned division(s)"},
                RoleCode.DIVISION_MANAGER, new String[]{"Division Manager", "Operations of assigned division"},
                RoleCode.DEPOT_HEAD, new String[]{"Depot Head", "Operations of assigned depot"},
                RoleCode.TOWN_MANAGER, new String[]{"Town Manager", "Operations of assigned town"},
                RoleCode.DRIVER, new String[]{"Driver", "Operational crew - bus driver"},
                RoleCode.CONDUCTOR, new String[]{"Conductor", "Operational crew - bus conductor"});

        for (Map.Entry<RoleCode, String[]> e : definitions.entrySet()) {
            roleRepository.findByCode(e.getKey()).orElseGet(() ->
                    roleRepository.save(new com.kabus.tracking.domain.entity.Role(e.getKey(), e.getValue()[0], e.getValue()[1])));
        }
    }

    private void ensureSuperAdmin() {
        AppProperties.Seed seed = props.getSeed();
        Optional<User> existing = userRepository.findByUsername(seed.getAdminUsername());
        User admin = existing.orElseGet(() -> {
            User created = new User();
            created.setUsername(seed.getAdminUsername());
            created.setFullName(seed.getAdminFullName());
            created.setEmail(seed.getAdminEmail());
            created.setPasswordHash(passwordEncoder.encode(seed.getAdminPassword()));
            User saved = userRepository.save(created);
            log.info("Created SUPER_ADMIN account '{}'. CHANGE THE PASSWORD in production.",
                    seed.getAdminUsername());
            return saved;
        });
        if (superAdminProfileRepository.findByUserId(admin.getId()).isEmpty()) {
            SuperAdminProfile profile = new SuperAdminProfile();
            profile.setUser(admin);
            superAdminProfileRepository.save(profile);
        }
    }

    /**
     * Creates the demo/test account for every non-SUPER_ADMIN role.
     *
     * <p>Idempotency is per account, never global: each demo account is created
     * only when its own username is absent, and each role membership only when
     * that specific user lacks it. There is deliberately no
     * {@code userRepository.count()} guard - {@link #ensureSuperAdmin()} always
     * runs first and guarantees at least one row exists, so a global count
     * check can never let the demo seed through once SUPER_ADMIN exists.
     * Re-running this method is therefore a no-op, which makes restarts safe.</p>
     *
     * <p>The SUPER_ADMIN account is never read or written by this method.</p>
     */
    private void ensureDemoUsersIfEnabled() {
        if (!"true".equalsIgnoreCase(System.getenv("SEED_DEMO_USERS"))
                && !"dev".equalsIgnoreCase(System.getProperty("spring.profiles.active", ""))) {
            return;
        }

        Optional<Division> division = divisionRepository.findAll().stream().findFirst();
        Optional<Depot> depot = depotRepository.findAll().stream().findFirst();
        Optional<Town> town = townRepository.findAll().stream().findFirst();
        if (division.isEmpty() || depot.isEmpty() || town.isEmpty()) {
            log.info("Demo users skipped: seed organization not found. Run database/seed/seed.sql first.");
            return;
        }

        // DIVISION_ADMIN is a corporation-wide role: resolve the corporation that
        // owns the seed division and record corporation_admins membership.
        Corporation corporation = division.get().getCorporation();
        if (corporation == null) {
            corporation = corporationRepository.findAll().stream().findFirst().orElse(null);
        }
        if (corporation == null) {
            log.info("Demo users skipped: no corporation found for the seed division.");
            return;
        }

        createAdminProfile("divisionadmin", "DivAdmin@123", "Division Admin",
                RoleCode.DIVISION_ADMIN, corporation, null, null, null);
        createAdminProfile("divisionmanager", "DivMgr@123", "Division Manager",
                RoleCode.DIVISION_MANAGER, null, division.get(), null, null);
        createAdminProfile("depothead", "DepotHead@123", "Depot Head",
                RoleCode.DEPOT_HEAD, null, null, depot.get(), null);
        createAdminProfile("townmanager", "TownMgr@123", "Town Manager",
                RoleCode.TOWN_MANAGER, null, null, null, town.get());

        createCrewUser("driver", "Driver@123", "Driver Ramesh");
        createCrewUser("conductor", "Conductor@123", "Conductor Suresh");

        linkCrewAccount("driver", "CRW-DRV-001");
        linkCrewAccount("conductor", "CRW-CND-001");
        log.info("Ensured development accounts: divisionadmin, divisionmanager, depothead, townmanager, driver, conductor. "
                + "DISABLE with SEED_DEMO_USERS=false in production.");
    }

    /**
     * Creates the user row for a demo account only when that username is free.
     * The email is derived from the username and is therefore unique per
     * account, which also satisfies the {@code uq_users_email} unique key.
     * An existing account is never re-passworded or otherwise modified.
     */
    private User createUser(String username, String password, String fullName) {
        return userRepository.findByUsername(username).orElseGet(() -> {
            String email = username + "@kabus.dev";
            User user = new User();
            user.setUsername(username);
            user.setFullName(fullName);
            user.setEmail(userRepository.existsByEmail(email) ? null : email);
            user.setPasswordHash(passwordEncoder.encode(password));
            return userRepository.save(user);
        });
    }

    private void createAdminProfile(String username, String password, String fullName,
                                    RoleCode role, Corporation corporation,
                                    Division division, Depot depot, Town town) {
        User user = createUser(username, password, fullName);
        Role roleEntity = roleRepository.findByCode(role)
                .orElseGet(() -> roleRepository.save(new Role(role, role.name(), null)));
        if (user.getRoles().stream().noneMatch(r -> r.getCode() == role)) {
            user.getRoles().add(roleEntity);
            userRepository.save(user);
        }
        switch (role) {
            case DIVISION_ADMIN -> {
                if (corporation != null && corporationAdminProfileRepository.findByUserId(user.getId()).isEmpty()) {
                    CorporationAdminProfile p = new CorporationAdminProfile();
                    p.setUser(user);
                    p.setCorporation(corporation);
                    corporationAdminProfileRepository.save(p);
                }
            }
            case DIVISION_MANAGER -> {
                if (division != null && divisionManagerProfileRepository.findByUserId(user.getId()).isEmpty()) {
                    DivisionManagerProfile p = new DivisionManagerProfile();
                    p.setUser(user);
                    p.setDivision(division);
                    divisionManagerProfileRepository.save(p);
                }
            }
            case DEPOT_HEAD -> {
                if (depot != null && depotHeadProfileRepository.findByUserId(user.getId()).isEmpty()) {
                    DepotHeadProfile p = new DepotHeadProfile();
                    p.setUser(user);
                    p.setDepot(depot);
                    depotHeadProfileRepository.save(p);
                }
            }
            case TOWN_MANAGER -> {
                if (town != null && townManagerProfileRepository.findByUserId(user.getId()).isEmpty()) {
                    TownManagerProfile p = new TownManagerProfile();
                    p.setUser(user);
                    p.setTown(town);
                    townManagerProfileRepository.save(p);
                }
            }
            default -> {
                // crew roles use the crew profile only
            }
        }
    }

    private void createCrewUser(String username, String password, String fullName) {
        createUser(username, password, fullName);
    }

    /**
     * Links a crew account to its crew record through {@code crew.user_id}.
     * Crew authentication stays username-based and the crew role still comes
     * from {@code crew.crew_type}; this method only fills in the linkage and
     * never rewrites an existing, different owner.
     */
    private void linkCrewAccount(String username, String badge) {
        userRepository.findByUsername(username)
                .flatMap(user -> crewRepository.findByBadgeNo(badge)
                        .filter(crew -> crew.getUser() == null
                                || crew.getUser().getId().equals(user.getId()))
                        .map(crew -> {
                            crew.setUser(user);
                            return crew;
                        }))
                .ifPresent(crewRepository::save);
    }
}