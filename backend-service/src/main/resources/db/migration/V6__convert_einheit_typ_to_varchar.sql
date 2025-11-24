-- Convert einheit typ column from PostgreSQL enum to VARCHAR
ALTER TABLE zev.einheit ALTER COLUMN typ TYPE VARCHAR(20) USING typ::text;

-- Drop the enum type with CASCADE
DROP TYPE IF EXISTS zev.einheit_typ CASCADE;
