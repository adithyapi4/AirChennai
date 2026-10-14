CREATE TABLE seat_layout (
    aircraft_type_id BIGINT NOT NULL,
    row_number SMALLINT NOT NULL,
    seat_letter CHAR(1) NOT NULL,
    column_position SMALLINT NOT NULL,

    cabin_class VARCHAR(20) NOT NULL,
    seat_position VARCHAR(10) NOT NULL,

    is_exit_row BOOLEAN NOT NULL DEFAULT FALSE,
    extra_legroom BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT pk_seat_layout
        PRIMARY KEY (aircraft_type_id, row_number, seat_letter),

    CONSTRAINT fk_seat_layout_aircraft_type
        FOREIGN KEY (aircraft_type_id)
        REFERENCES aircraft_type(aircraft_type_id)
        ON DELETE RESTRICT,

    CONSTRAINT uq_seat_layout_column
        UNIQUE (aircraft_type_id, row_number, column_position),

    CONSTRAINT chk_seat_layout_row
        CHECK (row_number > 0),

    CONSTRAINT chk_seat_layout_column
        CHECK (column_position > 0),

    CONSTRAINT chk_seat_layout_seat_letter
        CHECK (seat_letter ~ '^[A-Z]$'),

    CONSTRAINT chk_seat_layout_cabin_class
        CHECK (cabin_class IN (
            'ECONOMY',
            'PREMIUM_ECONOMY',
            'BUSINESS',
            'FIRST'
        )),

    CONSTRAINT chk_seat_layout_position
        CHECK (seat_position IN (
            'WINDOW',
            'MIDDLE',
            'AISLE'
        ))
);

-- Validate the final seat count at transaction commit. The constraint triggers
-- are deferred so all seat-layout rows for an aircraft type can be inserted in
-- one transaction before the count is checked.
CREATE OR REPLACE FUNCTION assert_aircraft_type_seat_count(
    p_aircraft_type_id BIGINT
)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    expected_seats INTEGER;
    actual_seats BIGINT;
BEGIN
    SELECT at.total_seats
      INTO expected_seats
      FROM aircraft_type at
     WHERE at.aircraft_type_id = p_aircraft_type_id;

    -- The type may have been removed in the same transaction after its layout
    -- was removed. The foreign key prevents orphaned layout rows from committing.
    IF NOT FOUND THEN
        RETURN;
    END IF;

    SELECT COUNT(*)
      INTO actual_seats
      FROM seat_layout sl
     WHERE sl.aircraft_type_id = p_aircraft_type_id;

    IF actual_seats <> expected_seats THEN
        RAISE EXCEPTION
            'Aircraft type % specifies % seats, but seat_layout contains % seat records.',
            p_aircraft_type_id, expected_seats, actual_seats
            USING ERRCODE = '23514';
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION enforce_aircraft_type_seat_count()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_TABLE_NAME = 'aircraft_type' THEN
        PERFORM assert_aircraft_type_seat_count(NEW.aircraft_type_id);
    ELSIF TG_OP = 'INSERT' THEN
        PERFORM assert_aircraft_type_seat_count(NEW.aircraft_type_id);
    ELSIF TG_OP = 'DELETE' THEN
        PERFORM assert_aircraft_type_seat_count(OLD.aircraft_type_id);
    ELSE
        PERFORM assert_aircraft_type_seat_count(NEW.aircraft_type_id);

        IF OLD.aircraft_type_id IS DISTINCT FROM NEW.aircraft_type_id THEN
            PERFORM assert_aircraft_type_seat_count(OLD.aircraft_type_id);
        END IF;
    END IF;

    -- AFTER triggers ignore the return value.
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_aircraft_type_seat_count
AFTER INSERT OR UPDATE ON aircraft_type
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION enforce_aircraft_type_seat_count();

CREATE CONSTRAINT TRIGGER trg_seat_layout_seat_count
AFTER INSERT OR UPDATE OR DELETE ON seat_layout
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION enforce_aircraft_type_seat_count();
