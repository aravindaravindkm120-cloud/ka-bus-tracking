# Data retention & privacy

## What is stored and why

| Data | Retention | Why |
|---|---|---|
| `live_locations` (one row per bus) | replaced on every fix; auto-removed when the evaluator marks a bus OFFLINE / superseded | state only, not history |
| `location_history` | **30 days** (`app.retention.location-history-days`, default) | live replay & audits while legally relevant; raw trail must not linger forever |
| `gps_sessions` | kept while history exists (FK), cleaned by the same purge | session ownership + stats |
| `refresh_tokens` | token `expires_at` (30 d); rows revoked on logout / replacement | auth rotation |
| `ad_impressions` | kept (aggregates cheap over indexed `viewed_at`) | honest billing/attribution |
| `notifications` / `audit_logs` | long-lived | ops + security |
| Device identifiers (`device_id`) | per passenger device | favorites, recent searches, ad dedupe — no PII, no login |

## The purge

- `location_history` is `PARTITION BY RANGE COLUMNS (captured_at)` by month
  (see MYSQL_PERFORMANCE.md). Retention = drop whole partitions older than
  `LOCATION_HISTORY_RETENTION_DAYS`, then `OPTIMIZE TABLE` occasionally.
- The backend health/ops surface exposes cleanup status; the seed's
  `system_settings` rows toggle the evaluator and purge:
  - `location_history.purge_enabled`
  - `location_history.retention_days`

## Operational policy (planned/implemented surface)

- **Purpose limitation**: only the minimal data needed for live tracking,
  trip history, favorites, and honest ad measurement is collected.
- **Scheduled cleanup**: a monthly partition drop keeps the fleet's raw GPS
  trail at 30 days, which bounds both storage and any future subject-access
  work.
- **No analytics shadow-CDP**: no third-party SDKs, no telemetry beacons, no
  cross-device profiles. Ads are first-party (see AD_REVENUE.md).
- **Anonymized by design**: passengers are a `device_id` UUID, not an account.

## Implementation promises to honor

- Never log `password_hash`, tokens, or `device_id` in plaintext logs.
- Purge worker deletes `location_history` *before* associated `gps_sessions`
  rows, preserving referential integrity (FKs are `ON DELETE` safe).
- When volume grows, move the purge off the primary (replica/local dump) —
  never block the writer path.