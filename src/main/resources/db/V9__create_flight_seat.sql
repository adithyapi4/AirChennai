-- One row per physical seat on a specific flight instance.
-- Rows are generated from seat_layout for the aircraft type used by the instance.
-- Passengers who skip seat selection do not claim a flight_seat row until check-in;
-- their cabin capacity reservation is tracked separately in flight-instance cabin inventory.

CREATE TABLE flight_seat (
    flight_seat_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    flight_instance_id BIGINT NOT NULL,
    seat_number VARCHAR(6) NOT NULL,
    cabin_class VARCHAR(20) NOT NULL,

    seat_fee NUMERIC(8,2) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',

    -- Set while a specific seat is held or booked for a passenger.
    -- Foreign keys are added by later migrations because booking and booking_passenger
    -- are created after this migration.
    booking_id BIGINT,
    booking_passenger_id BIGINT,
    held_until TIMESTAMPTZ,

    CONSTRAINT fk_flight_seat_flight_instance
        FOREIGN KEY (flight_instance_id)
        REFERENCES public.flight_instance(flight_instance_id)
        ON DELETE RESTRICT,

    CONSTRAINT uq_flight_seat_instance_number
        UNIQUE (flight_instance_id, seat_number),

    -- A passenger cannot occupy two seats on the same flight instance.
    CONSTRAINT uq_flight_seat_instance_passenger
        UNIQUE (flight_instance_id, booking_passenger_id),

    CONSTRAINT chk_flight_seat_cabin_class
        CHECK (cabin_class IN (
            'ECONOMY',
            'PREMIUM_ECONOMY',
            'BUSINESS',
            'FIRST'
        )),

    CONSTRAINT chk_flight_seat_fee
        CHECK (seat_fee >= 0),

    CONSTRAINT chk_flight_seat_status
        CHECK (status IN ('AVAILABLE', 'HELD', 'BOOKED', 'BLOCKED')),

    -- HELD: a specific seat is temporarily held for one booking passenger.
    -- BOOKED: payment/confirmation succeeded, or a seat was assigned at check-in.
    -- AVAILABLE/BLOCKED: no booking or passenger is associated with the seat.
    CONSTRAINT chk_flight_seat_assignment
        CHECK (
            (
                status = 'HELD'
                AND booking_id IS NOT NULL
                AND booking_passenger_id IS NOT NULL
                AND held_until IS NOT NULL
            )
            OR (
                status = 'BOOKED'
                AND booking_id IS NOT NULL
                AND booking_passenger_id IS NOT NULL
                AND held_until IS NULL
            )
            OR (
                status IN ('AVAILABLE', 'BLOCKED')
                AND booking_id IS NULL
                AND booking_passenger_id IS NULL
                AND held_until IS NULL
            )
        )
);

-- Supports finding an available seat in a particular cabin for a flight instance.
CREATE INDEX idx_flight_seat_instance_cabin_status
    ON flight_seat (flight_instance_id, cabin_class, status);

-- Supports retrieving seats associated with a booking.
CREATE INDEX idx_flight_seat_booking_id
    ON flight_seat (booking_id)
    WHERE booking_id IS NOT NULL;

COMMENT ON COLUMN flight_seat.cabin_class IS
    'Cabin class copied from seat_layout when physical seat inventory is generated for this flight instance.';

COMMENT ON COLUMN flight_seat.booking_id IS
    'Booking associated with a specifically selected or assigned seat; the foreign key is added in the booking migration.';

COMMENT ON COLUMN flight_seat.booking_passenger_id IS
    'Passenger assigned to this physical seat; the passenger/booking consistency foreign key is added in a later migration.';

COMMENT ON COLUMN flight_seat.held_until IS
    'Expiry instant for a HELD seat; null after the seat is booked or made available again.';
