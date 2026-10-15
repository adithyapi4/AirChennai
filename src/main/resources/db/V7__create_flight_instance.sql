-- AirChennai Flyway migration V7
-- Creates one record for each dated departure generated from a recurring flight schedule.
-- Requires V3 (aircraft_type), V4 (aircraft), and V6 (flight).

CREATE TABLE flight_instance (
    flight_instance_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    flight_id BIGINT NOT NULL,
    flight_date DATE NOT NULL,

    -- Snapshot of the aircraft type for this departure. The trigger below
    -- copies it from flight when the instance is created.
    aircraft_type_id BIGINT NOT NULL,

    -- The individual aircraft can be assigned later.
    aircraft_id BIGINT,

    scheduled_departure_at TIMESTAMPTZ NOT NULL,
    scheduled_arrival_at TIMESTAMPTZ NOT NULL,

    -- Initialized to the scheduled times unless the instance is marked DELAYED.
    estimated_departure_at TIMESTAMPTZ NOT NULL,
    estimated_arrival_at TIMESTAMPTZ NOT NULL,

    actual_departure_at TIMESTAMPTZ,
    actual_arrival_at TIMESTAMPTZ,

    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    terminal VARCHAR(20),
    gate VARCHAR(20),

    CONSTRAINT fk_flight_instance_flight
        FOREIGN KEY (flight_id)
        REFERENCES flight(flight_id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_flight_instance_aircraft_type
        FOREIGN KEY (aircraft_type_id)
        REFERENCES aircraft_type(aircraft_type_id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_flight_instance_aircraft
        FOREIGN KEY (aircraft_id)
        REFERENCES aircraft(aircraft_id)
        ON DELETE RESTRICT,

    -- A recurring schedule can generate at most one instance on a given local departure date.
    CONSTRAINT uq_flight_instance_flight_date
        UNIQUE (flight_id, flight_date),

    CONSTRAINT chk_flight_instance_status
        CHECK (status IN (
            'SCHEDULED',
            'DELAYED',
            'BOARDING',
            'DEPARTED',
            'ARRIVED',
            'COMPLETED',
            'CANCELLED',
            'DIVERTED'
        )),

    CONSTRAINT chk_flight_instance_scheduled_times
        CHECK (scheduled_arrival_at > scheduled_departure_at),

    CONSTRAINT chk_flight_instance_estimated_times
        CHECK (estimated_arrival_at > estimated_departure_at),

    -- Before departure, scheduled estimates must match unless the flight is DELAYED.
    -- Once the flight moves past pre-departure statuses, keep estimates as historical estimates.
    CONSTRAINT chk_flight_instance_estimates
        CHECK (
            (
                status = 'DELAYED'
                AND estimated_departure_at > scheduled_departure_at
            )
            OR (
                status IN ('SCHEDULED', 'BOARDING')
                AND estimated_departure_at = scheduled_departure_at
                AND estimated_arrival_at = scheduled_arrival_at
            )
            OR status IN ('DEPARTED', 'ARRIVED', 'COMPLETED', 'CANCELLED', 'DIVERTED')
        ),

    CONSTRAINT chk_flight_instance_actual_times
        CHECK (
            actual_arrival_at IS NULL
            OR (
                actual_departure_at IS NOT NULL
                AND actual_arrival_at >= actual_departure_at
            )
        )
);

-- Copies the recurring schedule's aircraft type into the instance on creation.
-- The type remains a snapshot if the recurring schedule is changed later.
-- Also prevents assigning an individual aircraft of a different type.
-- For SCHEDULED/BOARDING instances, estimates are kept equal to the scheduled times.
CREATE OR REPLACE FUNCTION airchennai_prepare_flight_instance()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_flight_aircraft_type_id BIGINT;
    v_assigned_aircraft_type_id BIGINT;
BEGIN
    IF TG_OP = 'INSERT' THEN
        SELECT f.aircraft_type_id
          INTO v_flight_aircraft_type_id
          FROM flight f
         WHERE f.flight_id = NEW.flight_id;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'Cannot create flight instance: flight_id % does not exist', NEW.flight_id;
        END IF;

        NEW.aircraft_type_id := v_flight_aircraft_type_id;
    ELSIF NEW.flight_id IS DISTINCT FROM OLD.flight_id THEN
        SELECT f.aircraft_type_id
          INTO v_flight_aircraft_type_id
          FROM flight f
         WHERE f.flight_id = NEW.flight_id;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'Cannot change flight instance: flight_id % does not exist', NEW.flight_id;
        END IF;

        NEW.aircraft_type_id := v_flight_aircraft_type_id;
    ELSE
        -- Keep the type snapshot stable; don't let ordinary updates silently change it.
        NEW.aircraft_type_id := OLD.aircraft_type_id;
    END IF;

    IF NEW.aircraft_id IS NOT NULL THEN
        SELECT a.aircraft_type_id
          INTO v_assigned_aircraft_type_id
          FROM aircraft a
         WHERE a.aircraft_id = NEW.aircraft_id;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'Cannot assign aircraft_id %: aircraft does not exist', NEW.aircraft_id;
        END IF;

        IF v_assigned_aircraft_type_id <> NEW.aircraft_type_id THEN
            RAISE EXCEPTION
                'Aircraft type mismatch: flight instance requires aircraft_type_id %, but aircraft_id % has type %',
                NEW.aircraft_type_id, NEW.aircraft_id, v_assigned_aircraft_type_id;
        END IF;
    END IF;

    IF NEW.status IN ('SCHEDULED', 'BOARDING') THEN
        NEW.estimated_departure_at := NEW.scheduled_departure_at;
        NEW.estimated_arrival_at := NEW.scheduled_arrival_at;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_flight_instance_prepare
BEFORE INSERT OR UPDATE ON flight_instance
FOR EACH ROW
EXECUTE FUNCTION airchennai_prepare_flight_instance();

CREATE INDEX idx_flight_instance_flight_date
    ON flight_instance(flight_date);

CREATE INDEX idx_flight_instance_aircraft_id
    ON flight_instance(aircraft_id);

COMMENT ON COLUMN flight_instance.flight_date IS
    'Scheduled departure date in the origin airport local calendar; the initial AirChennai network uses IST.';

COMMENT ON COLUMN flight_instance.aircraft_type_id IS
    'Snapshot of the required aircraft type copied from flight when this instance is created; used to retrieve the seat layout.';

COMMENT ON COLUMN flight_instance.scheduled_departure_at IS
    'Scheduled departure instant in UTC, calculated from the recurring schedule and flight_date.';

COMMENT ON COLUMN flight_instance.scheduled_arrival_at IS
    'Scheduled arrival instant in UTC, calculated from the recurring schedule, arrival_day_offset, and flight_date.';

COMMENT ON COLUMN flight_instance.estimated_departure_at IS
    'Current estimated departure instant in UTC; initially equals scheduled_departure_at.';

COMMENT ON COLUMN flight_instance.estimated_arrival_at IS
    'Current estimated arrival instant in UTC; initially equals scheduled_arrival_at.';
