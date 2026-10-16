-- Stores a per-flight-instance snapshot of seat capacity for each cabin class.
-- Capacity is initialized from aircraft_type when a flight_instance is inserted.
-- held_seats counts passengers with active checkout holds; confirmed_seats counts
-- confirmed passengers; blocked_seats counts seats withheld from sale for this flight.

CREATE TABLE flight_instance_cabin_inventory (
    flight_instance_id BIGINT NOT NULL,
    cabin_class VARCHAR(20) NOT NULL,

    total_seats INTEGER NOT NULL,
    held_seats INTEGER NOT NULL DEFAULT 0,
    confirmed_seats INTEGER NOT NULL DEFAULT 0,
    blocked_seats INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT pk_flight_instance_cabin_inventory
        PRIMARY KEY (flight_instance_id, cabin_class),

    CONSTRAINT fk_fici_flight_instance
        FOREIGN KEY (flight_instance_id)
        REFERENCES public.flight_instance(flight_instance_id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_fici_cabin_class
        CHECK (cabin_class IN (
            'ECONOMY',
            'PREMIUM_ECONOMY',
            'BUSINESS',
            'FIRST'
        )),

    CONSTRAINT chk_fici_nonnegative_counts
        CHECK (
            total_seats >= 0
            AND held_seats >= 0
            AND confirmed_seats >= 0
            AND blocked_seats >= 0
        ),

    CONSTRAINT chk_fici_capacity_not_exceeded
        CHECK (held_seats + confirmed_seats + blocked_seats <= total_seats)
);

COMMENT ON TABLE flight_instance_cabin_inventory IS
    'Per-flight-instance snapshot of cabin capacity. Capacity is initialized from aircraft_type; booking services atomically update held and confirmed counts.';

COMMENT ON COLUMN flight_instance_cabin_inventory.total_seats IS
    'Snapshot of the aircraft type capacity for this cabin when the flight instance is created.';

COMMENT ON COLUMN flight_instance_cabin_inventory.held_seats IS
    'Passenger capacity temporarily reserved during checkout, including bookings without selected physical seats.';

COMMENT ON COLUMN flight_instance_cabin_inventory.confirmed_seats IS
    'Passenger capacity held by confirmed bookings, whether or not individual seats have been assigned.';

COMMENT ON COLUMN flight_instance_cabin_inventory.blocked_seats IS
    'Physical seats removed from sale for this flight instance; keep synchronized with blocked flight_seat rows.';

-- Seed all four cabin rows whenever a flight instance is created. This copies
-- the capacities from the aircraft type and includes rows for zero-capacity cabins.
CREATE OR REPLACE FUNCTION public.initialize_flight_instance_cabin_inventory()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO flight_instance_cabin_inventory (
        flight_instance_id,
        cabin_class,
        total_seats
    )
    SELECT
        NEW.flight_instance_id,
        cabin.cabin_class,
        cabin.total_seats
    FROM public.aircraft_type AS at
    CROSS JOIN LATERAL (
        VALUES
            ('ECONOMY'::VARCHAR(20), at.economy_seats),
            ('PREMIUM_ECONOMY'::VARCHAR(20), at.premium_economy_seats),
            ('BUSINESS'::VARCHAR(20), at.business_seats),
            ('FIRST'::VARCHAR(20), at.first_seats)
    ) AS cabin(cabin_class, total_seats)
    WHERE at.aircraft_type_id = NEW.aircraft_type_id
    ON CONFLICT (flight_instance_id, cabin_class) DO NOTHING;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_initialize_flight_instance_cabin_inventory
AFTER INSERT ON public.flight_instance
FOR EACH ROW
EXECUTE FUNCTION public.initialize_flight_instance_cabin_inventory();

-- Backfill any flight instances that existed before this migration.
INSERT INTO flight_instance_cabin_inventory (
    flight_instance_id,
    cabin_class,
    total_seats
)
SELECT
    fi.flight_instance_id,
    cabin.cabin_class,
    cabin.total_seats
FROM public.flight_instance AS fi
JOIN public.aircraft_type AS at
  ON at.aircraft_type_id = fi.aircraft_type_id
CROSS JOIN LATERAL (
    VALUES
        ('ECONOMY'::VARCHAR(20), at.economy_seats),
        ('PREMIUM_ECONOMY'::VARCHAR(20), at.premium_economy_seats),
        ('BUSINESS'::VARCHAR(20), at.business_seats),
        ('FIRST'::VARCHAR(20), at.first_seats)
) AS cabin(cabin_class, total_seats)
ON CONFLICT (flight_instance_id, cabin_class) DO NOTHING;
