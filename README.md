# KA Bus Tracking

A complete, self-hosted, zero-cost **Karnataka-wide public bus tracking system**.

Designed to run on a single small VM (₹0 privileges tier eligible) and scale
horizontally toward 5 crore+ registered users when the fleet and passenger
base grow. No cloud-only APIs, no fake GPS, no fake ad impressions.

## What is in the repo

```
ka-bus-tracking/
├── backend/          Spring Boot 3.3 / Java 21 REST + WebSocket API
├── passenger-web     React 18 + TypeScript + Vite + Leaflet (live map for the public)
├── android-admin     Kotlin + Jetpack Compose admin app (all admin roles)
├── android-crew      Kotlin + Compose crew app (DRIVER/CONDUCTOR, real GPS foreground service)
└── database/
    ├── schema/schema.sql        Canonical MySQL DDL (InnoDB, utf8mb4)
    └── seed/seed.sql            Reference data: roles, org, sample suite, ad placements
```

## System shape

- **Roles are always decided by the backend.** `SUPER_ADMIN → DIVISION_ADMIN →
  DIVISION_MANAGER → DEPOT_HEAD → TOWN_MANAGER → DRIVER/CONDUCTOR`. Each admin's
  data scope is derived server-side from the user's own records — never from
  client input.
- **Hierarchy**: corporation → division → depot → town → bus → crew → trip →
  live GPS. All scoped queries are translated to depot-ID lists.
- **Live tracking**: crew app uploads genuine device GPS fixes
  (5 s interval) to `/api/crew/gps/*`; a single live row per bus plus append-only
  history; status is derived: LIVE ≤60 s, STALE ≤10 min, else OFFLINE.
- **Live map**: passengers receive real-time updates over STOMP/SockJS
  `/topic/live`; the passenger web app also polls `/api/public/buses/live`.
- **Ads**: first-party, centralized. Slot config lives in `ad_placements`,
  served by `GET /api/public/ad/serve?placement=`. **An impression is recorded
  only after the ad was genuinely visible ≥1 s** (IntersectionObserver / timer)
  with `durationViewedMs > 0` enforced server-side. Ads never overlay GPS or
  Start/End controls.
- **Auth**: stateless JWT (access 15 min) + rotating refresh tokens (30 d)
  stored hashed (SHA-256) in `refresh_tokens`. Rate-limited login.
- **No fake data paths**: GPS uploads are validated for coordinate ranges,
  device-to-server clock skew (≤30 s), per-session ownership, min interval,
  and one active session per bus.

## Tech / versions

| Piece | Version |
|---|---|
| Backend | Spring Boot 3.3.5, Java 21, Hibernate 6.5, Spring Security, STOMP |
| DB | MySQL 8+/9 (InnoDB, `rewriteBatchedStatements=true`); tests use H2 MySQL-mode |
| Passenger web | React 18.3, Vite 5, TypeScript 5.5, Leaflet 1.9, @stomp/stompjs 7 |
| Admin / Crew | Kotlin 1.9.24, AGP 8.5.2, Compose BOM 2024.06, minSdk 26, target 34, FusedLocationProviderClient |

## Prerequisites (this machine)

- JDK 21 (`JAVA_HOME`)
- Maven: `C:\Users\Admin\ka-tools\apache-maven-3.9.9\bin\mvn.cmd`
- Android SDK at `C:\Users\Admin\AppData\Local\Android\Sdk` (build-tools 34), `ANDROID_HOME` set
- Node 24 → use `npm.cmd` (npm.ps1 is blocked here)

## Run the backend

```powershell
# 1) apply MySQL schema + seed (see database/run_migrations.ps1 for the system MySQL)
mysql -u root -p < database/schema/schema.sql
mysql -u root -p ka_bus_tracking < database/seed/seed.sql

# 2) start (JWT_SECRET is REQUIRED, >=48 chars)
$env:JWT_SECRET="replace-me-with-48+random-chars"
$env:DB_HOST="localhost"; $env:DB_PORT="3306"
$env:DB_USER="root"; $env:DB_PASSWORD="your-pass"; $env:DB_NAME="ka_bus_tracking"
$env:SEED_ADMIN_PASSWORD="Admin@123"
mvn -f backend\pom.xml spring-boot:run
```

First start bootstraps the SUPER_ADMIN login from `app.seed.*`
(default `admin` / `Admin@123`) — **change it** and set `SEED_ADMIN_PASSWORD`
in production. JPA runs `ddl-auto: none`; the schema is owned by
`database/schema/schema.sql`.

## Run the passenger web (dev)

```powershell
cd passenger-web
npm.cmd install
npm run dev        # http://localhost:5173, proxies /api + /ws to :8080
npm run build      # type-checks (tsc -b) and bundles to dist/
```

## Build the Android apps

```powershell
$env:ANDROID_HOME="C:\Users\Admin\AppData\Local\Android\Sdk"
    (cd android-admin; .\gradlew.bat assembleDebug)   # KA Bus Admin
    (cd android-crew;  .\gradlew.bat assembleDebug)   # KA Bus Crew
# APKs: app\build\outputs\apk\debug\app-debug.apk
```

Debug builds point at `http://10.0.2.2:8080` (Android emulator → host).

## Test

- Backend integration tests run against in-memory H2 in MySQL compatibility
  mode — **no MySQL required**:
  ```powershell
  mvn -f backend\pom.xml test      # 34 tests, 0 failures
  ```
- Coverage: auth/JWT rotation/logout, admin scoping, crew-vs-admin isolation,
  GPS lifecycle + anti-spoofing, live status thresholds, passenger API +
  favorites + recent searches, ads (serve/impression/permissions), and admin
  trips DTO mapping (lazy-load regression).

## Docs

- `docs/SCALABILITY.md` — from one VM to 5 crore users without magic
- `docs/MYSQL_PERFORMANCE.md` — indexes, batching, partitioning, tuning
- `docs/DATA_RETENTION.md` — location_history retention & purge
- `docs/AD_REVENUE.md` — first-party ad system, honest impressions
- `docs/FINAL_REPORT.md` — build/verification status per component

## Key configuration (backend `application.yml` + env overrides)

| Env | Default | Meaning |
|---|---|---|
| `JWT_SECRET` | *(required)* | HMAC key ≥48 chars |
| `JWT_EXPIRATION_MS` / `JWT_REFRESH_EXPIRATION_MS` | 900000 / 2592000000 | access / refresh lifetime |
| `RATE_LIMIT_LOGIN` / `RATE_LIMIT_GPS` / `RATE_LIMIT_PUBLIC` | 5 / 120 / 120 per min | in-memory rate limits |
| `GPS_MIN_INTERVAL_MS` / `GPS_MAX_SKEW_MS` | 3000 / 30000 | upload pacing & clock-skew guard |
| `LIVE_SECONDS` / `STALE_SECONDS` | 60 / 600 | live status thresholds |
| `LOCATION_HISTORY_RETENTION_DAYS` | 30 | raw history retention |
| `DB_POOL_SIZE` | 10 | HikariCP max pool |

## API surface (summary)

```
POST /api/auth/login | /refresh | /logout        GET /api/auth/me
Admin (role-scoped): /api/admin/dashboard | /trips | /live-buses | /crew | /notifications
Crew:  /api/crew/assignment | /status | /notifications | /ad
Gps:   POST /api/crew/gps/start | /location?sessionKey= | /end?sessionKey=
Passenger: /api/public/config | /buses/live | /buses/{id} | /search | /suggestions
           /favorites | /recent-searches | /ads/serve | /ads/impression
WS:    /ws  → subscribe /topic/live
```