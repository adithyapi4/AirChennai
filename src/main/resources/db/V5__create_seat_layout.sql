-- V5: Physical seat blueprint for each aircraft type.
-- Each row represents one physical seat. The cabin capacities are stored in
-- aircraft_type; this migration verifies that the blueprint matches those
-- capacities exactly, both overall and for each cabin class.

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
        REFERENCES public.aircraft_type (aircraft_type_id)
        ON DELETE RESTRICT,

    -- A seat's column position must be unique within a row and aircraft type.
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

CREATE INDEX idx_seat_layout_type_cabin
    ON public.seat_layout (aircraft_type_id, cabin_class);

-- Assert that the physical layout matches the capacity configured on
-- aircraft_type: total seats and the number of seats in every cabin class.
-- This is called by deferred constraint triggers so a complete layout can be
-- inserted/updated in one transaction and checked at transaction commit.
CREATE OR REPLACE FUNCTION public.assert_aircraft_type_seat_count(
    p_aircraft_type_id BIGINT
)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    expected_total INTEGER;
    expected_economy INTEGER;
    expected_premium_economy INTEGER;
    expected_business INTEGER;
    expected_first INTEGER;

    actual_total BIGINT;
    actual_economy BIGINT;
    actual_premium_economy BIGINT;
    actual_business BIGINT;
    actual_first BIGINT;
BEGIN
    SELECT
        at.total_seats,
        at.economy_seats,
        at.premium_economy_seats,
        at.business_seats,
        at.first_seats
    INTO
        expected_total,
        expected_economy,
        expected_premium_economy,
        expected_business,
        expected_first
    FROM public.aircraft_type at
    WHERE at.aircraft_type_id = p_aircraft_type_id;

    -- The type may have been deleted in this transaction after its layout was
    -- removed. The foreign key prevents any orphaned layout rows from committing.
    IF NOT FOUND THEN
        RETURN;
    END IF;

    SELECT
        COUNT(*),
        COUNT(*) FILTER (WHERE sl.cabin_class = 'ECONOMY'),
        COUNT(*) FILTER (WHERE sl.cabin_class = 'PREMIUM_ECONOMY'),
        COUNT(*) FILTER (WHERE sl.cabin_class = 'BUSINESS'),
        COUNT(*) FILTER (WHERE sl.cabin_class = 'FIRST')
    INTO
        actual_total,
        actual_economy,
        actual_premium_economy,
        actual_business,
        actual_first
    FROM public.seat_layout sl
    WHERE sl.aircraft_type_id = p_aircraft_type_id;

    IF actual_total <> expected_total
       OR actual_economy <> expected_economy
       OR actual_premium_economy <> expected_premium_economy
       OR actual_business <> expected_business
       OR actual_first <> expected_first THEN
        RAISE EXCEPTION
            'Seat layout mismatch for aircraft_type_id %. Expected total/economy/premium_economy/business/first = %/%/%/%/%, actual = %/%/%/%/%.',
            p_aircraft_type_id,
            expected_total,
            expected_economy,
            expected_premium_economy,
            expected_business,
            expected_first,
            actual_total,
            actual_economy,
            actual_premium_economy,
            actual_business,
            actual_first
            USING ERRCODE = '23514';
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.enforce_aircraft_type_seat_count()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_TABLE_NAME = 'aircraft_type' THEN
        -- This trigger is installed for INSERT/UPDATE on aircraft_type.
        PERFORM public.assert_aircraft_type_seat_count(NEW.aircraft_type_id);
    ELSIF TG_OP = 'INSERT' THEN
        PERFORM public.assert_aircraft_type_seat_count(NEW.aircraft_type_id);
    ELSIF TG_OP = 'DELETE' THEN
        PERFORM public.assert_aircraft_type_seat_count(OLD.aircraft_type_id);
    ELSE
        -- UPDATE may move a seat between aircraft types, so validate both types.
        PERFORM public.assert_aircraft_type_seat_count(NEW.aircraft_type_id);

        IF OLD.aircraft_type_id IS DISTINCT FROM NEW.aircraft_type_id THEN
            PERFORM public.assert_aircraft_type_seat_count(OLD.aircraft_type_id);
        END IF;
    END IF;

    -- AFTER triggers ignore the return value.
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_aircraft_type_seat_count
AFTER INSERT OR UPDATE ON public.aircraft_type
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION public.enforce_aircraft_type_seat_count();

CREATE CONSTRAINT TRIGGER trg_seat_layout_seat_count
AFTER INSERT OR UPDATE OR DELETE ON public.seat_layout
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION public.enforce_aircraft_type_seat_count();

COMMENT ON TABLE public.seat_layout IS
    'Physical seat blueprint for each aircraft type; per-cabin counts must match aircraft_type cabin capacities.';

COMMENT ON COLUMN public.seat_layout.cabin_class IS
    'Cabin class of this physical seat; its count per aircraft type must match the corresponding aircraft_type capacity.';
