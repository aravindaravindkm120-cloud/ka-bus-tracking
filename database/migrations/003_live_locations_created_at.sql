-- ============================================================
-- KA Bus Tracking - Migrations: 003_live_locations_created_at.sql
--
-- live_locations was missing the created_at audit column that every
-- BaseEntity subclass expects (BASE ENTITY maps created_at/updated_at).
-- Reading live locations raised:
--   Unknown column 'll1_0.created_at' in 'field list'
-- (AdminController.liveBuses / PassengerService.allLiveBuses).
--
-- Safe for both a fresh schema.sql (which now includes the column)
-- and an upgraded 001/002 baseline.
-- ============================================================

SET @col_missing = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'live_locations'
      AND COLUMN_NAME = 'created_at'
);

SET @ddl = IF(@col_missing = 0,
    'ALTER TABLE live_locations ADD COLUMN created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) AFTER id',
    'SELECT ''live_locations.created_at already exists; skipping'' AS note'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;