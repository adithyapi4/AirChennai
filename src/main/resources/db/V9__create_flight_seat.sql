CREATE TABLE flight_seat (
    flight_seat_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    flight_instance_id BIGINT NOT NULL,
    seat_number VARCHAR(5) NOT NULL,

    seat_fee NUMERIC(8,2) NOT NULL DEFAULT 0.00,

    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    held_until TIMESTAMPTZ,
    held_by_booking_id BIGINT,

    CONSTRAINT fk_flight_seat_instance
        FOREIGN KEY (flight_instance_id)
        REFERENCES flight_instance(flight_instance_id)
        ON DELETE RESTRICT,

    CONSTRAINT uq_flight_seat_instance_seat
        UNIQUE (flight_instance_id, seat_number),

    CONSTRAINT chk_flight_seat_status
        CHECK (status IN (
            'AVAILABLE',
            'HELD',
            'BOOKED',
            'BLOCKED'
        )),

    CONSTRAINT chk_flight_seat_fee
        CHECK (seat_fee >= 0),

    CONSTRAINT chk_flight_seat_hold
        CHECK (
            (
                status = 'HELD'
                AND held_until IS NOT NULL
                AND held_by_booking_id IS NOT NULL
            )
            OR
            (
                status <> 'HELD'
                AND held_until IS NULL
                AND held_by_booking_id IS NULL
            )
        )
);

COMMENT ON TABLE flight_seat IS
    'Seat inventory for a specific flight instance. Rows may be removed after flight completion and archival according to the retention workflow.';

COMMENT ON COLUMN flight_seat.held_by_booking_id IS
    'Identifies the pending booking that owns a temporary seat hold. Add the foreign-key constraint after the booking table is created.';
