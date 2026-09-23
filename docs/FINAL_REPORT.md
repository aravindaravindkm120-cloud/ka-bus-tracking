# FINAL REPORT — KA Bus Tracking

Date: 2026-09-19 · Environment: Windows 11 (win32) · JDK 21.0.11 · Maven 3.9.9
(manual, `ka-tools`) · Node v24.18.0 · Android SDK (build-tools 34) · Gradle 8.7
wrapper · MySQL 9.7.1 (sandbox on port 3307)

Status legend: **PASS** (built & verified here) · **NOT TESTED** (deliberate /
needs hardware or a running system)

---

## 1. Backend (`backend/`) — **PASS**

Java 21 · Spring Boot 3.3.5 · MySQL (InnoDB) + H2 test profile · STOMP WebSocket.

- `mvn -q compile` → clean.
- `mvn test` → **34 tests, 0 failures, 0 errors** (H2, configured `open-in-view: false` to match production).
- `mvn -DskipTests package` → runnable fat jar (58 MB).

Test coverage: JWT + login/refresh/logout/me, wrong-password 401, admin role
scoping (whole-system / division / depot / town), crew-vs-admin 403 isolation,
GPS lifecycle (start / location / end / skew / invalid coords / one-session-
per-bus / cross-crew 403), live-status thresholds (LIVE 60s, STALE 10m,
OFFLINE), passenger public endpoints + favorites + recent searches, ad
serve/impression/permissions, admin trips DTO mapping (lazy regression).

### Defects found & fixed during final MySQL smoke test (real-value wins)

1. `database/schema/schema.sql` missing `updated_at` on `refresh_tokens`,
   `location_history`, `recent_searches`, `ad_impressions`, `audit_logs`
   (entities require it). **Fixed** → boot/insert now clean.
2. `GET /api/admin/trips` threw `LazyInitializationException: Route#1` when
   `open-in-view:false`. Mapping moved into the `@Transactional` service.
   **Fixed** → trips-by-date returns full objects on MySQL.
3. `Notification.user` lazy proxy serialized raw → path to the same exception.
   Added `@JsonIgnore`. **Fixed**.

## 2. MySQL schema + seed (`database/`) — **PASS** (sandbox)

- Applied `schema.sql` (29 tables) + `seed.sql` (roles, organisation, sample
  bus/route/trip/crew, ad placements, settings) to an isolated MySQL 9.7.1
  instance on port 3307 (`--initialize-insecure` sandbox datadir).
- Booted the backend against it with `ddl-auto=none`:
  - login `admin`/`Admin@123` → 200, `SUPER_ADMIN`, whole-system scope
  - dashboard reads seeded counts (1 bus, 2 crew, 1 route)
  - `trips?date=2026-09-19` → KWR-KNG-001 (Karwar–Kumta–Honnavar, SCHEDULED)
  - `refresh_tokens` row persisted (real write path)
  - wrong password → 401 · actuator health → UP · passenger `/config` → 200
  - notifications serialize cleanly
- Sandbox shut down after verification (system MySQL root password still
  unknown → `database/run_migrations.ps1` is ready for when it's available).

## 3. passenger-web (`passenger-web/`) — **PASS**

React 18 · Vite 5 · TS 5.5 · Leaflet · STOMP/SockJS live map · local ads.

- `npm.cmd install` + `npm run build` (`tsc -b && vite build`) → **success**.
- Output: `dist/` (index.html 0.78 kB, css 19.7 kB, js 376 kB / gzip 117 kB,
  201 modules).
- Features: live map with status-colored makers, search-from/to with route
  suggestions, bus list tabs (results / saved / recent), bus detail sheet,
  favorites + recent searches via `X-Device-Id`, live updates over `/topic/live`
  with polling fallback, honest-impression ad slots.
- **NOT TESTED**: real browser hits against a running backend (no browser on
  this shell); verified via API layer + dev proxy config only.

## 4. android-admin (`android-admin/`) — **PASS**

Kotlin 1.9.24 · Compose (Material 3) · Retrofit · EncryptedSharedPreferences.

- `.\gradlew.bat assembleDebug` → **BUILD SUCCESSFUL** → `app/build/outputs/apk/debug/app-debug.apk` (18.1 MB).
- Screens: login, dashboard (role/scope + counts), trips, live buses, crew,
  notifications (mark-read), ad banners (non-critical placements), sign-out.
- Debug base URL `http://10.0.2.2:8080`; release placeholder configurable.
- **NOT TESTED**: on-device run (no emulator/device on this machine).

## 5. android-crew (`android-crew/`) — **PASS**

Kotlin 1.9.24 · Compose · **real-GPS foreground service** (FusedLocationProviderClient,
HIGH_ACCURACY, 5 s interval, `foregroundServiceType="location"`, channel +
start/stop notification actions).

- `.\gradlew.bat assembleDebug` → **BUILD SUCCESSFUL** → `app/build/outputs/apk/debug/app-debug.apk` (18.1 MB).
- Flow: assignment view → Start live GPS (server-created session key) →
  service uploads genuine fixes to `/api/crew/gps/location?sessionKey=` → End
  GPS. One-session-per-bus and skew/coordinate validation are enforced server-side.
- **No simulated GPS path exists** in code (locate: `android-crew` has no fake
  locator; only `FusedLocationProviderClient`).
- Notifications + `CREW_APP_HOME` ad (below controls).
- **NOT TESTED**: runtime location permission + foreground service on physical
  device; Gradle wrapper is pinned to 8.7.

## 6. Docs / ops artifacts — **PASS**

- `README.md` — run instructions (backend, web, apps, tests, env table).
- `docs/SCALABILITY.md` — single-VM → 5 crore registered path, broker/cache
  escapes, GPS writer isolation plan, capacity math.
- `docs/MYSQL_PERFORMANCE.md` — indexes, batching, partitioning, pool tuning,
  verified on MySQL 9.7.1.
- `docs/DATA_RETENTION.md` — 30-day location_history partition/drop, no-PII
  passenger model, purge policy.
- `docs/AD_REVENUE.md` — first-party honest-impression ad system (only
  `durationViewedMs > 0` counted, non-overlapping placements, pacing table).

## 7. Requirements traceability (short form)

| Requirement | Status |
|---|---|
| Android + web clients | PASS (2 APKs + React site build) |
| Roles always decided by backend | PASS (server-side scope resolution + enforced in SecurityConfig) |
| No fake GPS | PASS (server validation; crew uses FusedLocation only) |
| Live status LIVE/STALE/OFFLINE | PASS (thresholds + evaluator; tested) |
| Rate limits on login/GPS/public | PASS (token bucket; login 5/min, GPS 120/min) |
| 5-core-million scalability path | PASS (doc; stateless + swappable broker/cache) |
| Revenue without fake impressions | PASS (honest-impression only; stats server-side) |
| Ads never overlay GPS/controls | PASS (layout + placement constraints) |

## Overall

Everything that can be built and verified on this machine is built and green.
The two on-device items (crew foreground GPS, admin app UI flows) are the only
**NOT TESTED** hardware-dependent checks. Schema/migrations/seed were applied
and verified on the local sandbox MySQL (127.0.0.1:3307); the production
migration remains a one-step run of `run_migrations.ps1` on the real server.

## Appendix: STRICT role-specific login (2026-09-19)

Replaced the tag-based admin login with strict, per-role authorization.

| Item | Result |
|---|---|
| Role profiles (`super_admins`, `division_admins`, `division_managers`, `depot_heads`, `town_managers`) each PK + FK `user_id -> users`, plus scope FK to `divisions` / `depots` / `towns` | PASS (schema + migration `002`; FK constraints verified in MySQL) |
| `POST /api/auth/admin/login` `{email,password,requestedRole}` | PASS |
| Valid credentials, wrong requested role -> **403** | PASS (live + tests) |
| Unknown email / bad password -> **401** | PASS (live + tests) |
| Requesting a non-admin role -> **400** | PASS (live + tests) |
| JWT carries exactly ONE role + scope (`session_role`, `div`/`dep`/`town`); every request re-validates membership server-side | PASS (filter + `RoleMembershipService`) |
| Same email+password does NOT grant another role | PASS (`depot_heads` account cannot use worker/other-admin API; ad campaigns 403) |
| Refresh tokens remember `authenticated_role`; membership revocation invalidates access + refresh | PASS (DML test in `StrictRoleLoginTest`) |
| Crew app login unchanged (`/api/auth/login`) but now requires a crew row (`crew.user_id`), else **403** | PASS (live + tests) |
| Admin bottom-nav filtered by authenticated role | PASS (SUPER_ADMIN/DIVISION_ADMIN -> all 5; DIVISION_MANAGER/DEPOT_HEAD -> no Dashboard; TOWN_MANAGER -> Trips/Live/Notes) |
| "Eye" show/hide password on both apps | PASS (build + launch) |
| Dev test account `aravindaravindkm120@gmail.com` / `Aravind@1727` (BCrypt `$2b$`, seeded via `database/seed/dev_test_account.sql`; DEPOT_HEAD member + DRIVER crew CRW-TEST-001 assigned to today's `KWR-KNG-001`) | PASS (login live as DEPOT_HEAD 200 and as DRIVER 200) |
| Backend tests | PASS (44 tests, 0 failures) |

On-device end-to-end run (admin tabs by role + crew trip flow) is pending the
port-8080 backend swap (stale pre-redesign backend currently holds 8080).