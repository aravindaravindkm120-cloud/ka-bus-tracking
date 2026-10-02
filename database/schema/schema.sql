-- ============================================================
-- KA Bus Tracking - MySQL Schema
-- Karnataka-wide public bus tracking system
--
-- Engine:   InnoDB
-- Charset:  utf8mb4 (full Unicode incl. Kannada labels)
-- IDs:      BIGINT AUTO_INCREMENT
-- Timestamps: created_at / updated_at on every business table
--
-- Hierarchy: corporation -> division -> depot -> town -> bus
--             + crew, trips, GPS, ads, notifications, audit
-- ============================================================

-- ------------------------------------------------------------------
-- 1. AUTHENTICATION & AUTHORIZATION
-- ------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS roles (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code                VARCHAR(40)     NOT NULL,
    name                VARCHAR(80)     NOT NULL,
    description         VARCHAR(255)    NULL,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_roles_code (code)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS users (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username            VARCHAR(60)     NOT NULL,
    email               VARCHAR(120)    NULL,
    phone               VARCHAR(20)     NULL,
    full_name           VARCHAR(120)    NOT NULL,
    password_hash       VARCHAR(100)    NOT NULL,
    enabled             TINYINT(1)      NOT NULL DEFAULT 1,
    locked              TINYINT(1)      NOT NULL DEFAULT 0,
    must_change_password TINYINT(1)     NOT NULL DEFAULT 0,
    -- Authorization scope (never derived from client input).
    -- Exactly the columns relevant to a user's role are set:
    --   SUPER_ADMIN   : all NULL (whole system)
    --   DIVISION_ADMIN: division_id set
    --   DIVISION_MANAGER: division_id set
    --   DEPOT_HEAD    : depot_id set
    --   TOWN_MANAGER  : town_id set
    division_id         BIGINT UNSIGNED NULL,
    depot_id            BIGINT UNSIGNED NULL,
    town_id             BIGINT UNSIGNED NULL,
    last_login_at       DATETIME(3)     NULL,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_username (username),
    UNIQUE KEY uq_users_email (email),
    KEY idx_users_division (division_id),
    KEY idx_users_depot (depot_id),
    KEY idx_users_town (town_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS user_roles (
    user_id             BIGINT UNSIGNED NOT NULL,
    role_id             BIGINT UNSIGNED NOT NULL,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id, role_id),
    KEY idx_user_roles_role (role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id             BIGINT UNSIGNED NOT NULL,
    authenticated_role  VARCHAR(40)     NOT NULL,
    token_hash          CHAR(64)        NOT NULL,
    expires_at          DATETIME(3)     NOT NULL,
    revoked             TINYINT(1)      NOT NULL DEFAULT 0,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_refresh_tokens_hash (token_hash),
    KEY idx_refresh_tokens_user (user_id),
    KEY idx_refresh_tokens_revoke (user_id, revoked),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ------------------------------------------------------------------
-- 2. ORGANIZATION HIERARCHY
-- ------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS corporations (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code                VARCHAR(20)     NOT NULL,
    name                VARCHAR(160)    NOT NULL,
    address             VARCHAR(255)    NULL,
    city                VARCHAR(80)     NULL,
    state               VARCHAR(40)     NOT NULL DEFAULT 'Karnataka',
    contact_email       VARCHAR(120)    NULL,
    contact_phone       VARCHAR(20)     NULL,
    enabled             TINYINT(1)      NOT NULL DEFAULT 1,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_corporations_code (code)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS divisions (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    corporation_id      BIGINT UNSIGNED NOT NULL,
    code                VARCHAR(20)     NOT NULL,
    name                VARCHAR(160)    NOT NULL,
    head_office         VARCHAR(160)    NULL,
    enabled             TINYINT(1)      NOT NULL DEFAULT 1,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_divisions_corp_code (corporation_id, code),
    KEY idx_divisions_name (name),
    KEY idx_divisions_corp (corporation_id),
    CONSTRAINT fk_divisions_corp FOREIGN KEY (corporation_id) REFERENCES corporations (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS depots (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    division_id         BIGINT UNSIGNED NOT NULL,
    code                VARCHAR(20)     NOT NULL,
    name                VARCHAR(160)    NOT NULL,
    address             VARCHAR(255)    NULL,
    phone               VARCHAR(20)     NULL,
    enabled             TINYINT(1)      NOT NULL DEFAULT 1,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_depots_division_code (division_id, code),
    KEY idx_depots_name (name),
    KEY idx_depots_division (division_id),
    CONSTRAINT fk_depots_division FOREIGN KEY (division_id) REFERENCES divisions (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS towns (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    depot_id            BIGINT UNSIGNED NOT NULL,
    code                VARCHAR(20)     NOT NULL,
    name                VARCHAR(160)    NOT NULL,
    enabled             TINYINT(1)      NOT NULL DEFAULT 1,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_towns_depot_code (depot_id, code),
    KEY idx_towns_name (name),
    KEY idx_towns_depot (depot_id),
    CONSTRAINT fk_towns_depot FOREIGN KEY (depot_id) REFERENCES depots (id)
) ENGINE=InnoDB;

-- ------------------------------------------------------------------
-- Role profile tables (STRICT role-specific login).
--
-- Membership in an admin role is a single row in exactly one of these
-- tables. Authentication happens against users.password_hash only; the
-- profile tables decide WHICH role a user may assume. A user with valid
-- credentials but no row in the requested role table is rejected with
-- 403. Crew membership (DRIVER/CONDUCTOR) lives in the crew table via
-- user_id. Ordering matters: divisions/depots/towns are defined above.
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS super_admins (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id             BIGINT UNSIGNED NOT NULL,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_super_admins_user (user_id),
    CONSTRAINT fk_super_admins_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS division_admins (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id             BIGINT UNSIGNED NOT NULL,
    division_id         BIGINT UNSIGNED NOT NULL,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_division_admins_user (user_id),
    KEY idx_division_admins_division (division_id),
    CONSTRAINT fk_division_admins_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_division_admins_division FOREIGN KEY (division_id) REFERENCES divisions (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Corporation-level DIVISION_ADMIN membership (exactly one user per
-- corporation; the user administers the WHOLE corporation). Rows here give a
-- DIVISION_ADMIN login corporation-wide scope via the signed JWT.
CREATE TABLE IF NOT EXISTS corporation_admins (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id             BIGINT UNSIGNED NOT NULL,
    corporation_id      BIGINT UNSIGNED NOT NULL,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_corporation_admins_user (user_id),
    KEY idx_corporation_admins_corporation (corporation_id),
    CONSTRAINT fk_corporation_admins_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_corporation_admins_corporation FOREIGN KEY (corporation_id) REFERENCES corporations (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS division_managers (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id             BIGINT UNSIGNED NOT NULL,
    division_id         BIGINT UNSIGNED NOT NULL,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_division_managers_user (user_id),
    KEY idx_division_managers_division (division_id),
    CONSTRAINT fk_division_managers_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_division_managers_division FOREIGN KEY (division_id) REFERENCES divisions (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS depot_heads (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id             BIGINT UNSIGNED NOT NULL,
    depot_id            BIGINT UNSIGNED NOT NULL,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_depot_heads_user (user_id),
    KEY idx_depot_heads_depot (depot_id),
    CONSTRAINT fk_depot_heads_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_depot_heads_depot FOREIGN KEY (depot_id) REFERENCES depots (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS town_managers (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id             BIGINT UNSIGNED NOT NULL,
    town_id             BIGINT UNSIGNED NOT NULL,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_town_managers_user (user_id),
    KEY idx_town_managers_town (town_id),
    CONSTRAINT fk_town_managers_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_town_managers_town FOREIGN KEY (town_id) REFERENCES towns (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ------------------------------------------------------------------
-- 3. STAFF & CREW
-- ------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS staff (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id             BIGINT UNSIGNED NULL,
    corporation_id      BIGINT UNSIGNED NOT NULL,
    division_id         BIGINT UNSIGNED NOT NULL,
    depot_id            BIGINT UNSIGNED NOT NULL,
    town_id             BIGINT UNSIGNED NULL,
    emp_code            VARCHAR(30)     NULL,
    full_name           VARCHAR(120)    NOT NULL,
    phone               VARCHAR(20)     NULL,
    designation         VARCHAR(80)     NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_staff_emp_code (emp_code),
    KEY idx_staff_user (user_id),
    KEY idx_staff_corp (corporation_id),
    KEY idx_staff_division (division_id),
    KEY idx_staff_depot (depot_id),
    KEY idx_staff_town (town_id),
    KEY idx_staff_status (status),
    CONSTRAINT fk_staff_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_staff_corp FOREIGN KEY (corporation_id) REFERENCES corporations (id),
    CONSTRAINT fk_staff_division FOREIGN KEY (division_id) REFERENCES divisions (id),
    CONSTRAINT fk_staff_depot FOREIGN KEY (depot_id) REFERENCES depots (id),
    CONSTRAINT fk_staff_town FOREIGN KEY (town_id) REFERENCES towns (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS crew (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    staff_id            BIGINT UNSIGNED NULL,
    user_id             BIGINT UNSIGNED NULL,
    badge_no            VARCHAR(30)     NOT NULL,
    full_name           VARCHAR(120)    NOT NULL,
    phone               VARCHAR(20)     NULL,
    crew_type           VARCHAR(40)     NOT NULL,       -- DRIVER | CONDUCTOR
    license_no          VARCHAR(40)     NULL,
    license_expiry      DATE            NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | SUSPENDED | INACTIVE
    duty_status         VARCHAR(20)     NOT NULL DEFAULT 'OFF_DUTY', -- ON_DUTY | OFF_DUTY
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_crew_badge (badge_no),
    KEY idx_crew_user (user_id),
    KEY idx_crew_staff (staff_id),
    KEY idx_crew_type_status (crew_type, status),
    CONSTRAINT fk_crew_staff FOREIGN KEY (staff_id) REFERENCES staff (id) ON DELETE SET NULL,
    CONSTRAINT fk_crew_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ------------------------------------------------------------------
-- 4. BUSES & DOCUMENTS
-- ------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS bus_numbers (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    town_id             BIGINT UNSIGNED NOT NULL,
    depot_id            BIGINT UNSIGNED NOT NULL,
    division_id         BIGINT UNSIGNED NOT NULL,
    corporation_id      BIGINT UNSIGNED NOT NULL,
    bus_number          VARCHAR(20)     NOT NULL,
    bus_type            VARCHAR(40)     NOT NULL DEFAULT 'ORDINARY', -- ORDINARY | EXPRESS | RAJADHARSHA
    enabled             TINYINT(1)      NOT NULL DEFAULT 1,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    -- A bus number is unique within its depot, not system-wide.
    UNIQUE KEY uq_bus_numbers_depot_number (depot_id, bus_number),
    KEY idx_bus_numbers_corporation (corporation_id),
    KEY idx_bus_numbers_division (division_id),
    KEY idx_bus_numbers_town (town_id),
    CONSTRAINT fk_bus_numbers_town FOREIGN KEY (town_id) REFERENCES towns (id),
    CONSTRAINT fk_bus_numbers_depot FOREIGN KEY (depot_id) REFERENCES depots (id),
    CONSTRAINT fk_bus_numbers_division FOREIGN KEY (division_id) REFERENCES divisions (id),
    CONSTRAINT fk_bus_numbers_corporation FOREIGN KEY (corporation_id) REFERENCES corporations (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS buses (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    bus_number_id       BIGINT UNSIGNED NOT NULL,   -- the bus number master this vehicle runs on
    town_id             BIGINT UNSIGNED NOT NULL,
    depot_id            BIGINT UNSIGNED NOT NULL,   -- denormalized for fast filters
    capacity            INT             NOT NULL DEFAULT 40,
    fuel_type           VARCHAR(20)     NOT NULL DEFAULT 'DIESEL',
    make_model          VARCHAR(80)     NULL,
    manufacture_year    INT             NULL,
    gps_device_id       VARCHAR(60)     NULL,
    gps_enabled         TINYINT(1)      NOT NULL DEFAULT 1,
    status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | MAINTENANCE | INACTIVE | RETIRED
    enabled             TINYINT(1)      NOT NULL DEFAULT 1,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_buses_bus_number (bus_number_id),
    KEY idx_buses_town (town_id),
    KEY idx_buses_depot (depot_id),
    KEY idx_buses_status (status),
    CONSTRAINT fk_buses_bus_number FOREIGN KEY (bus_number_id) REFERENCES bus_numbers (id),
    CONSTRAINT fk_buses_town FOREIGN KEY (town_id) REFERENCES towns (id),
    CONSTRAINT fk_buses_depot FOREIGN KEY (depot_id) REFERENCES depots (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS bus_documents (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    bus_id              BIGINT UNSIGNED NOT NULL,
    doc_type            VARCHAR(40)     NOT NULL,   -- REGISTRATION | INSURANCE | FITNESS | TAX | PERMIT
    doc_number          VARCHAR(60)     NOT NULL,
    issued_date         DATE            NULL,
    expiry_date         DATE            NULL,
    file_url            VARCHAR(500)    NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'VALID', -- VALID | EXPIRING | EXPIRED | REVOKED
    notes               VARCHAR(255)    NULL,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_bus_docs (bus_id, doc_type),
    KEY idx_bus_docs_expiry (expiry_date),
    CONSTRAINT fk_bus_docs_bus FOREIGN KEY (bus_id) REFERENCES buses (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ------------------------------------------------------------------
-- 5. ROUTES & STOPS
-- ------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS routes (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    division_id         BIGINT UNSIGNED NOT NULL,
    code                VARCHAR(20)     NOT NULL,
    name                VARCHAR(160)    NOT NULL,
    origin              VARCHAR(120)    NOT NULL,
    destination         VARCHAR(120)    NOT NULL,
    distance_km         DECIMAL(8,2)    NULL,
    est_duration_min    INT             NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | SUSPENDED | INACTIVE
    enabled             TINYINT(1)      NOT NULL DEFAULT 1,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_routes_div_code (division_id, code),
    KEY idx_routes_division (division_id),
    KEY idx_routes_origin_dest (origin, destination),
    CONSTRAINT fk_routes_division FOREIGN KEY (division_id) REFERENCES divisions (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS route_stops (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    route_id            BIGINT UNSIGNED NOT NULL,
    stop_order          INT             NOT NULL,
    stop_name           VARCHAR(160)    NOT NULL,
    latitude            DECIMAL(8,6)    NOT NULL,
    longitude           DECIMAL(9,6)    NOT NULL,
    distance_from_start DECIMAL(8,2)    NULL DEFAULT 0,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_route_stops (route_id, stop_order),
    KEY idx_route_stops_route (route_id),
    CONSTRAINT fk_route_stops_route FOREIGN KEY (route_id) REFERENCES routes (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ------------------------------------------------------------------
-- 6. TRIPS, ASSIGNMENTS
-- ------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS trips (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    route_id            BIGINT UNSIGNED NOT NULL,
    bus_id              BIGINT UNSIGNED NOT NULL,
    trip_number         VARCHAR(30)     NOT NULL,
    trip_date           DATE            NOT NULL,
    scheduled_departure DATETIME(3)     NOT NULL,
    scheduled_arrival   DATETIME(3)     NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'SCHEDULED', -- SCHEDULED | RUNNING | COMPLETED | CANCELLED | ENDED
    direction           VARCHAR(10)     NOT NULL DEFAULT 'OUTBOUND',  -- OUTBOUND | INBOUND
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_trips_number_date (trip_number, trip_date),
    KEY idx_trips_route (route_id),
    KEY idx_trips_bus (bus_id),
    KEY idx_trips_date_status (trip_date, status),
    KEY idx_trips_running (status, scheduled_departure),
    CONSTRAINT fk_trips_route FOREIGN KEY (route_id) REFERENCES routes (id),
    CONSTRAINT fk_trips_bus FOREIGN KEY (bus_id) REFERENCES buses (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS trip_stops (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    trip_id             BIGINT UNSIGNED NOT NULL,
    stop_order          INT             NOT NULL,
    stop_name           VARCHAR(160)    NOT NULL,
    latitude            DECIMAL(8,6)    NOT NULL,
    longitude           DECIMAL(9,6)    NOT NULL,
    planned_time        DATETIME(3)     NULL,
    actual_time         DATETIME(3)     NULL,
    distance_from_start DECIMAL(8,2)    NULL DEFAULT 0,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_trip_stops (trip_id, stop_order),
    KEY idx_trip_stops_trip (trip_id),
    CONSTRAINT fk_trip_stops_trip FOREIGN KEY (trip_id) REFERENCES trips (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS crew_assignments (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    trip_id             BIGINT UNSIGNED NOT NULL,
    crew_id             BIGINT UNSIGNED NOT NULL,
    crew_type           VARCHAR(40)     NOT NULL,    -- DRIVER | CONDUCTOR
    assigned_from       DATETIME(3)     NULL,
    assigned_to         DATETIME(3)     NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | COMPLETED | CANCELLED
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_crew_assign_active (trip_id, crew_id, status),
    KEY idx_crew_assign_crew (crew_id),
    KEY idx_crew_assign_trip (trip_id),
    KEY idx_crew_assign_active (status, assigned_from),
    CONSTRAINT fk_crew_assign_trip FOREIGN KEY (trip_id) REFERENCES trips (id),
    CONSTRAINT fk_crew_assign_crew FOREIGN KEY (crew_id) REFERENCES crew (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS bus_assignments (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    trip_id             BIGINT UNSIGNED NOT NULL,
    bus_id              BIGINT UNSIGNED NOT NULL,
    assigned_from       DATETIME(3)     NULL,
    assigned_to         DATETIME(3)     NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | COMPLETED | CANCELLED
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_bus_assign_active (trip_id, bus_id, status),
    KEY idx_bus_assign_bus (bus_id),
    KEY idx_bus_assign_trip (trip_id),
    CONSTRAINT fk_bus_assign_trip FOREIGN KEY (trip_id) REFERENCES trips (id),
    CONSTRAINT fk_bus_assign_bus FOREIGN KEY (bus_id) REFERENCES buses (id)
) ENGINE=InnoDB;

-- ------------------------------------------------------------------
-- 7. GPS SESSIONS & LIVE LOCATIONS
-- ------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS gps_sessions (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    trip_id             BIGINT UNSIGNED NOT NULL,
    bus_id              BIGINT UNSIGNED NOT NULL,
    crew_id             BIGINT UNSIGNED NOT NULL,   -- the crew who started the session
    session_key         CHAR(36)        NOT NULL,
    started_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ended_at            DATETIME(3)     NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | ENDED | EXPIRED
    start_latitude      DECIMAL(8,6)    NULL,
    start_longitude     DECIMAL(9,6)    NULL,
    device_id           VARCHAR(80)     NULL,
    app_version         VARCHAR(20)     NULL,
    last_heartbeat_at   DATETIME(3)     NULL,
    updates_count       BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_gps_sessions_key (session_key),
    KEY idx_gps_sessions_bus (bus_id, status),
    KEY idx_gps_sessions_trip (trip_id),
    KEY idx_gps_sessions_crew (crew_id, status),
    KEY idx_gps_sessions_active (status, started_at),
    CONSTRAINT fk_gps_sessions_trip FOREIGN KEY (trip_id) REFERENCES trips (id),
    CONSTRAINT fk_gps_sessions_bus FOREIGN KEY (bus_id) REFERENCES buses (id),
    CONSTRAINT fk_gps_sessions_crew FOREIGN KEY (crew_id) REFERENCES crew (id)
) ENGINE=InnoDB;

-- Current/latest location only. One row per tracked bus.
CREATE TABLE IF NOT EXISTS live_locations (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    bus_id              BIGINT UNSIGNED NOT NULL,
    trip_id             BIGINT UNSIGNED NULL,
    gps_session_id      BIGINT UNSIGNED NULL,
    route_id            BIGINT UNSIGNED NULL,
    latitude            DECIMAL(8,6)    NOT NULL,
    longitude           DECIMAL(9,6)    NOT NULL,
    speed_kmh           DECIMAL(6,2)    NOT NULL DEFAULT 0,
    heading             DECIMAL(6,2)    NOT NULL DEFAULT 0,
    accuracy_m          DECIMAL(7,2)    NOT NULL DEFAULT 0,
    altitude_m          DECIMAL(8,2)    NULL,
    captured_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    status              VARCHAR(20)     NOT NULL DEFAULT 'LIVE', -- LIVE | STALE | OFFLINE
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_live_locations_bus (bus_id),
    KEY idx_live_locations_status (status),
    KEY idx_live_locations_captured (captured_at),
    KEY idx_live_locations_trip (trip_id),
    KEY idx_live_locations_route (route_id),
    KEY idx_live_locations_compound (status, captured_at DESC),
    CONSTRAINT fk_live_bus FOREIGN KEY (bus_id) REFERENCES buses (id) ON DELETE CASCADE,
    CONSTRAINT fk_live_trip FOREIGN KEY (trip_id) REFERENCES trips (id) ON DELETE SET NULL,
    CONSTRAINT fk_live_gps_session FOREIGN KEY (gps_session_id) REFERENCES gps_sessions (id) ON DELETE SET NULL,
    CONSTRAINT fk_live_route FOREIGN KEY (route_id) REFERENCES routes (id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- Historical GPS trace. Grows large -> retention policy applies.
CREATE TABLE IF NOT EXISTS location_history (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    bus_id              BIGINT UNSIGNED NOT NULL,
    trip_id             BIGINT UNSIGNED NULL,
    gps_session_id      BIGINT UNSIGNED NULL,
    latitude            DECIMAL(8,6)    NOT NULL,
    longitude           DECIMAL(9,6)    NOT NULL,
    speed_kmh           DECIMAL(6,2)    NOT NULL DEFAULT 0,
    heading             DECIMAL(6,2)    NOT NULL DEFAULT 0,
    accuracy_m          DECIMAL(7,2)    NOT NULL DEFAULT 0,
    altitude_m          DECIMAL(8,2)    NULL,
    captured_at         DATETIME(3)     NOT NULL,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_loc_hist_bus_time (bus_id, captured_at),
    KEY idx_loc_hist_trip (trip_id, captured_at),
    KEY idx_loc_hist_session (gps_session_id),
    KEY idx_loc_hist_compound (gps_session_id, captured_at DESC),
    KEY idx_loc_hist_captured (captured_at),
    CONSTRAINT fk_loc_hist_bus FOREIGN KEY (bus_id) REFERENCES buses (id) ON DELETE CASCADE,
    CONSTRAINT fk_loc_hist_trip FOREIGN KEY (trip_id) REFERENCES trips (id) ON DELETE SET NULL,
    CONSTRAINT fk_loc_hist_session FOREIGN KEY (gps_session_id) REFERENCES gps_sessions (id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ------------------------------------------------------------------
-- 8. PASSENGER MEMORY (no login; identified by device id)
-- ------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS favorites (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    device_id           VARCHAR(80)     NOT NULL,
    user_id             BIGINT UNSIGNED NULL,
    item_type           VARCHAR(20)     NOT NULL,   -- ROUTE | BUS | PLACE | STOP
    item_id             BIGINT UNSIGNED NULL,
    label               VARCHAR(160)    NULL,
    latitude            DECIMAL(8,6)    NULL,
    longitude           DECIMAL(9,6)    NULL,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_favorites_device (device_id),
    KEY idx_favorites_user (user_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS recent_searches (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    device_id           VARCHAR(80)     NOT NULL,
    user_id             BIGINT UNSIGNED NULL,
    from_name           VARCHAR(160)    NOT NULL,
    to_name             VARCHAR(160)    NOT NULL,
    searched_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_recent_device (device_id, searched_at),
    KEY idx_recent_user (user_id, searched_at),
    KEY idx_recent_searched (searched_at)
) ENGINE=InnoDB;

-- ------------------------------------------------------------------
-- 9. NOTIFICATIONS
-- ------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS notifications (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id             BIGINT UNSIGNED NOT NULL,
    type                VARCHAR(40)     NOT NULL,   -- TRIP_ALERT | SYSTEM | AD | ...
    title               VARCHAR(200)    NOT NULL,
    body                VARCHAR(1000)   NULL,
    data_json           JSON            NULL,
    is_read             TINYINT(1)      NOT NULL DEFAULT 0,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_notifications_user (user_id, is_read, created_at),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ------------------------------------------------------------------
-- 10. ADVERTISING (centralized ad system)
-- ------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS ad_campaigns (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    title               VARCHAR(200)    NOT NULL,
    image_url           VARCHAR(500)    NOT NULL,
    target_url          VARCHAR(500)    NULL,
    placement           VARCHAR(60)     NOT NULL,   -- see ad_placements.code
    start_at            DATETIME(3)     NOT NULL,
    end_at              DATETIME(3)     NULL,
    enabled             TINYINT(1)      NOT NULL DEFAULT 1,
    priority            INT             NOT NULL DEFAULT 0,
    max_impressions     BIGINT UNSIGNED NULL,
    impressions_count   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_by          BIGINT UNSIGNED NULL,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_ad_campaigns_placement (placement, enabled, priority),
    KEY idx_ad_campaigns_window (start_at, end_at),
    CONSTRAINT fk_ad_campaigns_creator FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS ad_placements (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code                VARCHAR(60)     NOT NULL,
    name                VARCHAR(120)    NOT NULL,
    description         VARCHAR(255)    NULL,
    duration_seconds    INT             NOT NULL DEFAULT 5,
    frequency_seconds   INT             NOT NULL DEFAULT 0,
    enabled             TINYINT(1)      NOT NULL DEFAULT 1,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_ad_placements_code (code)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS ad_impressions (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    campaign_id         BIGINT UNSIGNED NOT NULL,
    placement_id        BIGINT UNSIGNED NOT NULL,
    device_id           VARCHAR(80)     NULL,
    user_id             BIGINT UNSIGNED NULL,
    viewed_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    duration_viewed_ms  INT             NOT NULL DEFAULT 0,
    clicked             TINYINT(1)      NOT NULL DEFAULT 0,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_ad_impressions_campaign (campaign_id, viewed_at),
    KEY idx_ad_impressions_placement (placement_id, viewed_at),
    KEY idx_ad_impressions_device (device_id, viewed_at),
    CONSTRAINT fk_ad_impressions_campaign FOREIGN KEY (campaign_id) REFERENCES ad_campaigns (id) ON DELETE CASCADE,
    CONSTRAINT fk_ad_impressions_placement FOREIGN KEY (placement_id) REFERENCES ad_placements (id) ON DELETE CASCADE,
    CONSTRAINT fk_ad_impressions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ------------------------------------------------------------------
-- 11. AUDIT & SETTINGS
-- ------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS audit_logs (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id             BIGINT UNSIGNED NULL,
    action              VARCHAR(80)     NOT NULL,
    resource_type       VARCHAR(60)     NULL,
    resource_id         BIGINT UNSIGNED NULL,
    detail_json         JSON            NULL,
    ip_address          VARCHAR(45)     NULL,
    user_agent          VARCHAR(255)    NULL,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_audit_user (user_id, created_at),
    KEY idx_audit_action (action, created_at),
    KEY idx_audit_resource (resource_type, resource_id, created_at),
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS system_settings (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    setting_key         VARCHAR(80)     NOT NULL,
    setting_value       TEXT            NOT NULL,
    description         VARCHAR(255)    NULL,
    updated_by          BIGINT UNSIGNED NULL,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_settings_key (setting_key)
) ENGINE=InnoDB;

-- ------------------------------------------------------------------
-- Schema complete.
-- Verify with:
--   mysql -u root -p < database/schema/schema.sql
-- ============================================================