-- Adds the first-login password-change flag to existing employee tables.
-- V17 is reserved for flight_instance_cabin_inventory in the current plan.
-- Existing employee rows receive FALSE; new rows also default to FALSE.

ALTER TABLE employee
    ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN employee.must_change_password IS
    'When TRUE, the employee must change their initial or reset password before accessing normal application features.';
