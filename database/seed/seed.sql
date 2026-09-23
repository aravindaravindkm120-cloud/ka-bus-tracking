-- ============================================================
-- KA Bus Tracking - Seed Data
--
-- Loads reference data ONLY:
--   * roles
--   * sample organization (1 corporation / division / depot / town)
--   * sample staff, crew, buses, route, stops, trips, assignments
--   * ad placements (centralized ad-slot configuration)
--   * system settings (live thresholds, retention, ad defaults)
--
-- The SUPER_ADMIN login is bootstrapped by the backend at first
-- startup (see backend DataInitializer). This keeps a login
-- credential out of the seed SQL and lets the password come
-- from an environment variable (SEED_ADMIN_PASSWORD / default).
-- ============================================================

INSERT INTO roles (code, name, description) VALUES
    ('SUPER_ADMIN',       'Super Admin',            'Whole-system access'),
    ('DIVISION_ADMIN',    'Division Admin',         'Admin of assigned division(s)'),
    ('DIVISION_MANAGER',  'Division Manager',       'Operations of assigned division'),
    ('DEPOT_HEAD',        'Depot Head',             'Operations of assigned depot'),
    ('TOWN_MANAGER',      'Town Manager',           'Operations of assigned town'),
    ('DRIVER',            'Driver',                 'Operational crew - bus driver'),
    ('CONDUCTOR',         'Conductor',              'Operational crew - bus conductor')
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description);

-- ------------------------------------------------------------------
-- Ad placements (centralized ad-slot configuration)
-- ------------------------------------------------------------------
INSERT INTO ad_placements (code, name, description, duration_seconds, frequency_seconds, enabled) VALUES
    ('PASSENGER_WEB_HOME',        'Passenger Web - Home',         'Banner/hero slot on passenger home page', 5, 180, 1),
    ('PASSENGER_WEB_SEARCH',      'Passenger Web - Search',       'Slot beside search results', 5, 180, 1),
    ('PASSENGER_WEB_BUS_DETAILS', 'Passenger Web - Bus Details',  'Slot on bus detail page', 5, 180, 1),
    ('PASSENGER_WEB_MAP_BOTTOM',  'Passenger Web - Map Bottom',   'Bottom card slot on live map', 5, 180, 1),
    ('ADMIN_APP_DASHBOARD',       'Admin App - Dashboard',        'Non-critical dashboard region', 5, 300, 1),
    ('ADMIN_APP_NON_CRITICAL',    'Admin App - Non Critical',     'List/empty regions - never over GPS controls', 5, 300, 1),
    ('CREW_APP_HOME',             'Crew App - Home',              'Home screen, away from Start/End Trip controls', 5, 600, 1),
    ('CREW_APP_NON_CRITICAL',     'Crew App - Non Critical',      'Secondary regions - never over GPS controls', 5, 600, 1)
ON DUPLICATE KEY UPDATE
    name = VALUES(name), duration_seconds = VALUES(duration_seconds),
    frequency_seconds = VALUES(frequency_seconds), enabled = VALUES(enabled);

-- ------------------------------------------------------------------
-- System settings
-- ------------------------------------------------------------------
INSERT INTO system_settings (setting_key, setting_value, description) VALUES
    ('live.threshold.live_seconds',      '60',    'Bus is LIVE when last location update <= this many seconds'),
    ('live.threshold.stale_seconds',    '600',   'Bus becomes OFFLINE when last update older than this (10 min)'),
    ('live.evaluator.enabled',           'true',  'Enable the central live-status evaluator job'),
    ('gps.location.min_interval_ms',   '3000',   'Minimum accepted interval between GPS uploads (rate limit)'),
    ('gps.location.timestamp_max_skew_ms', '30000','Acceptable clock skew between device and server'),
    ('location_history.retention_days', '30',    'How long raw location_history rows are kept'),
    ('location_history.purge_enabled',  'true',  'Enable scheduled purge of old location_history'),
    ('ads.default.duration_seconds',      '5',   'Default dev ad display duration (per placement-configurable)'),
    ('ads.default.frequency_seconds',   '180',   'Default dev ad repeat frequency'),
    ('notifications.channel',            'IN_APP', 'Backend notification channel (IN_APP for now)')
ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value), description = VALUES(description);

-- ------------------------------------------------------------------
-- Karnataka transport corporations (ids always come from MySQL;
-- never assume KSRTC=1, BMTC=2, ...)
-- ------------------------------------------------------------------
INSERT INTO corporations (code, name, city, state) VALUES
    ('KSRTC',  'Karnataka State Road Transport Corporation',        'Bengaluru', 'Karnataka'),
    ('BMTC',   'Bangalore Metropolitan Transport Corporation',      'Bengaluru', 'Karnataka'),
    ('KKRTC',  'Kalyana Karnataka Road Transport Corporation',      'Kalaburagi', 'Karnataka'),
    ('NWKRTC', 'North Western Karnataka Road Transport Corporation', 'Hubballi',  'Karnataka')
ON DUPLICATE KEY UPDATE name = VALUES(name), city = VALUES(city), state = VALUES(state);

INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT id, 'KAD', 'Kadamba Nutrition / Division', 'Karwar'
FROM corporations WHERE code = 'KSRTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'KWD', 'Karwar Depot', 'Karwar Bus Stand Road, Karwar', '08382-226000'
FROM divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE d.code = 'KAD'
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO towns (depot_id, code, name)
SELECT dp.id, 'KWN', 'Karwar Town'
FROM depots dp JOIN divisions d ON d.id = dp.division_id
WHERE dp.code = 'KWD'
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- ------------------------------------------------------------------
-- Sample staff / crew
-- ------------------------------------------------------------------
INSERT INTO staff (corporation_id, division_id, depot_id, town_id, emp_code, full_name, phone, designation, status)
SELECT c.id, d.id, dp.id, t.id, 'EMP-DRV-001', 'Ramesh Kumar', '90300-00001', 'Driver', 'ACTIVE'
FROM corporations c, divisions d, depots dp, towns t
WHERE c.code = 'KSRTC' AND d.code = 'KAD' AND dp.code = 'KWD' AND t.code = 'KWN'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name);

INSERT INTO staff (corporation_id, division_id, depot_id, town_id, emp_code, full_name, phone, designation, status)
SELECT c.id, d.id, dp.id, t.id, 'EMP-CND-001', 'Suresh Patil', '90300-00002', 'Conductor', 'ACTIVE'
FROM corporations c, divisions d, depots dp, towns t
WHERE c.code = 'KSRTC' AND d.code = 'KAD' AND dp.code = 'KWD' AND t.code = 'KWN'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name);

INSERT INTO crew (staff_id, badge_no, full_name, phone, crew_type, license_no, status, duty_status)
SELECT s.id, 'CRW-DRV-001', s.full_name, s.phone, 'DRIVER', 'KA0301200012345', 'ACTIVE', 'OFF_DUTY'
FROM staff s WHERE s.emp_code = 'EMP-DRV-001'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name);

INSERT INTO crew (staff_id, badge_no, full_name, phone, crew_type, status, duty_status)
SELECT s.id, 'CRW-CND-001', s.full_name, s.phone, 'CONDUCTOR', 'ACTIVE', 'OFF_DUTY'
FROM staff s WHERE s.emp_code = 'EMP-CND-001'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name);

-- ------------------------------------------------------------------
-- Sample bus, route, stops, trip
-- ------------------------------------------------------------------
INSERT INTO buses (town_id, depot_id, registration_no, bus_type, capacity, fuel_type, make_model, manufacture_year, status)
SELECT t.id, dp.id, 'KA-30-F-2025', 'ORDINARY', 40, 'DIESEL', 'TATA Starbus', 2023, 'ACTIVE'
FROM towns t JOIN depots dp ON dp.id = t.depot_id
WHERE t.code = 'KWN'
ON DUPLICATE KEY UPDATE bus_type = VALUES(bus_type);

INSERT INTO routes (division_id, code, name, origin, destination, distance_km, est_duration_min, status)
SELECT d.id, 'R-KWR-KNG', 'Karwar - Kumta - Honnavar', 'Karwar', 'Honnavar', 92.30, 90, 'ACTIVE'
FROM divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE d.code = 'KAD'
ON DUPLICATE KEY UPDATE name = VALUES(name), distance_km = VALUES(distance_km);

DELETE FROM route_stops WHERE route_id = (SELECT id FROM routes WHERE code = 'R-KWR-KNG');

-- Stop order follows the real coastal road network (Karwar -> Kadwad ->
-- Gokarna Road -> Kumta -> Honnavar). Kadwad is ~11 km from Karwar on the
-- Karwar-Ankola road (real village location, NOT the coastal Goa-border area).
INSERT INTO route_stops (route_id, stop_order, stop_name, latitude, longitude, distance_from_start)
SELECT r.id, 1, 'Karwar Bus Stand', 14.813583, 74.129685, 0.0    FROM routes r WHERE r.code = 'R-KWR-KNG'
UNION ALL
SELECT r.id, 2, 'Kadwad',          14.837120, 74.173938, 11.0   FROM routes r WHERE r.code = 'R-KWR-KNG'
UNION ALL
SELECT r.id, 3, 'Gokarna Road',    14.543851, 74.311392, 48.7   FROM routes r WHERE r.code = 'R-KWR-KNG'
UNION ALL
SELECT r.id, 4, 'Kumta',           14.425000, 74.416667, 73.0   FROM routes r WHERE r.code = 'R-KWR-KNG'
UNION ALL
SELECT r.id, 5, 'Honnavar',        14.277470, 74.446510, 92.3   FROM routes r WHERE r.code = 'R-KWR-KNG';

INSERT INTO trips (route_id, bus_id, trip_number, trip_date, scheduled_departure, scheduled_arrival, status, direction)
SELECT r.id, b.id, 'KWR-KNG-001', CURDATE(), CONCAT(CURDATE(), ' 06:00:00'), CONCAT(CURDATE(), ' 07:30:00'), 'SCHEDULED', 'OUTBOUND'
FROM routes r JOIN buses b ON b.registration_no = 'KA-30-F-2025'
WHERE r.code = 'R-KWR-KNG'
ON DUPLICATE KEY UPDATE route_id = VALUES(route_id);

INSERT INTO crew_assignments (trip_id, crew_id, crew_type, status)
SELECT t.id, c.id, 'DRIVER', 'ACTIVE'
FROM trips t JOIN crew c ON c.badge_no = 'CRW-DRV-001'
WHERE t.trip_number = 'KWR-KNG-001' AND t.trip_date = CURDATE()
ON DUPLICATE KEY UPDATE crew_type = VALUES(crew_type);

INSERT INTO crew_assignments (trip_id, crew_id, crew_type, status)
SELECT t.id, c.id, 'CONDUCTOR', 'ACTIVE'
FROM trips t JOIN crew c ON c.badge_no = 'CRW-CND-001'
WHERE t.trip_number = 'KWR-KNG-001' AND t.trip_date = CURDATE()
ON DUPLICATE KEY UPDATE crew_type = VALUES(crew_type);

INSERT INTO bus_assignments (trip_id, bus_id, status)
SELECT t.id, b.id, 'ACTIVE'
FROM trips t JOIN buses b ON b.registration_no = 'KA-30-F-2025'
WHERE t.trip_number = 'KWR-KNG-001' AND t.trip_date = CURDATE()
ON DUPLICATE KEY UPDATE bus_id = VALUES(bus_id);

-- ============================================================
-- ============================================================
-- MASTER ORGANIZATION DATA: divisions, depots, Division Managers,
-- Depot Heads, and the four corporation-level Division Admins.
--
-- Generated content (do not hand-edit). Roles:
--   * DIVISION_ADMIN  = exactly 4 accounts, one per corporation (corporation-wide)
--   * DIVISION_MANAGER = one per division
--   * DEPOT_HEAD      = one per depot
-- DEV-ONLY password for every seeded account: KsrDivision@123
-- Idempotent: safe to re-run; no duplicates are created.
-- ============================================================

-- ------------------------------------------------------------------
-- KSRTC MASTER DATA: divisions + depots + admins
-- ------------------------------------------------------------------
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password) VALUES
('ksrtc.admin@kabus.dev', 'ksrtc.admin@kabus.dev', 'KSRTC Division Admin', '$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO', 1, 0, 1)
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO corporation_admins (user_id, corporation_id)
SELECT u.id, c.id FROM users u, corporations c WHERE u.username='ksrtc.admin@kabus.dev' AND c.code='KSRTC'
ON DUPLICATE KEY UPDATE corporation_id = VALUES(corporation_id);

-- Division: Bangalore Central Division
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'BNGC', 'Bangalore Central Division', 'Bangalore Central Division' FROM corporations c WHERE c.code = 'KSRTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.bngc@ksrtc.dev','admin.bngc@ksrtc.dev','Bangalore Central Division Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='BNGC'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.bngc@ksrtc.dev' AND c.code='KSRTC' AND d.code='BNGC'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'BMF1', 'Bangalore Mofussil-1', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='BNGC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.bmf1@ksrtc.dev','head.bmf1@ksrtc.dev','Bangalore Mofussil-1 Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='BNGC' AND dp.code='BMF1'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.bmf1@ksrtc.dev' AND c.code='KSRTC' AND d.code='BNGC' AND dp.code='BMF1'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'BMF2', 'Bangalore Mofussil-2', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='BNGC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.bmf2@ksrtc.dev','head.bmf2@ksrtc.dev','Bangalore Mofussil-2 Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='BNGC' AND dp.code='BMF2'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.bmf2@ksrtc.dev' AND c.code='KSRTC' AND d.code='BNGC' AND dp.code='BMF2'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'BMF4', 'Bangalore Mofussil-4', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='BNGC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.bmf4@ksrtc.dev','head.bmf4@ksrtc.dev','Bangalore Mofussil-4 Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='BNGC' AND dp.code='BMF4'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.bmf4@ksrtc.dev' AND c.code='KSRTC' AND d.code='BNGC' AND dp.code='BMF4'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'BMF5', 'Bangalore Mofussil-5', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='BNGC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.bmf5@ksrtc.dev','head.bmf5@ksrtc.dev','Bangalore Mofussil-5 Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='BNGC' AND dp.code='BMF5'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.bmf5@ksrtc.dev' AND c.code='KSRTC' AND d.code='BNGC' AND dp.code='BMF5'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'BMF6', 'Bangalore Mofussil-6', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='BNGC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.bmf6@ksrtc.dev','head.bmf6@ksrtc.dev','Bangalore Mofussil-6 Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='BNGC' AND dp.code='BMF6'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.bmf6@ksrtc.dev' AND c.code='KSRTC' AND d.code='BNGC' AND dp.code='BMF6'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'NLM', 'Nelamangala', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='BNGC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.nlm@ksrtc.dev','head.nlm@ksrtc.dev','Nelamangala Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='BNGC' AND dp.code='NLM'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.nlm@ksrtc.dev' AND c.code='KSRTC' AND d.code='BNGC' AND dp.code='NLM'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
-- Division: Bengaluru South Division
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'BLRS', 'Bengaluru South Division', 'Bengaluru South Division' FROM corporations c WHERE c.code = 'KSRTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.blrs@ksrtc.dev','admin.blrs@ksrtc.dev','Bengaluru South Division Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='BLRS'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.blrs@ksrtc.dev' AND c.code='KSRTC' AND d.code='BLRS'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'ANK', 'Anekal', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='BLRS'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.ank@ksrtc.dev','head.ank@ksrtc.dev','Anekal Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='BLRS' AND dp.code='ANK'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.ank@ksrtc.dev' AND c.code='KSRTC' AND d.code='BLRS' AND dp.code='ANK'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'CHN', 'Channapatna', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='BLRS'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.chn@ksrtc.dev','head.chn@ksrtc.dev','Channapatna Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='BLRS' AND dp.code='CHN'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.chn@ksrtc.dev' AND c.code='KSRTC' AND d.code='BLRS' AND dp.code='CHN'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'HRH', 'Harohalli', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='BLRS'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.hrh@ksrtc.dev','head.hrh@ksrtc.dev','Harohalli Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='BLRS' AND dp.code='HRH'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.hrh@ksrtc.dev' AND c.code='KSRTC' AND d.code='BLRS' AND dp.code='HRH'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'KKP', 'Kanakapura', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='BLRS'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.kkp@ksrtc.dev','head.kkp@ksrtc.dev','Kanakapura Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='BLRS' AND dp.code='KKP'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.kkp@ksrtc.dev' AND c.code='KSRTC' AND d.code='BLRS' AND dp.code='KKP'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'MGD', 'Magadi', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='BLRS'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.mgd@ksrtc.dev','head.mgd@ksrtc.dev','Magadi Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='BLRS' AND dp.code='MGD'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.mgd@ksrtc.dev' AND c.code='KSRTC' AND d.code='BLRS' AND dp.code='MGD'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'RMG', 'Ramanagara', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='BLRS'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.rmg@ksrtc.dev','head.rmg@ksrtc.dev','Ramanagara Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='BLRS' AND dp.code='RMG'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.rmg@ksrtc.dev' AND c.code='KSRTC' AND d.code='BLRS' AND dp.code='RMG'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
-- Division: Tumkur Division
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'TUM', 'Tumkur Division', 'Tumkur Division' FROM corporations c WHERE c.code = 'KSRTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.tum@ksrtc.dev','admin.tum@ksrtc.dev','Tumkur Division Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='TUM'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.tum@ksrtc.dev' AND c.code='KSRTC' AND d.code='TUM'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'KNG', 'Kunigal', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='TUM'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.kng@ksrtc.dev','head.kng@ksrtc.dev','Kunigal Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='TUM' AND dp.code='KNG'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.kng@ksrtc.dev' AND c.code='KSRTC' AND d.code='TUM' AND dp.code='KNG'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'TPT', 'Tiptur', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='TUM'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.tpt@ksrtc.dev','head.tpt@ksrtc.dev','Tiptur Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='TUM' AND dp.code='TPT'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.tpt@ksrtc.dev' AND c.code='KSRTC' AND d.code='TUM' AND dp.code='TPT'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'TMK1', 'Tumkur-1', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='TUM'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.tmk1@ksrtc.dev','head.tmk1@ksrtc.dev','Tumkur-1 Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='TUM' AND dp.code='TMK1'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.tmk1@ksrtc.dev' AND c.code='KSRTC' AND d.code='TUM' AND dp.code='TMK1'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'TMK2', 'Tumkur-2', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='TUM'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.tmk2@ksrtc.dev','head.tmk2@ksrtc.dev','Tumkur-2 Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='TUM' AND dp.code='TMK2'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.tmk2@ksrtc.dev' AND c.code='KSRTC' AND d.code='TUM' AND dp.code='TMK2'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'TRV', 'Turuvekere', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='TUM'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.trv@ksrtc.dev','head.trv@ksrtc.dev','Turuvekere Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='TUM' AND dp.code='TRV'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.trv@ksrtc.dev' AND c.code='KSRTC' AND d.code='TUM' AND dp.code='TRV'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
-- Division: Kolar Division
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'KLR', 'Kolar Division', 'Kolar Division' FROM corporations c WHERE c.code = 'KSRTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.klr@ksrtc.dev','admin.klr@ksrtc.dev','Kolar Division Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='KLR'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.klr@ksrtc.dev' AND c.code='KSRTC' AND d.code='KLR'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'KGF', 'KGF', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='KLR'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.kgf@ksrtc.dev','head.kgf@ksrtc.dev','KGF Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='KLR' AND dp.code='KGF'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.kgf@ksrtc.dev' AND c.code='KSRTC' AND d.code='KLR' AND dp.code='KGF'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'KLR', 'Kolar', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='KLR'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.klr@ksrtc.dev','head.klr@ksrtc.dev','Kolar Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='KLR' AND dp.code='KLR'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.klr@ksrtc.dev' AND c.code='KSRTC' AND d.code='KLR' AND dp.code='KLR'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'MLR', 'Malur', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='KLR'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.mlr@ksrtc.dev','head.mlr@ksrtc.dev','Malur Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='KLR' AND dp.code='MLR'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.mlr@ksrtc.dev' AND c.code='KSRTC' AND d.code='KLR' AND dp.code='MLR'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'MBG', 'Mulbagal', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='KLR'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.mbg@ksrtc.dev','head.mbg@ksrtc.dev','Mulbagal Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='KLR' AND dp.code='MBG'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.mbg@ksrtc.dev' AND c.code='KSRTC' AND d.code='KLR' AND dp.code='MBG'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'SRP', 'Srinivasapura', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='KLR'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.srp@ksrtc.dev','head.srp@ksrtc.dev','Srinivasapura Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='KLR' AND dp.code='SRP'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.srp@ksrtc.dev' AND c.code='KSRTC' AND d.code='KLR' AND dp.code='SRP'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
-- Division: Chikballapur Division
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'CKP', 'Chikballapur Division', 'Chikballapur Division' FROM corporations c WHERE c.code = 'KSRTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.ckp@ksrtc.dev','admin.ckp@ksrtc.dev','Chikballapur Division Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='CKP'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.ckp@ksrtc.dev' AND c.code='KSRTC' AND d.code='CKP'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'BGP', 'Bagepalli', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='CKP'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.bgp@ksrtc.dev','head.bgp@ksrtc.dev','Bagepalli Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='CKP' AND dp.code='BGP'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.bgp@ksrtc.dev' AND c.code='KSRTC' AND d.code='CKP' AND dp.code='BGP'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'CBP', 'Chikballapur', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='CKP'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.cbp@ksrtc.dev','head.cbp@ksrtc.dev','Chikballapur Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='CKP' AND dp.code='CBP'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.cbp@ksrtc.dev' AND c.code='KSRTC' AND d.code='CKP' AND dp.code='CBP'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'SDL', 'Shidlaghatta', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='CKP'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.sdl@ksrtc.dev','head.sdl@ksrtc.dev','Shidlaghatta Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='CKP' AND dp.code='SDL'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.sdl@ksrtc.dev' AND c.code='KSRTC' AND d.code='CKP' AND dp.code='SDL'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'CTM', 'Chintamani', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='CKP'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.ctm@ksrtc.dev','head.ctm@ksrtc.dev','Chintamani Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='CKP' AND dp.code='CTM'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.ctm@ksrtc.dev' AND c.code='KSRTC' AND d.code='CKP' AND dp.code='CTM'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'GBR', 'Gauribidanur', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='CKP'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.gbr@ksrtc.dev','head.gbr@ksrtc.dev','Gauribidanur Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='CKP' AND dp.code='GBR'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.gbr@ksrtc.dev' AND c.code='KSRTC' AND d.code='CKP' AND dp.code='GBR'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'DBP', 'Doddaballapur', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='CKP'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.dbp@ksrtc.dev','head.dbp@ksrtc.dev','Doddaballapur Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='CKP' AND dp.code='DBP'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.dbp@ksrtc.dev' AND c.code='KSRTC' AND d.code='CKP' AND dp.code='DBP'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
-- Division: Mysore City Division
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'MYSC', 'Mysore City Division', 'Mysore City Division' FROM corporations c WHERE c.code = 'KSRTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.mysc@ksrtc.dev','admin.mysc@ksrtc.dev','Mysore City Division Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='MYSC'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.mysc@ksrtc.dev' AND c.code='KSRTC' AND d.code='MYSC'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'KVN', 'Kuvempunagar', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='MYSC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.kvn@ksrtc.dev','head.kvn@ksrtc.dev','Kuvempunagar Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='MYSC' AND dp.code='KVN'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.kvn@ksrtc.dev' AND c.code='KSRTC' AND d.code='MYSC' AND dp.code='KVN'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'SGL', 'Sathagalli', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='MYSC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.sgl@ksrtc.dev','head.sgl@ksrtc.dev','Sathagalli Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='MYSC' AND dp.code='SGL'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.sgl@ksrtc.dev' AND c.code='KSRTC' AND d.code='MYSC' AND dp.code='SGL'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'VJN', 'Vijayanagara', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='MYSC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.vjn@ksrtc.dev','head.vjn@ksrtc.dev','Vijayanagara Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='MYSC' AND dp.code='VJN'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.vjn@ksrtc.dev' AND c.code='KSRTC' AND d.code='MYSC' AND dp.code='VJN'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
-- Division: Mysore Rural Division
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'MYSR', 'Mysore Rural Division', 'Mysore Rural Division' FROM corporations c WHERE c.code = 'KSRTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.mysr@ksrtc.dev','admin.mysr@ksrtc.dev','Mysore Rural Division Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='MYSR'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.mysr@ksrtc.dev' AND c.code='KSRTC' AND d.code='MYSR'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'MYM1', 'Mysore Mofussil-1', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='MYSR'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.mym1@ksrtc.dev','head.mym1@ksrtc.dev','Mysore Mofussil-1 Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='MYSR' AND dp.code='MYM1'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.mym1@ksrtc.dev' AND c.code='KSRTC' AND d.code='MYSR' AND dp.code='MYM1'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'MYM2', 'Mysore Mofussil-2', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='MYSR'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.mym2@ksrtc.dev','head.mym2@ksrtc.dev','Mysore Mofussil-2 Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='MYSR' AND dp.code='MYM2'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.mym2@ksrtc.dev' AND c.code='KSRTC' AND d.code='MYSR' AND dp.code='MYM2'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'HDK', 'Heggadadevanakote', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='MYSR'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.hdk@ksrtc.dev','head.hdk@ksrtc.dev','Heggadadevanakote Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='MYSR' AND dp.code='HDK'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.hdk@ksrtc.dev' AND c.code='KSRTC' AND d.code='MYSR' AND dp.code='HDK'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'HNS', 'Hunsur', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='MYSR'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.hns@ksrtc.dev','head.hns@ksrtc.dev','Hunsur Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='MYSR' AND dp.code='HNS'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.hns@ksrtc.dev' AND c.code='KSRTC' AND d.code='MYSR' AND dp.code='HNS'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'KRJ', 'Krishnarajanagara', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='MYSR'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.krj@ksrtc.dev','head.krj@ksrtc.dev','Krishnarajanagara Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='MYSR' AND dp.code='KRJ'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.krj@ksrtc.dev' AND c.code='KSRTC' AND d.code='MYSR' AND dp.code='KRJ'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'PYPT', 'Piriyapatna', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='MYSR'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.pypt@ksrtc.dev','head.pypt@ksrtc.dev','Piriyapatna Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='MYSR' AND dp.code='PYPT'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.pypt@ksrtc.dev' AND c.code='KSRTC' AND d.code='MYSR' AND dp.code='PYPT'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
-- Division: Chamarajanagar Division
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'CMJ', 'Chamarajanagar Division', 'Chamarajanagar Division' FROM corporations c WHERE c.code = 'KSRTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.cmj@ksrtc.dev','admin.cmj@ksrtc.dev','Chamarajanagar Division Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='CMJ'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.cmj@ksrtc.dev' AND c.code='KSRTC' AND d.code='CMJ'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'CMJ', 'Chamarajanagar', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='CMJ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.cmj@ksrtc.dev','head.cmj@ksrtc.dev','Chamarajanagar Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='CMJ' AND dp.code='CMJ'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.cmj@ksrtc.dev' AND c.code='KSRTC' AND d.code='CMJ' AND dp.code='CMJ'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'GLP', 'Gundlupet', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='CMJ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.glp@ksrtc.dev','head.glp@ksrtc.dev','Gundlupet Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='CMJ' AND dp.code='GLP'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.glp@ksrtc.dev' AND c.code='KSRTC' AND d.code='CMJ' AND dp.code='GLP'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'KLG', 'Kollegal', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='CMJ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.klg@ksrtc.dev','head.klg@ksrtc.dev','Kollegal Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='CMJ' AND dp.code='KLG'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.klg@ksrtc.dev' AND c.code='KSRTC' AND d.code='CMJ' AND dp.code='KLG'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
-- Division: Mandya Division
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'MND', 'Mandya Division', 'Mandya Division' FROM corporations c WHERE c.code = 'KSRTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.mnd@ksrtc.dev','admin.mnd@ksrtc.dev','Mandya Division Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='MND'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.mnd@ksrtc.dev' AND c.code='KSRTC' AND d.code='MND'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'KJP', 'Krishnarajapet', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='MND'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.kjp@ksrtc.dev','head.kjp@ksrtc.dev','Krishnarajapet Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='MND' AND dp.code='KJP'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.kjp@ksrtc.dev' AND c.code='KSRTC' AND d.code='MND' AND dp.code='KJP'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'MDR', 'Maddur', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='MND'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.mdr@ksrtc.dev','head.mdr@ksrtc.dev','Maddur Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='MND' AND dp.code='MDR'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.mdr@ksrtc.dev' AND c.code='KSRTC' AND d.code='MND' AND dp.code='MDR'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'MVL', 'Malavalli', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='MND'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.mvl@ksrtc.dev','head.mvl@ksrtc.dev','Malavalli Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='MND' AND dp.code='MVL'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.mvl@ksrtc.dev' AND c.code='KSRTC' AND d.code='MND' AND dp.code='MVL'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'MND', 'Mandya', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='MND'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.mnd@ksrtc.dev','head.mnd@ksrtc.dev','Mandya Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='MND' AND dp.code='MND'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.mnd@ksrtc.dev' AND c.code='KSRTC' AND d.code='MND' AND dp.code='MND'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'NGM', 'Nagamangala', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='MND'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.ngm@ksrtc.dev','head.ngm@ksrtc.dev','Nagamangala Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='MND' AND dp.code='NGM'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.ngm@ksrtc.dev' AND c.code='KSRTC' AND d.code='MND' AND dp.code='NGM'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'PDV', 'Pandavapura', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='MND'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.pdv@ksrtc.dev','head.pdv@ksrtc.dev','Pandavapura Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='MND' AND dp.code='PDV'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.pdv@ksrtc.dev' AND c.code='KSRTC' AND d.code='MND' AND dp.code='PDV'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
-- Division: Hassan Division
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'HSN', 'Hassan Division', 'Hassan Division' FROM corporations c WHERE c.code = 'KSRTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.hsn@ksrtc.dev','admin.hsn@ksrtc.dev','Hassan Division Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='HSN'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.hsn@ksrtc.dev' AND c.code='KSRTC' AND d.code='HSN'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'ARK', 'Arkalgud', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='HSN'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.ark@ksrtc.dev','head.ark@ksrtc.dev','Arkalgud Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='HSN' AND dp.code='ARK'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.ark@ksrtc.dev' AND c.code='KSRTC' AND d.code='HSN' AND dp.code='ARK'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'CYP', 'Channarayapatna', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='HSN'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.cyp@ksrtc.dev','head.cyp@ksrtc.dev','Channarayapatna Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='HSN' AND dp.code='CYP'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.cyp@ksrtc.dev' AND c.code='KSRTC' AND d.code='HSN' AND dp.code='CYP'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'HSN1', 'Hassan-1', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='HSN'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.hsn1@ksrtc.dev','head.hsn1@ksrtc.dev','Hassan-1 Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='HSN' AND dp.code='HSN1'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.hsn1@ksrtc.dev' AND c.code='KSRTC' AND d.code='HSN' AND dp.code='HSN1'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'HSN2', 'Hassan-2', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='HSN'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.hsn2@ksrtc.dev','head.hsn2@ksrtc.dev','Hassan-2 Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='HSN' AND dp.code='HSN2'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.hsn2@ksrtc.dev' AND c.code='KSRTC' AND d.code='HSN' AND dp.code='HSN2'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'HNR', 'Holenarasipur', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='HSN'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.hnr@ksrtc.dev','head.hnr@ksrtc.dev','Holenarasipur Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='HSN' AND dp.code='HNR'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.hnr@ksrtc.dev' AND c.code='KSRTC' AND d.code='HSN' AND dp.code='HNR'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'RMP', 'Ramanathapura', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='HSN'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.rmp@ksrtc.dev','head.rmp@ksrtc.dev','Ramanathapura Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='HSN' AND dp.code='RMP'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.rmp@ksrtc.dev' AND c.code='KSRTC' AND d.code='HSN' AND dp.code='RMP'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
-- Division: Chikkamagaluru Division
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'CKM', 'Chikkamagaluru Division', 'Chikkamagaluru Division' FROM corporations c WHERE c.code = 'KSRTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.ckm@ksrtc.dev','admin.ckm@ksrtc.dev','Chikkamagaluru Division Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='CKM'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.ckm@ksrtc.dev' AND c.code='KSRTC' AND d.code='CKM'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'CKML', 'Chikmagalur', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='CKM'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.ckml@ksrtc.dev','head.ckml@ksrtc.dev','Chikmagalur Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='CKM' AND dp.code='CKML'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.ckml@ksrtc.dev' AND c.code='KSRTC' AND d.code='CKM' AND dp.code='CKML'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'KDR', 'Kadur', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='CKM'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.kdr@ksrtc.dev','head.kdr@ksrtc.dev','Kadur Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='CKM' AND dp.code='KDR'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.kdr@ksrtc.dev' AND c.code='KSRTC' AND d.code='CKM' AND dp.code='KDR'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'MDG', 'Mudigere', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='CKM'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.mdg@ksrtc.dev','head.mdg@ksrtc.dev','Mudigere Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='CKM' AND dp.code='MDG'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.mdg@ksrtc.dev' AND c.code='KSRTC' AND d.code='CKM' AND dp.code='MDG'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'ARS', 'Arsikere', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='CKM'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.ars@ksrtc.dev','head.ars@ksrtc.dev','Arsikere Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='CKM' AND dp.code='ARS'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.ars@ksrtc.dev' AND c.code='KSRTC' AND d.code='CKM' AND dp.code='ARS'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'BLU', 'Belur', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='CKM'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.blu@ksrtc.dev','head.blu@ksrtc.dev','Belur Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='CKM' AND dp.code='BLU'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.blu@ksrtc.dev' AND c.code='KSRTC' AND d.code='CKM' AND dp.code='BLU'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'SKL', 'Sakleshpur', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='CKM'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.skl@ksrtc.dev','head.skl@ksrtc.dev','Sakleshpur Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='CKM' AND dp.code='SKL'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.skl@ksrtc.dev' AND c.code='KSRTC' AND d.code='CKM' AND dp.code='SKL'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
-- Division: Puttur Division
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'PTR', 'Puttur Division', 'Puttur Division' FROM corporations c WHERE c.code = 'KSRTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.ptr@ksrtc.dev','admin.ptr@ksrtc.dev','Puttur Division Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='PTR'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.ptr@ksrtc.dev' AND c.code='KSRTC' AND d.code='PTR'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'MDK', 'Madikeri', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='PTR'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.mdk@ksrtc.dev','head.mdk@ksrtc.dev','Madikeri Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='PTR' AND dp.code='MDK'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.mdk@ksrtc.dev' AND c.code='KSRTC' AND d.code='PTR' AND dp.code='MDK'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'BCR', 'BC Road', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='PTR'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.bcr@ksrtc.dev','head.bcr@ksrtc.dev','BC Road Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='PTR' AND dp.code='BCR'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.bcr@ksrtc.dev' AND c.code='KSRTC' AND d.code='PTR' AND dp.code='BCR'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'PTR', 'Puttur', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='PTR'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.ptr@ksrtc.dev','head.ptr@ksrtc.dev','Puttur Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='PTR' AND dp.code='PTR'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.ptr@ksrtc.dev' AND c.code='KSRTC' AND d.code='PTR' AND dp.code='PTR'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'SLL', 'Sullia', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='PTR'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.sll@ksrtc.dev','head.sll@ksrtc.dev','Sullia Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='PTR' AND dp.code='SLL'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.sll@ksrtc.dev' AND c.code='KSRTC' AND d.code='PTR' AND dp.code='SLL'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'DST', 'Dharmasthala', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='PTR'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.dst@ksrtc.dev','head.dst@ksrtc.dev','Dharmasthala Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='PTR' AND dp.code='DST'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.dst@ksrtc.dev' AND c.code='KSRTC' AND d.code='PTR' AND dp.code='DST'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
-- Division: Mangaluru Division
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'MNG', 'Mangaluru Division', 'Mangaluru Division' FROM corporations c WHERE c.code = 'KSRTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.mng@ksrtc.dev','admin.mng@ksrtc.dev','Mangaluru Division Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='MNG'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.mng@ksrtc.dev' AND c.code='KSRTC' AND d.code='MNG'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'MNG1', 'Mangalore-1', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='MNG'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.mng1@ksrtc.dev','head.mng1@ksrtc.dev','Mangalore-1 Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='MNG' AND dp.code='MNG1'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.mng1@ksrtc.dev' AND c.code='KSRTC' AND d.code='MNG' AND dp.code='MNG1'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'MNG2', 'Mangalore-2', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='MNG'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.mng2@ksrtc.dev','head.mng2@ksrtc.dev','Mangalore-2 Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='MNG' AND dp.code='MNG2'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.mng2@ksrtc.dev' AND c.code='KSRTC' AND d.code='MNG' AND dp.code='MNG2'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'MNG3', 'Mangalore-3', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='MNG'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.mng3@ksrtc.dev','head.mng3@ksrtc.dev','Mangalore-3 Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='MNG' AND dp.code='MNG3'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.mng3@ksrtc.dev' AND c.code='KSRTC' AND d.code='MNG' AND dp.code='MNG3'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'KND', 'Kundapura', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='MNG'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.knd@ksrtc.dev','head.knd@ksrtc.dev','Kundapura Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='MNG' AND dp.code='KND'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.knd@ksrtc.dev' AND c.code='KSRTC' AND d.code='MNG' AND dp.code='KND'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'UDI', 'Udupi', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='MNG'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.udi@ksrtc.dev','head.udi@ksrtc.dev','Udupi Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='MNG' AND dp.code='UDI'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.udi@ksrtc.dev' AND c.code='KSRTC' AND d.code='MNG' AND dp.code='UDI'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
-- Division: Davanagere Division
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'DVG', 'Davanagere Division', 'Davanagere Division' FROM corporations c WHERE c.code = 'KSRTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.dvg@ksrtc.dev','admin.dvg@ksrtc.dev','Davanagere Division Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='DVG'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.dvg@ksrtc.dev' AND c.code='KSRTC' AND d.code='DVG'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'DVG1', 'Davanagere-1', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='DVG'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.dvg1@ksrtc.dev','head.dvg1@ksrtc.dev','Davanagere-1 Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='DVG' AND dp.code='DVG1'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.dvg1@ksrtc.dev' AND c.code='KSRTC' AND d.code='DVG' AND dp.code='DVG1'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'DVG2', 'Davanagere-2', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='DVG'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.dvg2@ksrtc.dev','head.dvg2@ksrtc.dev','Davanagere-2 Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='DVG' AND dp.code='DVG2'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.dvg2@ksrtc.dev' AND c.code='KSRTC' AND d.code='DVG' AND dp.code='DVG2'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'HHR', 'Harihara', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='DVG'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.hhr@ksrtc.dev','head.hhr@ksrtc.dev','Harihara Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='DVG' AND dp.code='HHR'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.hhr@ksrtc.dev' AND c.code='KSRTC' AND d.code='DVG' AND dp.code='HHR'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'CNG', 'Channagiri', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='DVG'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.cng@ksrtc.dev','head.cng@ksrtc.dev','Channagiri Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='DVG' AND dp.code='CNG'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.cng@ksrtc.dev' AND c.code='KSRTC' AND d.code='DVG' AND dp.code='CNG'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
-- Division: Chitradurga Division
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'CHD', 'Chitradurga Division', 'Chitradurga Division' FROM corporations c WHERE c.code = 'KSRTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.chd@ksrtc.dev','admin.chd@ksrtc.dev','Chitradurga Division Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='CHD'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.chd@ksrtc.dev' AND c.code='KSRTC' AND d.code='CHD'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'CHD', 'Chitradurga', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='CHD'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.chd@ksrtc.dev','head.chd@ksrtc.dev','Chitradurga Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='CHD' AND dp.code='CHD'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.chd@ksrtc.dev' AND c.code='KSRTC' AND d.code='CHD' AND dp.code='CHD'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'HRY', 'Hiriyur', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='CHD'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.hry@ksrtc.dev','head.hry@ksrtc.dev','Hiriyur Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='CHD' AND dp.code='HRY'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.hry@ksrtc.dev' AND c.code='KSRTC' AND d.code='CHD' AND dp.code='HRY'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'CHL', 'Challakere', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='CHD'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.chl@ksrtc.dev','head.chl@ksrtc.dev','Challakere Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='CHD' AND dp.code='CHL'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.chl@ksrtc.dev' AND c.code='KSRTC' AND d.code='CHD' AND dp.code='CHL'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'HSD', 'Hosadurga', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='CHD'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.hsd@ksrtc.dev','head.hsd@ksrtc.dev','Hosadurga Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='CHD' AND dp.code='HSD'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.hsd@ksrtc.dev' AND c.code='KSRTC' AND d.code='CHD' AND dp.code='HSD'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
-- Division: Shivamogga Division
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'SHM', 'Shivamogga Division', 'Shivamogga Division' FROM corporations c WHERE c.code = 'KSRTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.shm@ksrtc.dev','admin.shm@ksrtc.dev','Shivamogga Division Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='SHM'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.shm@ksrtc.dev' AND c.code='KSRTC' AND d.code='SHM'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'SGR', 'Sagar', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='SHM'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.sgr@ksrtc.dev','head.sgr@ksrtc.dev','Sagar Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='SHM' AND dp.code='SGR'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.sgr@ksrtc.dev' AND c.code='KSRTC' AND d.code='SHM' AND dp.code='SGR'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'SHM', 'Shimoga', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='SHM'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.shm@ksrtc.dev','head.shm@ksrtc.dev','Shimoga Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='SHM' AND dp.code='SHM'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.shm@ksrtc.dev' AND c.code='KSRTC' AND d.code='SHM' AND dp.code='SHM'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'BDV', 'Bhadravati', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='SHM'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.bdv@ksrtc.dev','head.bdv@ksrtc.dev','Bhadravati Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='SHM' AND dp.code='BDV'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.bdv@ksrtc.dev' AND c.code='KSRTC' AND d.code='SHM' AND dp.code='BDV'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'SKP', 'Shikaripura', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='SHM'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.skp@ksrtc.dev','head.skp@ksrtc.dev','Shikaripura Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='SHM' AND dp.code='SKP'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.skp@ksrtc.dev' AND c.code='KSRTC' AND d.code='SHM' AND dp.code='SKP'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'HNL', 'Honnali', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='SHM'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.hnl@ksrtc.dev','head.hnl@ksrtc.dev','Honnali Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='SHM' AND dp.code='HNL'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.hnl@ksrtc.dev' AND c.code='KSRTC' AND d.code='SHM' AND dp.code='HNL'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
-- Division: Kempegowda Bus Station Division
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'KBS', 'Kempegowda Bus Station Division', 'Kempegowda Bus Station Division' FROM corporations c WHERE c.code = 'KSRTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.kbs@ksrtc.dev','admin.kbs@ksrtc.dev','Kempegowda Bus Station Division Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='KBS'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.kbs@ksrtc.dev' AND c.code='KSRTC' AND d.code='KBS'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'KBSK', 'Kempegowda Bus Station KKRTC', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KSRTC' AND d.code='KBS'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.kbsk@ksrtc.dev','head.kbsk@ksrtc.dev','Kempegowda Bus Station KKRTC Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KSRTC' AND d.code='KBS' AND dp.code='KBSK'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.kbsk@ksrtc.dev' AND c.code='KSRTC' AND d.code='KBS' AND dp.code='KBSK'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);

-- ------------------------------------------------------------------
-- KKRTC MASTER DATA: divisions + depots + admins
-- ------------------------------------------------------------------
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password) VALUES
('kkrtc.admin@kabus.dev', 'kkrtc.admin@kabus.dev', 'KKRTC Division Admin', '$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO', 1, 0, 1)
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO corporation_admins (user_id, corporation_id)
SELECT u.id, c.id FROM users u, corporations c WHERE u.username='kkrtc.admin@kabus.dev' AND c.code='KKRTC'
ON DUPLICATE KEY UPDATE corporation_id = VALUES(corporation_id);

-- Division: Kalaburagi-1 Division
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'KLBA', 'Kalaburagi-1 Division', 'Kalaburagi-1 Division' FROM corporations c WHERE c.code = 'KKRTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.klba@kkrtc.dev','admin.klba@kkrtc.dev','Kalaburagi-1 Division Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='KLBA'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.klba@kkrtc.dev' AND c.code='KKRTC' AND d.code='KLBA'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'CHC', 'Chincholi', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='KLBA'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.chc@kkrtc.dev','head.chc@kkrtc.dev','Chincholi Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='KLBA' AND dp.code='CHC'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.chc@kkrtc.dev' AND c.code='KKRTC' AND d.code='KLBA' AND dp.code='CHC'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'CTP', 'Chittapur', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='KLBA'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.ctp@kkrtc.dev','head.ctp@kkrtc.dev','Chittapur Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='KLBA' AND dp.code='CTP'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.ctp@kkrtc.dev' AND c.code='KKRTC' AND d.code='KLBA' AND dp.code='CTP'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'GBG1', 'Gulbarga-1', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='KLBA'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.gbg1@kkrtc.dev','head.gbg1@kkrtc.dev','Gulbarga-1 Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='KLBA' AND dp.code='GBG1'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.gbg1@kkrtc.dev' AND c.code='KKRTC' AND d.code='KLBA' AND dp.code='GBG1'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'GBG4', 'Gulbarga-4', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='KLBA'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.gbg4@kkrtc.dev','head.gbg4@kkrtc.dev','Gulbarga-4 Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='KLBA' AND dp.code='GBG4'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.gbg4@kkrtc.dev' AND c.code='KKRTC' AND d.code='KLBA' AND dp.code='GBG4'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'KAG', 'Kalagi', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='KLBA'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.kag@kkrtc.dev','head.kag@kkrtc.dev','Kalagi Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='KLBA' AND dp.code='KAG'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.kag@kkrtc.dev' AND c.code='KKRTC' AND d.code='KLBA' AND dp.code='KAG'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'SDM', 'Sedam', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='KLBA'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.sdm@kkrtc.dev','head.sdm@kkrtc.dev','Sedam Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='KLBA' AND dp.code='SDM'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.sdm@kkrtc.dev' AND c.code='KKRTC' AND d.code='KLBA' AND dp.code='SDM'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
-- Division: Kalaburagi-2 Division
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'KLBB', 'Kalaburagi-2 Division', 'Kalaburagi-2 Division' FROM corporations c WHERE c.code = 'KKRTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.klbb@kkrtc.dev','admin.klbb@kkrtc.dev','Kalaburagi-2 Division Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='KLBB'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.klbb@kkrtc.dev' AND c.code='KKRTC' AND d.code='KLBB'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'GBG2', 'Gulbarga-2', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='KLBB'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.gbg2@kkrtc.dev','head.gbg2@kkrtc.dev','Gulbarga-2 Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='KLBB' AND dp.code='GBG2'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.gbg2@kkrtc.dev' AND c.code='KKRTC' AND d.code='KLBB' AND dp.code='GBG2'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'GBG3', 'Gulbarga-3', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='KLBB'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.gbg3@kkrtc.dev','head.gbg3@kkrtc.dev','Gulbarga-3 Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='KLBB' AND dp.code='GBG3'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.gbg3@kkrtc.dev' AND c.code='KKRTC' AND d.code='KLBB' AND dp.code='GBG3'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'JWR', 'Jewargi', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='KLBB'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.jwr@kkrtc.dev','head.jwr@kkrtc.dev','Jewargi Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='KLBB' AND dp.code='JWR'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.jwr@kkrtc.dev' AND c.code='KKRTC' AND d.code='KLBB' AND dp.code='JWR'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'AFZ', 'Afzalpur', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='KLBB'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.afz@kkrtc.dev','head.afz@kkrtc.dev','Afzalpur Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='KLBB' AND dp.code='AFZ'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.afz@kkrtc.dev' AND c.code='KKRTC' AND d.code='KLBB' AND dp.code='AFZ'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
-- Division: Yadgir Division
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'YDG', 'Yadgir Division', 'Yadgir Division' FROM corporations c WHERE c.code = 'KKRTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.ydg@kkrtc.dev','admin.ydg@kkrtc.dev','Yadgir Division Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='YDG'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.ydg@kkrtc.dev' AND c.code='KKRTC' AND d.code='YDG'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'GMK', 'Gurmitkal', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='YDG'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.gmk@kkrtc.dev','head.gmk@kkrtc.dev','Gurmitkal Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='YDG' AND dp.code='GMK'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.gmk@kkrtc.dev' AND c.code='KKRTC' AND d.code='YDG' AND dp.code='GMK'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'SHP', 'Shahapur', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='YDG'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.shp@kkrtc.dev','head.shp@kkrtc.dev','Shahapur Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='YDG' AND dp.code='SHP'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.shp@kkrtc.dev' AND c.code='KKRTC' AND d.code='YDG' AND dp.code='SHP'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'SRP', 'Shorapur', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='YDG'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.srp@kkrtc.dev','head.srp@kkrtc.dev','Shorapur Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='YDG' AND dp.code='SRP'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.srp@kkrtc.dev' AND c.code='KKRTC' AND d.code='YDG' AND dp.code='SRP'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'YDG', 'Yadgir', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='YDG'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.ydg@kkrtc.dev','head.ydg@kkrtc.dev','Yadgir Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='YDG' AND dp.code='YDG'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.ydg@kkrtc.dev' AND c.code='KKRTC' AND d.code='YDG' AND dp.code='YDG'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
-- Division: Bidar Division
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'BDR', 'Bidar Division', 'Bidar Division' FROM corporations c WHERE c.code = 'KKRTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.bdr@kkrtc.dev','admin.bdr@kkrtc.dev','Bidar Division Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='BDR'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.bdr@kkrtc.dev' AND c.code='KKRTC' AND d.code='BDR'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'ARD', 'Aurad', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='BDR'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.ard@kkrtc.dev','head.ard@kkrtc.dev','Aurad Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='BDR' AND dp.code='ARD'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.ard@kkrtc.dev' AND c.code='KKRTC' AND d.code='BDR' AND dp.code='ARD'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'BSK', 'Basavakalyan', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='BDR'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.bsk@kkrtc.dev','head.bsk@kkrtc.dev','Basavakalyan Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='BDR' AND dp.code='BSK'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.bsk@kkrtc.dev' AND c.code='KKRTC' AND d.code='BDR' AND dp.code='BSK'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'BHK', 'Bhalki', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='BDR'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.bhk@kkrtc.dev','head.bhk@kkrtc.dev','Bhalki Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='BDR' AND dp.code='BHK'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.bhk@kkrtc.dev' AND c.code='KKRTC' AND d.code='BDR' AND dp.code='BHK'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'BDR1', 'Bidar-1', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='BDR'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.bdr1@kkrtc.dev','head.bdr1@kkrtc.dev','Bidar-1 Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='BDR' AND dp.code='BDR1'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.bdr1@kkrtc.dev' AND c.code='KKRTC' AND d.code='BDR' AND dp.code='BDR1'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'BDR2', 'Bidar-2', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='BDR'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.bdr2@kkrtc.dev','head.bdr2@kkrtc.dev','Bidar-2 Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='BDR' AND dp.code='BDR2'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.bdr2@kkrtc.dev' AND c.code='KKRTC' AND d.code='BDR' AND dp.code='BDR2'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'HMB', 'Humnabad', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='BDR'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.hmb@kkrtc.dev','head.hmb@kkrtc.dev','Humnabad Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='BDR' AND dp.code='HMB'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.hmb@kkrtc.dev' AND c.code='KKRTC' AND d.code='BDR' AND dp.code='HMB'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
-- Division: Raichur Division
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'RCU', 'Raichur Division', 'Raichur Division' FROM corporations c WHERE c.code = 'KKRTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.rcu@kkrtc.dev','admin.rcu@kkrtc.dev','Raichur Division Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='RCU'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.rcu@kkrtc.dev' AND c.code='KKRTC' AND d.code='RCU'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'DVR', 'Devadurga', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='RCU'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.dvr@kkrtc.dev','head.dvr@kkrtc.dev','Devadurga Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='RCU' AND dp.code='DVR'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.dvr@kkrtc.dev' AND c.code='KKRTC' AND d.code='RCU' AND dp.code='DVR'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'LGS', 'Lingasugur', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='RCU'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.lgs@kkrtc.dev','head.lgs@kkrtc.dev','Lingasugur Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='RCU' AND dp.code='LGS'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.lgs@kkrtc.dev' AND c.code='KKRTC' AND d.code='RCU' AND dp.code='LGS'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'MNV', 'Manvi', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='RCU'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.mnv@kkrtc.dev','head.mnv@kkrtc.dev','Manvi Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='RCU' AND dp.code='MNV'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.mnv@kkrtc.dev' AND c.code='KKRTC' AND d.code='RCU' AND dp.code='MNV'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'MSK', 'Maski', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='RCU'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.msk@kkrtc.dev','head.msk@kkrtc.dev','Maski Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='RCU' AND dp.code='MSK'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.msk@kkrtc.dev' AND c.code='KKRTC' AND d.code='RCU' AND dp.code='MSK'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'RCU1', 'Raichur-1', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='RCU'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.rcu1@kkrtc.dev','head.rcu1@kkrtc.dev','Raichur-1 Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='RCU' AND dp.code='RCU1'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.rcu1@kkrtc.dev' AND c.code='KKRTC' AND d.code='RCU' AND dp.code='RCU1'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'RCU2', 'Raichur-2', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='RCU'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.rcu2@kkrtc.dev','head.rcu2@kkrtc.dev','Raichur-2 Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='RCU' AND dp.code='RCU2'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.rcu2@kkrtc.dev' AND c.code='KKRTC' AND d.code='RCU' AND dp.code='RCU2'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'RCU3', 'Raichur-3', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='RCU'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.rcu3@kkrtc.dev','head.rcu3@kkrtc.dev','Raichur-3 Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='RCU' AND dp.code='RCU3'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.rcu3@kkrtc.dev' AND c.code='KKRTC' AND d.code='RCU' AND dp.code='RCU3'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'SNR', 'Sindhanur', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='RCU'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.snr@kkrtc.dev','head.snr@kkrtc.dev','Sindhanur Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='RCU' AND dp.code='SNR'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.snr@kkrtc.dev' AND c.code='KKRTC' AND d.code='RCU' AND dp.code='SNR'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
-- Division: Koppal Division
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'KPL', 'Koppal Division', 'Koppal Division' FROM corporations c WHERE c.code = 'KKRTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.kpl@kkrtc.dev','admin.kpl@kkrtc.dev','Koppal Division Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='KPL'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.kpl@kkrtc.dev' AND c.code='KKRTC' AND d.code='KPL'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'GNG', 'Gangavati', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='KPL'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.gng@kkrtc.dev','head.gng@kkrtc.dev','Gangavati Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='KPL' AND dp.code='GNG'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.gng@kkrtc.dev' AND c.code='KKRTC' AND d.code='KPL' AND dp.code='GNG'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'KPL', 'Koppal', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='KPL'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.kpl@kkrtc.dev','head.kpl@kkrtc.dev','Koppal Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='KPL' AND dp.code='KPL'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.kpl@kkrtc.dev' AND c.code='KKRTC' AND d.code='KPL' AND dp.code='KPL'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'KKN', 'Kuknur', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='KPL'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.kkn@kkrtc.dev','head.kkn@kkrtc.dev','Kuknur Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='KPL' AND dp.code='KKN'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.kkn@kkrtc.dev' AND c.code='KKRTC' AND d.code='KPL' AND dp.code='KKN'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'KSG', 'Kustagi', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='KPL'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.ksg@kkrtc.dev','head.ksg@kkrtc.dev','Kustagi Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='KPL' AND dp.code='KSG'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.ksg@kkrtc.dev' AND c.code='KKRTC' AND d.code='KPL' AND dp.code='KSG'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'YLB', 'Yelburga', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='KPL'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.ylb@kkrtc.dev','head.ylb@kkrtc.dev','Yelburga Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='KPL' AND dp.code='YLB'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.ylb@kkrtc.dev' AND c.code='KKRTC' AND d.code='KPL' AND dp.code='YLB'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
-- Division: Ballari Division
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'BLL', 'Ballari Division', 'Ballari Division' FROM corporations c WHERE c.code = 'KKRTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.bll@kkrtc.dev','admin.bll@kkrtc.dev','Ballari Division Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='BLL'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.bll@kkrtc.dev' AND c.code='KKRTC' AND d.code='BLL'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'BLL1', 'Ballari-1', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='BLL'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.bll1@kkrtc.dev','head.bll1@kkrtc.dev','Ballari-1 Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='BLL' AND dp.code='BLL1'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.bll1@kkrtc.dev' AND c.code='KKRTC' AND d.code='BLL' AND dp.code='BLL1'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'BLL2', 'Ballari-2', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='BLL'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.bll2@kkrtc.dev','head.bll2@kkrtc.dev','Ballari-2 Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='BLL' AND dp.code='BLL2'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.bll2@kkrtc.dev' AND c.code='KKRTC' AND d.code='BLL' AND dp.code='BLL2'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'BLL3', 'Ballari-3', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='BLL'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.bll3@kkrtc.dev','head.bll3@kkrtc.dev','Ballari-3 Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='BLL' AND dp.code='BLL3'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.bll3@kkrtc.dev' AND c.code='KKRTC' AND d.code='BLL' AND dp.code='BLL3'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'KGD', 'Kurugodu', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='BLL'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.kgd@kkrtc.dev','head.kgd@kkrtc.dev','Kurugodu Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='BLL' AND dp.code='KGD'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.kgd@kkrtc.dev' AND c.code='KKRTC' AND d.code='BLL' AND dp.code='KGD'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'SRG', 'Siruguppa', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='BLL'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.srg@kkrtc.dev','head.srg@kkrtc.dev','Siruguppa Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='BLL' AND dp.code='SRG'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.srg@kkrtc.dev' AND c.code='KKRTC' AND d.code='BLL' AND dp.code='SRG'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'SND', 'Sandur', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='BLL'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.snd@kkrtc.dev','head.snd@kkrtc.dev','Sandur Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='BLL' AND dp.code='SND'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.snd@kkrtc.dev' AND c.code='KKRTC' AND d.code='BLL' AND dp.code='SND'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
-- Division: Vijayapura Division
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'VJP', 'Vijayapura Division', 'Vijayapura Division' FROM corporations c WHERE c.code = 'KKRTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.vjp@kkrtc.dev','admin.vjp@kkrtc.dev','Vijayapura Division Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='VJP'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.vjp@kkrtc.dev' AND c.code='KKRTC' AND d.code='VJP'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'VJP1', 'Vijayapura-1', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='VJP'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.vjp1@kkrtc.dev','head.vjp1@kkrtc.dev','Vijayapura-1 Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='VJP' AND dp.code='VJP1'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.vjp1@kkrtc.dev' AND c.code='KKRTC' AND d.code='VJP' AND dp.code='VJP1'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'VJP2', 'Vijayapura-2', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='VJP'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.vjp2@kkrtc.dev','head.vjp2@kkrtc.dev','Vijayapura-2 Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='VJP' AND dp.code='VJP2'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.vjp2@kkrtc.dev' AND c.code='KKRTC' AND d.code='VJP' AND dp.code='VJP2'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'VJP3', 'Vijayapura-3', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='VJP'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.vjp3@kkrtc.dev','head.vjp3@kkrtc.dev','Vijayapura-3 Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='VJP' AND dp.code='VJP3'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.vjp3@kkrtc.dev' AND c.code='KKRTC' AND d.code='VJP' AND dp.code='VJP3'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'IND', 'Indi', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='VJP'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.ind@kkrtc.dev','head.ind@kkrtc.dev','Indi Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='VJP' AND dp.code='IND'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.ind@kkrtc.dev' AND c.code='KKRTC' AND d.code='VJP' AND dp.code='IND'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'MDB', 'Muddebihal', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='VJP'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.mdb@kkrtc.dev','head.mdb@kkrtc.dev','Muddebihal Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='VJP' AND dp.code='MDB'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.mdb@kkrtc.dev' AND c.code='KKRTC' AND d.code='VJP' AND dp.code='MDB'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'SGI', 'Sindgi', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='VJP'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.sgi@kkrtc.dev','head.sgi@kkrtc.dev','Sindgi Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='VJP' AND dp.code='SGI'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.sgi@kkrtc.dev' AND c.code='KKRTC' AND d.code='VJP' AND dp.code='SGI'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'TKT', 'Talikoti', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='VJP'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.tkt@kkrtc.dev','head.tkt@kkrtc.dev','Talikoti Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='VJP' AND dp.code='TKT'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.tkt@kkrtc.dev' AND c.code='KKRTC' AND d.code='VJP' AND dp.code='TKT'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'BNB', 'Basavana Bagewadi', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='VJP'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.bnb@kkrtc.dev','head.bnb@kkrtc.dev','Basavana Bagewadi Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='VJP' AND dp.code='BNB'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.bnb@kkrtc.dev' AND c.code='KKRTC' AND d.code='VJP' AND dp.code='BNB'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
-- Division: Hosapete Division
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'HSP', 'Hosapete Division', 'Hosapete Division' FROM corporations c WHERE c.code = 'KKRTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.hsp@kkrtc.dev','admin.hsp@kkrtc.dev','Hosapete Division Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='HSP'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.hsp@kkrtc.dev' AND c.code='KKRTC' AND d.code='HSP'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'HRP', 'Harapanahalli', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='HSP'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.hrp@kkrtc.dev','head.hrp@kkrtc.dev','Harapanahalli Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='HSP' AND dp.code='HRP'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.hrp@kkrtc.dev' AND c.code='KKRTC' AND d.code='HSP' AND dp.code='HRP'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'HGB', 'Hagari Bommanahalli', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='HSP'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.hgb@kkrtc.dev','head.hgb@kkrtc.dev','Hagari Bommanahalli Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='HSP' AND dp.code='HGB'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.hgb@kkrtc.dev' AND c.code='KKRTC' AND d.code='HSP' AND dp.code='HGB'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'HSP', 'Hosapete', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='HSP'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.hsp@kkrtc.dev','head.hsp@kkrtc.dev','Hosapete Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='HSP' AND dp.code='HSP'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.hsp@kkrtc.dev' AND c.code='KKRTC' AND d.code='HSP' AND dp.code='HSP'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'HVD', 'Huvina Hadagali', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='HSP'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.hvd@kkrtc.dev','head.hvd@kkrtc.dev','Huvina Hadagali Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='HSP' AND dp.code='HVD'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.hvd@kkrtc.dev' AND c.code='KKRTC' AND d.code='HSP' AND dp.code='HVD'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'KDG', 'Kudligi', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='KKRTC' AND d.code='HSP'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.kdg@kkrtc.dev','head.kdg@kkrtc.dev','Kudligi Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='KKRTC' AND d.code='HSP' AND dp.code='KDG'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.kdg@kkrtc.dev' AND c.code='KKRTC' AND d.code='HSP' AND dp.code='KDG'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);

-- ------------------------------------------------------------------
-- NWKRTC MASTER DATA: divisions + depots + admins
-- ------------------------------------------------------------------
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password) VALUES
('nwkrtc.admin@kabus.dev', 'nwkrtc.admin@kabus.dev', 'NWKRTC Division Admin', '$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO', 1, 0, 1)
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO corporation_admins (user_id, corporation_id)
SELECT u.id, c.id FROM users u, corporations c WHERE u.username='nwkrtc.admin@kabus.dev' AND c.code='NWKRTC'
ON DUPLICATE KEY UPDATE corporation_id = VALUES(corporation_id);

-- Division: Belagavi Division
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'BLG', 'Belagavi Division', 'Belagavi Division' FROM corporations c WHERE c.code = 'NWKRTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.blg@nwkrtc.dev','admin.blg@nwkrtc.dev','Belagavi Division Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='BLG'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.blg@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='BLG'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'BLH', 'Bailhongal', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='BLG'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.blh@nwkrtc.dev','head.blh@nwkrtc.dev','Bailhongal Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='BLG' AND dp.code='BLH'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.blh@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='BLG' AND dp.code='BLH'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'BLG1', 'Belagavi-1', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='BLG'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.blg1@nwkrtc.dev','head.blg1@nwkrtc.dev','Belagavi-1 Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='BLG' AND dp.code='BLG1'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.blg1@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='BLG' AND dp.code='BLG1'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'BLG2', 'Belagavi-2', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='BLG'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.blg2@nwkrtc.dev','head.blg2@nwkrtc.dev','Belagavi-2 Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='BLG' AND dp.code='BLG2'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.blg2@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='BLG' AND dp.code='BLG2'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'BLG3', 'Belagavi-3', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='BLG'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.blg3@nwkrtc.dev','head.blg3@nwkrtc.dev','Belagavi-3 Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='BLG' AND dp.code='BLG3'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.blg3@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='BLG' AND dp.code='BLG3'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'BLG4', 'Belagavi-4', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='BLG'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.blg4@nwkrtc.dev','head.blg4@nwkrtc.dev','Belagavi-4 Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='BLG' AND dp.code='BLG4'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.blg4@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='BLG' AND dp.code='BLG4'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'KNP', 'Khanapur', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='BLG'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.knp@nwkrtc.dev','head.knp@nwkrtc.dev','Khanapur Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='BLG' AND dp.code='KNP'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.knp@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='BLG' AND dp.code='KNP'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'RMD', 'Ramadurga', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='BLG'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.rmd@nwkrtc.dev','head.rmd@nwkrtc.dev','Ramadurga Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='BLG' AND dp.code='RMD'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.rmd@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='BLG' AND dp.code='RMD'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
-- Division: Hubballi Division
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'HBL', 'Hubballi Division', 'Hubballi Division' FROM corporations c WHERE c.code = 'NWKRTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.hbl@nwkrtc.dev','admin.hbl@nwkrtc.dev','Hubballi Division Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='HBL'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.hbl@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='HBL'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'HBM1', 'Hubli Mofussil-1', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='HBL'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.hbm1@nwkrtc.dev','head.hbm1@nwkrtc.dev','Hubli Mofussil-1 Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='HBL' AND dp.code='HBM1'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.hbm1@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='HBL' AND dp.code='HBM1'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'HBM2', 'Hubli Mofussil-2', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='HBL'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.hbm2@nwkrtc.dev','head.hbm2@nwkrtc.dev','Hubli Mofussil-2 Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='HBL' AND dp.code='HBM2'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.hbm2@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='HBL' AND dp.code='HBM2'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'HBM3', 'Hubli Mofussil-3', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='HBL'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.hbm3@nwkrtc.dev','head.hbm3@nwkrtc.dev','Hubli Mofussil-3 Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='HBL' AND dp.code='HBM3'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.hbm3@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='HBL' AND dp.code='HBM3'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'NVL', 'Navalgund', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='HBL'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.nvl@nwkrtc.dev','head.nvl@nwkrtc.dev','Navalgund Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='HBL' AND dp.code='NVL'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.nvl@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='HBL' AND dp.code='NVL'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'KLG', 'Kalaghatagi', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='HBL'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.klg@nwkrtc.dev','head.klg@nwkrtc.dev','Kalaghatagi Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='HBL' AND dp.code='KLG'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.klg@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='HBL' AND dp.code='KLG'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
-- Division: Dharwad Division
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'DWD', 'Dharwad Division', 'Dharwad Division' FROM corporations c WHERE c.code = 'NWKRTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.dwd@nwkrtc.dev','admin.dwd@nwkrtc.dev','Dharwad Division Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='DWD'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.dwd@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='DWD'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'SVT', 'Savadatti', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='DWD'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.svt@nwkrtc.dev','head.svt@nwkrtc.dev','Savadatti Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='DWD' AND dp.code='SVT'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.svt@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='DWD' AND dp.code='SVT'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'DDL', 'Dandeli', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='DWD'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.ddl@nwkrtc.dev','head.ddl@nwkrtc.dev','Dandeli Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='DWD' AND dp.code='DDL'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.ddl@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='DWD' AND dp.code='DDL'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'HYL', 'Haliyal', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='DWD'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.hyl@nwkrtc.dev','head.hyl@nwkrtc.dev','Haliyal Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='DWD' AND dp.code='HYL'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.hyl@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='DWD' AND dp.code='HYL'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'DWDM', 'Dharwad Mofussil', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='DWD'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.dwdm@nwkrtc.dev','head.dwdm@nwkrtc.dev','Dharwad Mofussil Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='DWD' AND dp.code='DWDM'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.dwdm@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='DWD' AND dp.code='DWDM'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
-- Division: North Kanara Division
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'NCK', 'North Kanara Division', 'North Kanara Division' FROM corporations c WHERE c.code = 'NWKRTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.nck@nwkrtc.dev','admin.nck@nwkrtc.dev','North Kanara Division Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='NCK'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.nck@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='NCK'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'SRS', 'Sirsi', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='NCK'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.srs@nwkrtc.dev','head.srs@nwkrtc.dev','Sirsi Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='NCK' AND dp.code='SRS'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.srs@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='NCK' AND dp.code='SRS'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'KRW', 'Karwar', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='NCK'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.krw@nwkrtc.dev','head.krw@nwkrtc.dev','Karwar Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='NCK' AND dp.code='KRW'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.krw@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='NCK' AND dp.code='KRW'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'ANK', 'Ankola', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='NCK'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.ank@nwkrtc.dev','head.ank@nwkrtc.dev','Ankola Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='NCK' AND dp.code='ANK'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.ank@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='NCK' AND dp.code='ANK'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'BTK', 'Bhatkal', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='NCK'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.btk@nwkrtc.dev','head.btk@nwkrtc.dev','Bhatkal Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='NCK' AND dp.code='BTK'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.btk@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='NCK' AND dp.code='BTK'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'KMT', 'Kumta', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='NCK'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.kmt@nwkrtc.dev','head.kmt@nwkrtc.dev','Kumta Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='NCK' AND dp.code='KMT'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.kmt@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='NCK' AND dp.code='KMT'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'YLP', 'Yellapura', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='NCK'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.ylp@nwkrtc.dev','head.ylp@nwkrtc.dev','Yellapura Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='NCK' AND dp.code='YLP'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.ylp@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='NCK' AND dp.code='YLP'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
-- Division: Bagalkot Division
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'BGK', 'Bagalkot Division', 'Bagalkot Division' FROM corporations c WHERE c.code = 'NWKRTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.bgk@nwkrtc.dev','admin.bgk@nwkrtc.dev','Bagalkot Division Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='BGK'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.bgk@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='BGK'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'BDM', 'Badami', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='BGK'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.bdm@nwkrtc.dev','head.bdm@nwkrtc.dev','Badami Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='BGK' AND dp.code='BDM'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.bdm@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='BGK' AND dp.code='BDM'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'BGK', 'Bagalkot', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='BGK'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.bgk@nwkrtc.dev','head.bgk@nwkrtc.dev','Bagalkot Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='BGK' AND dp.code='BGK'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.bgk@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='BGK' AND dp.code='BGK'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'BLI', 'Bilagi', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='BGK'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.bli@nwkrtc.dev','head.bli@nwkrtc.dev','Bilagi Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='BGK' AND dp.code='BLI'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.bli@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='BGK' AND dp.code='BLI'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'GLD', 'Guledgudda', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='BGK'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.gld@nwkrtc.dev','head.gld@nwkrtc.dev','Guledgudda Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='BGK' AND dp.code='GLD'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.gld@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='BGK' AND dp.code='GLD'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'HND', 'Hungund', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='BGK'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.hnd@nwkrtc.dev','head.hnd@nwkrtc.dev','Hungund Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='BGK' AND dp.code='HND'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.hnd@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='BGK' AND dp.code='HND'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'ILK', 'Ilkal', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='BGK'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.ilk@nwkrtc.dev','head.ilk@nwkrtc.dev','Ilkal Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='BGK' AND dp.code='ILK'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.ilk@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='BGK' AND dp.code='ILK'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'JMK', 'Jamakhandi', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='BGK'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.jmk@nwkrtc.dev','head.jmk@nwkrtc.dev','Jamakhandi Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='BGK' AND dp.code='JMK'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.jmk@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='BGK' AND dp.code='JMK'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'MDH', 'Mudhol', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='BGK'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.mdh@nwkrtc.dev','head.mdh@nwkrtc.dev','Mudhol Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='BGK' AND dp.code='MDH'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.mdh@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='BGK' AND dp.code='MDH'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
-- Division: Gadag Division
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'GAD', 'Gadag Division', 'Gadag Division' FROM corporations c WHERE c.code = 'NWKRTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.gad@nwkrtc.dev','admin.gad@nwkrtc.dev','Gadag Division Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='GAD'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.gad@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='GAD'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'BTG', 'Betageri', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='GAD'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.btg@nwkrtc.dev','head.btg@nwkrtc.dev','Betageri Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='GAD' AND dp.code='BTG'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.btg@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='GAD' AND dp.code='BTG'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'GAD', 'Gadag', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='GAD'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.gad@nwkrtc.dev','head.gad@nwkrtc.dev','Gadag Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='GAD' AND dp.code='GAD'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.gad@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='GAD' AND dp.code='GAD'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'GJG', 'Gajendragad', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='GAD'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.gjg@nwkrtc.dev','head.gjg@nwkrtc.dev','Gajendragad Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='GAD' AND dp.code='GJG'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.gjg@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='GAD' AND dp.code='GJG'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'LXM', 'Laxmeshwar', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='GAD'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.lxm@nwkrtc.dev','head.lxm@nwkrtc.dev','Laxmeshwar Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='GAD' AND dp.code='LXM'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.lxm@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='GAD' AND dp.code='LXM'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'MGR', 'Mundargi', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='GAD'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.mgr@nwkrtc.dev','head.mgr@nwkrtc.dev','Mundargi Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='GAD' AND dp.code='MGR'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.mgr@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='GAD' AND dp.code='MGR'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'NRG', 'Naragund', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='GAD'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.nrg@nwkrtc.dev','head.nrg@nwkrtc.dev','Naragund Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='GAD' AND dp.code='NRG'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.nrg@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='GAD' AND dp.code='NRG'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'RON', 'Ron', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='GAD'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.ron@nwkrtc.dev','head.ron@nwkrtc.dev','Ron Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='GAD' AND dp.code='RON'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.ron@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='GAD' AND dp.code='RON'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'STT', 'Shirahatti', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='GAD'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.stt@nwkrtc.dev','head.stt@nwkrtc.dev','Shirahatti Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='GAD' AND dp.code='STT'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.stt@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='GAD' AND dp.code='STT'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
-- Division: Chikkodi Division
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'CKD', 'Chikkodi Division', 'Chikkodi Division' FROM corporations c WHERE c.code = 'NWKRTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.ckd@nwkrtc.dev','admin.ckd@nwkrtc.dev','Chikkodi Division Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='CKD'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.ckd@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='CKD'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'ATN', 'Athani', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='CKD'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.atn@nwkrtc.dev','head.atn@nwkrtc.dev','Athani Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='CKD' AND dp.code='ATN'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.atn@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='CKD' AND dp.code='ATN'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'CKD', 'Chikkodi', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='CKD'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.ckd@nwkrtc.dev','head.ckd@nwkrtc.dev','Chikkodi Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='CKD' AND dp.code='CKD'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.ckd@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='CKD' AND dp.code='CKD'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'GOK', 'Gokak', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='CKD'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.gok@nwkrtc.dev','head.gok@nwkrtc.dev','Gokak Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='CKD' AND dp.code='GOK'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.gok@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='CKD' AND dp.code='GOK'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'HKR', 'Hukkeri', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='CKD'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.hkr@nwkrtc.dev','head.hkr@nwkrtc.dev','Hukkeri Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='CKD' AND dp.code='HKR'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.hkr@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='CKD' AND dp.code='HKR'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'NPP', 'Nippani', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='CKD'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.npp@nwkrtc.dev','head.npp@nwkrtc.dev','Nippani Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='CKD' AND dp.code='NPP'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.npp@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='CKD' AND dp.code='NPP'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'RBG', 'Raibag', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='CKD'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.rbg@nwkrtc.dev','head.rbg@nwkrtc.dev','Raibag Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='CKD' AND dp.code='RBG'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.rbg@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='CKD' AND dp.code='RBG'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'SKS', 'Sankeshwar', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='CKD'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.sks@nwkrtc.dev','head.sks@nwkrtc.dev','Sankeshwar Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='CKD' AND dp.code='SKS'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.sks@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='CKD' AND dp.code='SKS'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
-- Division: Haveri Division
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'HVR', 'Haveri Division', 'Haveri Division' FROM corporations c WHERE c.code = 'NWKRTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.hvr@nwkrtc.dev','admin.hvr@nwkrtc.dev','Haveri Division Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='HVR'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.hvr@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='HVR'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'BYD', 'Byadgi', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='HVR'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.byd@nwkrtc.dev','head.byd@nwkrtc.dev','Byadgi Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='HVR' AND dp.code='BYD'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.byd@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='HVR' AND dp.code='BYD'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'HNG', 'Hanagal', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='HVR'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.hng@nwkrtc.dev','head.hng@nwkrtc.dev','Hanagal Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='HVR' AND dp.code='HNG'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.hng@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='HVR' AND dp.code='HNG'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'HVR', 'Haveri', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='HVR'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.hvr@nwkrtc.dev','head.hvr@nwkrtc.dev','Haveri Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='HVR' AND dp.code='HVR'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.hvr@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='HVR' AND dp.code='HVR'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'HRK', 'Hirekerur', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='HVR'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.hrk@nwkrtc.dev','head.hrk@nwkrtc.dev','Hirekerur Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='HVR' AND dp.code='HRK'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.hrk@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='HVR' AND dp.code='HRK'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'RNB', 'Ranebennur', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='HVR'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.rnb@nwkrtc.dev','head.rnb@nwkrtc.dev','Ranebennur Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='HVR' AND dp.code='RNB'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.rnb@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='HVR' AND dp.code='RNB'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'SNV', 'Savanur', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='HVR'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.snv@nwkrtc.dev','head.snv@nwkrtc.dev','Savanur Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='HVR' AND dp.code='SNV'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.snv@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='HVR' AND dp.code='SNV'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
-- Division: Hubli-Dharwad City Division
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'HDC', 'Hubli-Dharwad City Division', 'Hubli-Dharwad City Division' FROM corporations c WHERE c.code = 'NWKRTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.hdc@nwkrtc.dev','admin.hdc@nwkrtc.dev','Hubli-Dharwad City Division Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='HDC'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.hdc@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='HDC'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'HBC1', 'Hubli City-1', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='HDC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.hbc1@nwkrtc.dev','head.hbc1@nwkrtc.dev','Hubli City-1 Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='HDC' AND dp.code='HBC1'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.hbc1@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='HDC' AND dp.code='HBC1'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'HBR', 'Hubli BRTS', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='HDC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.hbr@nwkrtc.dev','head.hbr@nwkrtc.dev','Hubli BRTS Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='HDC' AND dp.code='HBR'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.hbr@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='HDC' AND dp.code='HBR'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'DBR', 'Dharwad BRTS', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='NWKRTC' AND d.code='HDC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.dbr@nwkrtc.dev','head.dbr@nwkrtc.dev','Dharwad BRTS Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='NWKRTC' AND d.code='HDC' AND dp.code='DBR'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.dbr@nwkrtc.dev' AND c.code='NWKRTC' AND d.code='HDC' AND dp.code='DBR'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);

-- ------------------------------------------------------------------
-- BMTC MASTER DATA: divisions + depots + admins
-- ------------------------------------------------------------------
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password) VALUES
('bmtc.admin@kabus.dev', 'bmtc.admin@kabus.dev', 'BMTC Division Admin', '$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO', 1, 0, 1)
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO corporation_admins (user_id, corporation_id)
SELECT u.id, c.id FROM users u, corporations c WHERE u.username='bmtc.admin@kabus.dev' AND c.code='BMTC'
ON DUPLICATE KEY UPDATE corporation_id = VALUES(corporation_id);

-- Division: Central Zone
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'CTZ', 'Central Zone', 'Central Zone' FROM corporations c WHERE c.code = 'BMTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.ctz@bmtc.dev','admin.ctz@bmtc.dev','Central Zone Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='CTZ'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.ctz@bmtc.dev' AND c.code='BMTC' AND d.code='CTZ'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'SBN', 'Subhashnagar', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='CTZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.sbn@bmtc.dev','head.sbn@bmtc.dev','Subhashnagar Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='CTZ' AND dp.code='SBN'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.sbn@bmtc.dev' AND c.code='BMTC' AND d.code='CTZ' AND dp.code='SBN'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'KTG', 'Kathriguppe', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='CTZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.ktg@bmtc.dev','head.ktg@bmtc.dev','Kathriguppe Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='CTZ' AND dp.code='KTG'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.ktg@bmtc.dev' AND c.code='BMTC' AND d.code='CTZ' AND dp.code='KTG'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'ITP', 'ITPL', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='CTZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.itp@bmtc.dev','head.itp@bmtc.dev','ITPL Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='CTZ' AND dp.code='ITP'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.itp@bmtc.dev' AND c.code='BMTC' AND d.code='CTZ' AND dp.code='ITP'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'HSR', 'HSR Layout', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='CTZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.hsr@bmtc.dev','head.hsr@bmtc.dev','HSR Layout Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='CTZ' AND dp.code='HSR'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.hsr@bmtc.dev' AND c.code='BMTC' AND d.code='CTZ' AND dp.code='HSR'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'HBB', 'Hebbala', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='CTZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.hbb@bmtc.dev','head.hbb@bmtc.dev','Hebbala Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='CTZ' AND dp.code='HBB'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.hbb@bmtc.dev' AND c.code='BMTC' AND d.code='CTZ' AND dp.code='HBB'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
-- Division: South Zone
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'STZ', 'South Zone', 'South Zone' FROM corporations c WHERE c.code = 'BMTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.stz@bmtc.dev','admin.stz@bmtc.dev','South Zone Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='STZ'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.stz@bmtc.dev' AND c.code='BMTC' AND d.code='STZ'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'STN', 'Shanthinagar', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='STZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.stn@bmtc.dev','head.stn@bmtc.dev','Shanthinagar Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='STZ' AND dp.code='STN'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.stn@bmtc.dev' AND c.code='BMTC' AND d.code='STZ' AND dp.code='STN'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'JYN', 'Jayanagara', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='STZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.jyn@bmtc.dev','head.jyn@bmtc.dev','Jayanagara Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='STZ' AND dp.code='JYN'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.jyn@bmtc.dev' AND c.code='BMTC' AND d.code='STZ' AND dp.code='JYN'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'BSK', 'Banashankari', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='STZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.bsk@bmtc.dev','head.bsk@bmtc.dev','Banashankari Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='STZ' AND dp.code='BSK'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.bsk@bmtc.dev' AND c.code='BMTC' AND d.code='STZ' AND dp.code='BSK'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'JGN', 'Jigani', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='STZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.jgn@bmtc.dev','head.jgn@bmtc.dev','Jigani Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='STZ' AND dp.code='JGN'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.jgn@bmtc.dev' AND c.code='BMTC' AND d.code='STZ' AND dp.code='JGN'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'KTD', 'Kothanur Dinne', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='STZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.ktd@bmtc.dev','head.ktd@bmtc.dev','Kothanur Dinne Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='STZ' AND dp.code='KTD'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.ktd@bmtc.dev' AND c.code='BMTC' AND d.code='STZ' AND dp.code='KTD'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'ANP', 'Anjanapura', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='STZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.anp@bmtc.dev','head.anp@bmtc.dev','Anjanapura Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='STZ' AND dp.code='ANP'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.anp@bmtc.dev' AND c.code='BMTC' AND d.code='STZ' AND dp.code='ANP'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
-- Division: East Zone
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'ETZ', 'East Zone', 'East Zone' FROM corporations c WHERE c.code = 'BMTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.etz@bmtc.dev','admin.etz@bmtc.dev','East Zone Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='ETZ'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.etz@bmtc.dev' AND c.code='BMTC' AND d.code='ETZ'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'IDR', 'Indiranagar', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='ETZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.idr@bmtc.dev','head.idr@bmtc.dev','Indiranagar Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='ETZ' AND dp.code='IDR'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.idr@bmtc.dev' AND c.code='BMTC' AND d.code='ETZ' AND dp.code='IDR'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'KMG', 'Koramangala', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='ETZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.kmg@bmtc.dev','head.kmg@bmtc.dev','Koramangala Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='ETZ' AND dp.code='KMG'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.kmg@bmtc.dev' AND c.code='BMTC' AND d.code='ETZ' AND dp.code='KMG'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'ECY', 'Electronic City', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='ETZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.ecy@bmtc.dev','head.ecy@bmtc.dev','Electronic City Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='ETZ' AND dp.code='ECY'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.ecy@bmtc.dev' AND c.code='BMTC' AND d.code='ETZ' AND dp.code='ECY'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'SRC', 'Surya City', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='ETZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.src@bmtc.dev','head.src@bmtc.dev','Surya City Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='ETZ' AND dp.code='SRC'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.src@bmtc.dev' AND c.code='BMTC' AND d.code='ETZ' AND dp.code='SRC'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'CNG', 'Chikkanagamangala', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='ETZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.cng@bmtc.dev','head.cng@bmtc.dev','Chikkanagamangala Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='ETZ' AND dp.code='CNG'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.cng@bmtc.dev' AND c.code='BMTC' AND d.code='ETZ' AND dp.code='CNG'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'GJR', 'Gunjur', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='ETZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.gjr@bmtc.dev','head.gjr@bmtc.dev','Gunjur Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='ETZ' AND dp.code='GJR'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.gjr@bmtc.dev' AND c.code='BMTC' AND d.code='ETZ' AND dp.code='GJR'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'KDH', 'Kodathi', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='ETZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.kdh@bmtc.dev','head.kdh@bmtc.dev','Kodathi Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='ETZ' AND dp.code='KDH'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.kdh@bmtc.dev' AND c.code='BMTC' AND d.code='ETZ' AND dp.code='KDH'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'SDM', 'Sadaramangala', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='ETZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.sdm@bmtc.dev','head.sdm@bmtc.dev','Sadaramangala Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='ETZ' AND dp.code='SDM'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.sdm@bmtc.dev' AND c.code='BMTC' AND d.code='ETZ' AND dp.code='SDM'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
-- Division: West Zone
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'WTZ', 'West Zone', 'West Zone' FROM corporations c WHERE c.code = 'BMTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.wtz@bmtc.dev','admin.wtz@bmtc.dev','West Zone Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='WTZ'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.wtz@bmtc.dev' AND c.code='BMTC' AND d.code='WTZ'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'KGR', 'Kengeri', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='WTZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.kgr@bmtc.dev','head.kgr@bmtc.dev','Kengeri Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='WTZ' AND dp.code='KGR'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.kgr@bmtc.dev' AND c.code='BMTC' AND d.code='WTZ' AND dp.code='KGR'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'DPN', 'Deepanjalinagar', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='WTZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.dpn@bmtc.dev','head.dpn@bmtc.dev','Deepanjalinagar Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='WTZ' AND dp.code='DPN'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.dpn@bmtc.dev' AND c.code='BMTC' AND d.code='WTZ' AND dp.code='DPN'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'CHL', 'Chandra Layout', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='WTZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.chl@bmtc.dev','head.chl@bmtc.dev','Chandra Layout Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='WTZ' AND dp.code='CHL'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.chl@bmtc.dev' AND c.code='BMTC' AND d.code='WTZ' AND dp.code='CHL'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'CNS', 'Channasandra', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='WTZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.cns@bmtc.dev','head.cns@bmtc.dev','Channasandra Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='WTZ' AND dp.code='CNS'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.cns@bmtc.dev' AND c.code='BMTC' AND d.code='WTZ' AND dp.code='CNS'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'PNL', 'Poornapragna Layout', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='WTZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.pnl@bmtc.dev','head.pnl@bmtc.dev','Poornapragna Layout Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='WTZ' AND dp.code='PNL'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.pnl@bmtc.dev' AND c.code='BMTC' AND d.code='WTZ' AND dp.code='PNL'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'BDD', 'Bidadi', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='WTZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.bdd@bmtc.dev','head.bdd@bmtc.dev','Bidadi Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='WTZ' AND dp.code='BDD'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.bdd@bmtc.dev' AND c.code='BMTC' AND d.code='WTZ' AND dp.code='BDD'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
-- Division: North Zone
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'NTZ', 'North Zone', 'North Zone' FROM corporations c WHERE c.code = 'BMTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.ntz@bmtc.dev','admin.ntz@bmtc.dev','North Zone Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='NTZ'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.ntz@bmtc.dev' AND c.code='BMTC' AND d.code='NTZ'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'YSP', 'Yeshwanthpura', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='NTZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.ysp@bmtc.dev','head.ysp@bmtc.dev','Yeshwanthpura Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='NTZ' AND dp.code='YSP'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.ysp@bmtc.dev' AND c.code='BMTC' AND d.code='NTZ' AND dp.code='YSP'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'YLK', 'Yelahanka', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='NTZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.ylk@bmtc.dev','head.ylk@bmtc.dev','Yelahanka Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='NTZ' AND dp.code='YLK'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.ylk@bmtc.dev' AND c.code='BMTC' AND d.code='NTZ' AND dp.code='YLK'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'MSP', 'M.S. Palya', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='NTZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.msp@bmtc.dev','head.msp@bmtc.dev','M.S. Palya Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='NTZ' AND dp.code='MSP'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.msp@bmtc.dev' AND c.code='BMTC' AND d.code='NTZ' AND dp.code='MSP'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'SDH', 'Sadenahalli', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='NTZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.sdh@bmtc.dev','head.sdh@bmtc.dev','Sadenahalli Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='NTZ' AND dp.code='SDH'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.sdh@bmtc.dev' AND c.code='BMTC' AND d.code='NTZ' AND dp.code='SDH'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'DVH', 'Devanahalli', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='NTZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.dvh@bmtc.dev','head.dvh@bmtc.dev','Devanahalli Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='NTZ' AND dp.code='DVH'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.dvh@bmtc.dev' AND c.code='BMTC' AND d.code='NTZ' AND dp.code='DVH'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'UTH', 'Uttanahalli', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='NTZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.uth@bmtc.dev','head.uth@bmtc.dev','Uttanahalli Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='NTZ' AND dp.code='UTH'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.uth@bmtc.dev' AND c.code='BMTC' AND d.code='NTZ' AND dp.code='UTH'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
-- Division: North-East Zone
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'NEZ', 'North-East Zone', 'North-East Zone' FROM corporations c WHERE c.code = 'BMTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.nez@bmtc.dev','admin.nez@bmtc.dev','North-East Zone Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='NEZ'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.nez@bmtc.dev' AND c.code='BMTC' AND d.code='NEZ'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'HNR', 'Hennur', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='NEZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.hnr@bmtc.dev','head.hnr@bmtc.dev','Hennur Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='NEZ' AND dp.code='HNR'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.hnr@bmtc.dev' AND c.code='BMTC' AND d.code='NEZ' AND dp.code='HNR'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'RTN', 'R.T. Nagar', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='NEZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.rtn@bmtc.dev','head.rtn@bmtc.dev','R.T. Nagar Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='NEZ' AND dp.code='RTN'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.rtn@bmtc.dev' AND c.code='BMTC' AND d.code='NEZ' AND dp.code='RTN'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'KYN', 'Kalyananagar', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='NEZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.kyn@bmtc.dev','head.kyn@bmtc.dev','Kalyananagar Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='NEZ' AND dp.code='KYN'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.kyn@bmtc.dev' AND c.code='BMTC' AND d.code='NEZ' AND dp.code='KYN'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'KRP', 'K.R. Puram', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='NEZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.krp@bmtc.dev','head.krp@bmtc.dev','K.R. Puram Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='NEZ' AND dp.code='KRP'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.krp@bmtc.dev' AND c.code='BMTC' AND d.code='NEZ' AND dp.code='KRP'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'HKT', 'Hosakote', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='NEZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.hkt@bmtc.dev','head.hkt@bmtc.dev','Hosakote Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='NEZ' AND dp.code='HKT'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.hkt@bmtc.dev' AND c.code='BMTC' AND d.code='NEZ' AND dp.code='HKT'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'MDR', 'Mandur', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='NEZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.mdr@bmtc.dev','head.mdr@bmtc.dev','Mandur Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='NEZ' AND dp.code='MDR'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.mdr@bmtc.dev' AND c.code='BMTC' AND d.code='NEZ' AND dp.code='MDR'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'BYR', 'Byrathi', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='NEZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.byr@bmtc.dev','head.byr@bmtc.dev','Byrathi Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='NEZ' AND dp.code='BYR'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.byr@bmtc.dev' AND c.code='BMTC' AND d.code='NEZ' AND dp.code='BYR'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
-- Division: North-West Zone
INSERT INTO divisions (corporation_id, code, name, head_office)
SELECT c.id, 'NWZ', 'North-West Zone', 'North-West Zone' FROM corporations c WHERE c.code = 'BMTC'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, division_id)
SELECT 'admin.nwz@bmtc.dev','admin.nwz@bmtc.dev','North-West Zone Manager','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,d.id
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='NWZ'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), division_id = VALUES(division_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO division_managers (user_id, division_id)
SELECT u.id, d.id FROM users u, divisions d JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='admin.nwz@bmtc.dev' AND c.code='BMTC' AND d.code='NWZ'
ON DUPLICATE KEY UPDATE division_id = VALUES(division_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'PNY', 'Peenya', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='NWZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.pny@bmtc.dev','head.pny@bmtc.dev','Peenya Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='NWZ' AND dp.code='PNY'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.pny@bmtc.dev' AND c.code='BMTC' AND d.code='NWZ' AND dp.code='PNY'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'SMH', 'Sumanahalli', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='NWZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.smh@bmtc.dev','head.smh@bmtc.dev','Sumanahalli Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='NWZ' AND dp.code='SMH'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.smh@bmtc.dev' AND c.code='BMTC' AND d.code='NWZ' AND dp.code='SMH'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'KNL', 'Kannalli', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='NWZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.knl@bmtc.dev','head.knl@bmtc.dev','Kannalli Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='NWZ' AND dp.code='KNL'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.knl@bmtc.dev' AND c.code='BMTC' AND d.code='NWZ' AND dp.code='KNL'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'NLM', 'Nelamangala', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='NWZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.nlm@bmtc.dev','head.nlm@bmtc.dev','Nelamangala Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='NWZ' AND dp.code='NLM'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.nlm@bmtc.dev' AND c.code='BMTC' AND d.code='NWZ' AND dp.code='NLM'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
INSERT INTO depots (division_id, code, name, address, phone)
SELECT d.id, 'SVP', 'Shivanapura', NULL, NULL
FROM divisions d JOIN corporations c ON c.id = d.corporation_id WHERE c.code='BMTC' AND d.code='NWZ'
ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO users (username, email, full_name, password_hash, enabled, locked, must_change_password, depot_id)
SELECT 'head.svp@bmtc.dev','head.svp@bmtc.dev','Shivanapura Depot Head','$2a$12$IX5CUbQjSLm65AOJ0k7ycekA.GAH56rZgyvrtBha9abrh9Zj5YGbO',1,0,1,dp.id
FROM depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE c.code='BMTC' AND d.code='NWZ' AND dp.code='SVP'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), depot_id = VALUES(depot_id), enabled = 1, locked = 0, must_change_password = 1;
INSERT INTO depot_heads (user_id, depot_id)
SELECT u.id, dp.id FROM users u, depots dp JOIN divisions d ON d.id = dp.division_id JOIN corporations c ON c.id = d.corporation_id
WHERE u.username='head.svp@bmtc.dev' AND c.code='BMTC' AND d.code='NWZ' AND dp.code='SVP'
ON DUPLICATE KEY UPDATE depot_id = VALUES(depot_id);
