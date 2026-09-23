# MySQL performance notes

The schema (`database/schema/schema.sql`) is InnoDB, `utf8mb4`, BIGINT identity
PKs, with dedicated indexes for every hot query path. This document explains the
index choices, the write path, and the tuning knobs.

## Connection path

```
app.datasource.url (application.yml)
  jdbc:mysql://.../ka_bus_tracking?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&rewriteBatchedStatements=true
```

`rewriteBatchedStatements=true` is what makes multi-row inserts/writes efficient
over JDBC (fewer round trips when Hibernate batch-flushes).

HikariCP defaults: `maximum-pool-size=10`, `minimum-idle=2`,
`connection-timeout=30000`. Tune with `DB_POOL_SIZE`:
- writer-heavy GPS startup: start at 10–20 per writer node;
- reader/passenger nodes: 5–10 is usually plenty because pass-rereads are cached
  and queries are short.

Rule of thumb: total `connections ≈ cores × 2 + effective_spindles`, and never
let `pool_size × nodes` exceed what `max_connections` on the server permits.

## Hot tables and their indexes

| Table | Indexes | Serves |
|---|---|---|
| `live_locations` | PK `(bus_id)` (one row per bus) | passenger/admin live views, status eval |
| `location_history` | `(bus_id, captured_at)`, `(gps_session_id, captured_at)`, `(captured_at)` | trip replay, retention purge |
| `gps_sessions` | `uq(session_key)`, `(user_id, status)`, `(bus_id, status)` | GPS start/location/end |
| `refresh_tokens` | `uq(token_hash)`, `(user_id, revoked)` | auth rotation |
| `routes` / `route_stops` | `uq(code)`, `(route_id, stop_order)` | search/suggestions, bus details |
| `trips` | `(route_id, status)`, `(bus_id, status)`, `(trip_date, bus_depot_id)` | trip lists, passenger "running trips" |
| `ad_impressions` | `(campaign_id, viewed_at)`, `(placement_id, viewed_at)` | campaign stats, honest-impression counts |
| `notifications` | `(user_id, is_read, created_at)` | crew/admin inbox |

All FK constraints are indexed implicitly via the referenced queries; the
pattern is: index the *filter* columns, never wrap indexed columns in functions,
and let all datetime filters be range scans over a leading index column.

## The GPS write pattern (the only write-heavy path)

1. `POST /api/crew/gps/location` → validate (ownership, coords, clock skew,
   min interval, rate limit).
2. Upsert `live_locations` by `bus_id` (update speed/heading/accuracy/status/
   captured_at).
3. Append to `location_history` partition for `captured_at` month.
4. Update `gps_sessions.updates_count` (cheap PK update).

That is one small update + one insert per 5 s per bus. 1,000 buses = only
~200 ops/s. No locks on shared hot rows because each bus is its own PK row.
Never build aggregates in the request path — the periodic live-status evaluator
does bulk updates with small batches (OFFLINE stamping), and dashboard figures
are COUNT queries over tiny tables.

## Partitioning `location_history` (see DATA_RETENTION.md)

```sql
ALTER TABLE location_history
  PARTITION BY RANGE COLUMNS (captured_at) (
    PARTITION p202601 VALUES LESS THAN ('2026-02-01'),
    PARTITION p202602 VALUES LESS THAN ('2026-03-01'),
    ... );
```

Monthly partitions give O(1)-ish drop for retention and keep per-partition
index trees small. Keep a `p_future` partition (`LESS THAN MAXVALUE`) and
script the add/drop cycle (see `database/run_migrations.ps1` pattern).

## Useful knobs/checks

- `innodb_buffer_pool_size` ≈ 60–70% of RAM for a DB-dedicated VM.
- `innodb_flush_log_at_trx_commit=2` on replicas (never on the primary unless
  trading some durability is accepted).
- `max_connections` sanity check; enable `performance_schema` for slow-query
  archaeology; set `long_query_time=1` in staging.
- Watch `HikariPool timeout` counts: raised `connection-timeout` only hides
  real pool starvation — raise the pool or reduce load instead.
- Use `EXPLAIN` for any new query; every passenger query should be index-only
  or an index range scan.

## Verified

Applied and smoke-tested against **MySQL 9.7.1 (sandbox, port 3307)**:
`schema.sql` + `seed.sql` applied cleanly (29 tables), backend booted with
`ddl-auto=none`, login → dashboard → trips-by-date all read/wrote correctly
after the DDL fixes below.

### Schema fixes found during MySQL verification (already applied)

- `refresh_tokens`, `location_history`, `recent_searches`, `ad_impressions`,
  `audit_logs` were missing `updated_at` (entities require it, `BaseEntity`
  inserts it non-null). Added `updated_at DATETIME(3) ... ON UPDATE
  CURRENT_TIMESTAMP(3)` to the DDL.