-- ============================================================
-- KA Bus Tracking - Migrations: 004_bus_numbers.sql
--
-- Promotes the bus number to a master of its own. Until now `buses`
-- carried registration_no/bus_type inline, so a bus number only
-- existed as part of one physical vehicle. That made it impossible
-- to define the fleet's bus-number list per depot independently of
-- which vehicles happen to be registered.
--
-- This migration:
--   1. creates `bus_numbers` (Corporation -> Division -> Depot ->
--      Town + bus_number + bus_type);
--   2. adds buses.bus_number_id;
--   3. backfills one master row per existing bus, so no data is lost;
--   4. links every existing bus to its master row;
--   5. retires the inline registration_no/bus_type columns, whose
--      values now live in bus_numbers.
--
-- Additive first, destructive last: every step is guarded so the
-- file is safe to re-run, and bus_number_id is only tightened to
-- NOT NULL once every bus is known to be mapped.
--
-- Signedness: a foreign key may only reference a column of exactly
-- the same type, signedness included. schema.sql declares ids as
-- BIGINT UNSIGNED, but a database created from an older schema.sql
-- uses plain signed BIGINT throughout. Rather than assume, the
-- migration reads towns.id and matches whatever it finds, so the
-- same file upgrades both a current install and a legacy one.
-- ============================================================

-- ' UNSIGNED' when the existing ids are unsigned, '' when they are
-- signed. Applied via REPLACE() to the templates below.
SET @su = (
    SELECT IF(COUNT(*) > 0, ' UNSIGNED', '')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'towns'
      AND COLUMN_NAME  = 'id'
      AND LOCATE('unsigned', LOWER(COLUMN_TYPE)) > 0
);

-- ------------------------------------------------------------------
-- 1. The bus number master
-- ------------------------------------------------------------------

SET @ddl = REPLACE('
CREATE TABLE IF NOT EXISTS bus_numbers (
    id                  BIGINT@SU NOT NULL AUTO_INCREMENT,
    town_id             BIGINT@SU NOT NULL,
    depot_id            BIGINT@SU NOT NULL,
    division_id         BIGINT@SU NOT NULL,
    corporation_id      BIGINT@SU NOT NULL,
    bus_number          VARCHAR(20)     NOT NULL,
    bus_type            VARCHAR(40)     NOT NULL DEFAULT ''ORDINARY'', -- ORDINARY | EXPRESS | RAJADHARSHA
    enabled             TINYINT(1)      NOT NULL DEFAULT 1,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    -- A bus number is unique within its depot, not system-wide: the same
    -- service number legitimately recurs across depots.
    UNIQUE KEY uq_bus_numbers_depot_number (depot_id, bus_number),
    KEY idx_bus_numbers_corporation (corporation_id),
    KEY idx_bus_numbers_division (division_id),
    KEY idx_bus_numbers_town (town_id),
    CONSTRAINT fk_bus_numbers_town FOREIGN KEY (town_id) REFERENCES towns (id),
    CONSTRAINT fk_bus_numbers_depot FOREIGN KEY (depot_id) REFERENCES depots (id),
    CONSTRAINT fk_bus_numbers_division FOREIGN KEY (division_id) REFERENCES divisions (id),
    CONSTRAINT fk_bus_numbers_corporation FOREIGN KEY (corporation_id) REFERENCES corporations (id)
) ENGINE=InnoDB', '@SU', @su);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------------
-- 2. buses.bus_number_id
-- ------------------------------------------------------------------

SET @col_missing = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'buses'
      AND COLUMN_NAME = 'bus_number_id'
);

SET @ddl = REPLACE(IF(@col_missing = 0,
    'ALTER TABLE buses ADD COLUMN bus_number_id BIGINT@SU NULL AFTER id',
    'SELECT ''buses.bus_number_id already exists; skipping'' AS note'
), '@SU', @su);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------------
-- 3. Backfill: one master row per distinct bus still carrying an
--    inline registration_no. depot/town/division/corporation are
--    derived from the bus's own depot so the master inherits the
--    existing hierarchy without inventing new organization rows.
--
--    Guarded on registration_no still being present. On a fresh
--    install schema.sql already created bus_numbers and buses
--    without the inline columns, and on a re-run step 6 has dropped
--    them, so there is nothing to backfill and nothing to read.
-- ------------------------------------------------------------------

SET @legacy_buses = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'buses'
      AND COLUMN_NAME = 'registration_no'
);

SET @ddl = IF(@legacy_buses > 0,
    'INSERT INTO bus_numbers (town_id, depot_id, division_id, corporation_id, bus_number, bus_type, enabled, created_at, updated_at)
     SELECT DISTINCT
            b.town_id,
            b.depot_id,
            d.division_id,
            dv.corporation_id,
            b.registration_no,
            b.bus_type,
            b.enabled,
            CURRENT_TIMESTAMP(3),
            CURRENT_TIMESTAMP(3)
     FROM buses b
     JOIN depots d ON d.id = b.depot_id
     JOIN divisions dv ON dv.id = d.division_id
     WHERE b.bus_number_id IS NULL
       AND b.registration_no IS NOT NULL
       AND b.registration_no <> ''''
     ON DUPLICATE KEY UPDATE bus_type = VALUES(bus_type)',
    'SELECT ''buses has no legacy registration_no; skipping backfill'' AS note'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------------
-- 4. Link every bus to its master row. Guarded for the same reason
--    as step 3: it matches on the legacy registration_no.
-- ------------------------------------------------------------------

SET @ddl = IF(@legacy_buses > 0,
    'UPDATE buses b
     JOIN depots d ON d.id = b.depot_id
     JOIN bus_numbers bn
       ON bn.depot_id = b.depot_id
      AND bn.bus_number = b.registration_no
     SET b.bus_number_id = bn.id
     WHERE b.bus_number_id IS NULL',
    'SELECT ''buses has no legacy registration_no; skipping link'' AS note'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------------
-- 5. Index + foreign key
-- ------------------------------------------------------------------

SET @idx_missing = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'buses'
      AND COLUMN_NAME = 'bus_number_id'
);

SET @ddl = IF(@idx_missing = 0,
    'ALTER TABLE buses ADD KEY idx_buses_bus_number (bus_number_id)',
    'SELECT ''buses.bus_number_id index already exists; skipping'' AS note'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_missing = (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'buses'
      AND CONSTRAINT_NAME = 'fk_buses_bus_number'
);

SET @ddl = IF(@fk_missing = 0,
    'ALTER TABLE buses ADD CONSTRAINT fk_buses_bus_number FOREIGN KEY (bus_number_id) REFERENCES bus_numbers (id)',
    'SELECT ''fk_buses_bus_number already exists; skipping'' AS note'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------------
-- 6. Retire the inline columns. Their values now live in
--    bus_numbers.bus_number / bus_numbers.bus_type.
-- ------------------------------------------------------------------

SET @reg_missing = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'buses'
      AND COLUMN_NAME = 'registration_no'
);

SET @ddl = IF(@reg_missing > 0,
    'ALTER TABLE buses DROP COLUMN registration_no',
    'SELECT ''buses.registration_no already dropped; skipping'' AS note'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @type_missing = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'buses'
      AND COLUMN_NAME = 'bus_type'
);

SET @ddl = IF(@type_missing > 0,
    'ALTER TABLE buses DROP COLUMN bus_type',
    'SELECT ''buses.bus_type already dropped; skipping'' AS note'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------------
-- 7. Tighten to NOT NULL only if every bus is mapped. If a bus
--    could not be matched above, the column stays nullable and the
--    note tells the operator to investigate rather than failing the
--    whole migration on a NOT NULL violation.
-- ------------------------------------------------------------------

SET @unmapped = (SELECT COUNT(*) FROM buses WHERE bus_number_id IS NULL);

SET @ddl = REPLACE(IF(@unmapped = 0,
    'ALTER TABLE buses MODIFY COLUMN bus_number_id BIGINT@SU NOT NULL',
    'SELECT ''buses still has unmapped rows; bus_number_id left nullable for inspection'' AS note'
), '@SU', @su);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
