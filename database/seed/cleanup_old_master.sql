-- ============================================================
-- ONE-TIME destructive cleanup: replace legacy master data with
-- the canonical division/depot/manager/depot-head tree that
-- seed.sql now creates.
--
-- This file:
--   * removes OLD seeded accounts  <code>.admin@<corp>.dev
--     and their division_admin / division_manager memberships
--   * removes OLD depot-head accounts head.<code>@<corp>.dev
--   * removes Account A's legacy division_admins row
--   * removes any seeded corporation_admin rows (@kabus.dev)
--   * deletes the OLD master-data divisions/depots/towns for the
--     four corporations (the KAD/KWD/KWN baseline is PRESERVED)
--
-- SAFETY: only safe while no child rows (routes, trips, towns, buses,
-- crew) have been created under the OLD master divisions. Run it once,
-- immediately before re-running the new seed.sql.
-- ============================================================

-- 1) Old depot-head memberships + depot-head users ---------------------
DELETE dh FROM depot_heads dh
JOIN users u ON u.id = dh.user_id
WHERE u.email LIKE 'head.%@%.dev';

DELETE dh FROM depot_heads dh
JOIN users u ON u.id = dh.user_id
WHERE u.email LIKE '%.admin@%.dev';

-- 2) Old division manager memberships (manager accounts) ---------------
DELETE dm FROM division_managers dm
JOIN users u ON u.id = dm.user_id
WHERE u.email LIKE '%.admin@%.dev';

-- 3) Old division_admin memberships (all legacy) -----------------------
DELETE da FROM division_admins da
JOIN users u ON u.id = da.user_id
WHERE u.email LIKE '%.admin@%.dev' OR u.email = 'aravindaravindkm@gmail.com';

-- 4) Old corporation_admin memberships + their @kabus.dev users --------
DELETE ca FROM corporation_admins ca
JOIN users u ON u.id = ca.user_id
WHERE u.email LIKE '%@kabus.dev';

-- 5) Old master-data users (any leftover) ------------------------------
DELETE FROM users WHERE email LIKE 'head.%@%.dev';
DELETE FROM users WHERE email LIKE '%.admin@%.dev';
DELETE FROM users WHERE email LIKE '%@kabus.dev';

-- 6) Baseline KAD/KWD/KWN is preserved; delete old towns/depots then
--    divisions for the four corporations (never by cascade risk).
DELETE t FROM towns t
JOIN depots dp ON dp.id = t.depot_id
JOIN divisions d ON d.id = dp.division_id
JOIN corporations c ON c.id = d.corporation_id
WHERE c.code IN ('KSRTC','BMTC','KKRTC','NWKRTC') AND d.code <> 'KAD';

DELETE dp FROM depots dp
JOIN divisions d ON d.id = dp.division_id
JOIN corporations c ON c.id = d.corporation_id
WHERE c.code IN ('KSRTC','BMTC','KKRTC','NWKRTC') AND d.code <> 'KAD';

DELETE d FROM divisions d
JOIN corporations c ON c.id = d.corporation_id
WHERE c.code IN ('KSRTC','BMTC','KKRTC','NWKRTC') AND d.code <> 'KAD';

-- ============================================================
-- Done. Verify before re-seeding:
--   SELECT d.code, c.code FROM divisions d
--   JOIN corporations c ON c.id = d.corporation_id ORDER BY c.code, d.code;
--   -> only the KAD baseline division should remain for the four corps
--      (plus whatever non-KAD data you created yourself).
-- ============================================================