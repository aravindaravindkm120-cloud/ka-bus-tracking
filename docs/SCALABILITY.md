# Scalability — from one ₹0 VM to 5 crore users

The system is deliberately boring: a stateless Spring Boot service in front of
MySQL, with a WebSocket fan-out channel. It starts on one small VM and scales
by adding identical nodes. There are no cloud-only SDKs, no hosted message
queues, and no hard technical bets that can't be reversed.

## Single node (now)

Runs entirely on one VM:

- Spring Boot (embedded Tomcat) + HikariCP (default pool 10) → MySQL (InnoDB).
- In-process Caffeine cache (5s/60s TTL) for config/passenger lookups.
- In-memory WebSocket STOMP broker for `/topic/live`.
- In-memory token-bucket rate limiting (login / GPS / public).
- All processes (backend only) are plain `java -jar` → one owner, one log.

This comfortably serves a fleet of hundreds of buses and tens of thousands of
concurrent passengers — well beyond the launch requirement.

## The load model

| Load vector | Rate | Design response |
|---|---|---|
| GPS fix upload (every 5 s per running bus) | ~1,000 buses → 12 k/min | tiny JSON, `rewriteBatchedStatements`, single row update per bus, history in a partitioned append table |
| Passenger live map | dozens of viewers per bus | WebSocket fan-out (1 update → N viewers), 30 s HTTP polling fallback |
| Search / suggestions | bursty | debit by limits; Caffeine cache; index on route origin/destination |
| Login | human-speed | rate-limited (5/min/IP+user) |

Always keep a headroom factor: the required peak is 5 crore registered users,
but registered ≠ concurrent. Realistically the *concurrent* passenger peek is a
small fraction of registered users, and each one reads a tiny cached bus list.

## Scale-out path (whenever needed)

1. **Stateless horizontal scale (backend)** — the API has no sticky local state
   except three in-memory things, each with an explicit escape hatch:
   - WebSocket session registry (in-memory STOMP broker). Move to an external
     broker (`spring-boot-starter` → RabbitMQ/ActiveMQ STOMP relay) or use a
     small broadcast layer; document that the passenger app already falls back
     to 30 s polling, so a missed push only degrades freshness.
   - Caffeine cache — trivially replaceable with a co-located Redis.
   - Rate limit buckets — replace with Lua/Redis window counters.
2. **DB read scale** — MySQL replicas behind a proxy; the public endpoints are
   read-mostly and already DTO-shaped. Write traffic (GPS) stays on primary.
3. **Web tier** — static `passenger-web` bundle served from any CDN/nginx;
   only `/api` and `/ws` hit the backend.
4. **GPS ingest isolation** — if ingest volume hurts passenger latency, point
   the crew app's `/api/crew/gps/location` at a dedicated writer instance with
   its own Hikari pool, then stream confirmations over the same broker.

## Capacity math for the big number

- 1 GPS row per bus per 5 s → a 10 k-bus state = 2 k writes/s on
  `live_locations` (upsert by PK) + append to partitioned `location_history`.
  MySQL handles this comfortably and it is far from the fleet max.
- Live passenger updates are a *broadcast amplification* problem, not a storage
  problem: 1 update → connected viewers. The broker/fan-out tier absorbs it.

## What stays intentionally small

- One schema, one application, one deployable jar per tier node.
- No microservices until a team needs them; split the GPS writer out *only*
  when MySQL writer load proves it (measured, not guessed).

## Principles

- Nothing about a client (admin/crew/passenger) is trusted to decide what it
  may see or do; every query is re-scoped server-side.
- Every hard dependency is swappable through config (broker, cache, rate-limit
  backend) so a scale-up decision is a config change, not a rewrite.
## Load validation (Phase 12)

How to confirm the numbers above on a real instance instead of trusting them:

1. Run the backend against MySQL (not H2) and point a production-like proxy at it.
2. Drive the **public read path** (the 5-crore-passenger profile) with the shipped harness:

   powershell -ExecutionPolicy Bypass -File scripts\load_test.ps1 -BaseUrl http://<host>:8080 -Concurrency 200 -Seconds 60 -IncludePublic

   It bursts anonymous GETs over /api/public/config|routes|buses|search|nearby and
   prints ok/failed + throughput. Expect failures only from deliberate 429
   rate-limiting of /api/public/buses/nearby when limits are exceeded (that is
   a control, not an error).

3. Write-path (GPS ingest) is measured with a *seeded fleet*, because a crew token
   only controls its own bus (GpsFlowTest proves cross-crew 403). Practical
   recipe: seed N driver tokens, start one session per bus (POST /api/crew/gps/start +
   ?busId=...), then hammer POST /api/crew/gps/location per session with a
   staggered 5 s cadence. The design target is **2 k upserts/s** on
   live_locations (+ append to partitioned history) at a 10 k-bus fleet;
   www.gps+sessionKey params keep it single-row per bus.
4. Watch HikariCP pool saturation and slow-query log on MySQL while running the
   load. The public read path should stay in the tens of ms/req; any degradation
   beyond ~70% of the pool is the trigger to move the GPS writer to its own
   instance (documented escape hatch above), then scale readers with replicas.

> These are the only *running-system* actions a new operator must take to confirm
> scalability; everything else is config (broker/cache/rate-limit backend).
