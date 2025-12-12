-- V19: Add mietername and messpunkt columns to einheit table for invoice generation
ALTER TABLE zev.einheit ADD COLUMN mietername VARCHAR(100);
ALTER TABLE zev.einheit ADD COLUMN messpunkt VARCHAR(50);

COMMENT ON COLUMN zev.einheit.mietername IS 'Name of the tenant for invoice address';
COMMENT ON COLUMN zev.einheit.messpunkt IS 'Metering point ID (e.g., CH1012501234500000000011000006457)';
