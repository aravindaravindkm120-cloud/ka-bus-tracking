# KA Bus Tracking - Database Migrations

## Strategy

Migration files are **incremental, append-only** changes. The canonical baseline
lives in `../schema/schema.sql`; it is applied once as the initial schema.
Every later change is a new numbered file in this directory:

```
001_baseline.sql          # no-op marker: schema.sql is the baseline
002_some_feature.sql      # future change (ALTER TABLE / new table)
003_another_change.sql    # future change
```

## Applying migrations

Use the runner script from the repository root:

```powershell
.\database\run_migrations.ps1 -DbUser root -DbPassword yourpassword
```

What the runner does:

1. Creates the database `ka_bus_tracking` (UTF8MB4).
2. Applies `../schema/schema.sql` (idempotent: `CREATE TABLE IF NOT EXISTS`).
3. Applies every `00X_*.sql` file in this directory in order.
4. Applies `../seed/seed.sql` (idempotent).
5. Records applied files in a `schema_migrations` table.

Migration files are tracked individually, so a migration is only executed once
(unless re-run with `-Force`, which is safe because all statements are
idempotent).

## Adding a new migration

Create a new file with the next sequence number, for example `006_add_fuel_usage.sql`:

```sql
ALTER TABLE buses ADD COLUMN fuel_capacity_litres INT NULL AFTER fuel_type;
```

Then run:

```powershell
.\database\run_migrations.ps1
```

## Notes

- Always write migrations as **idempotent** where possible.
- Never edit an already-applied migration; append a new file.
- The `schema_migrations` table records which files have been applied.
- Back up the database before applying destructive migrations in production.