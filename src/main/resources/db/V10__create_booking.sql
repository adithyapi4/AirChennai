CREATE TABLE booking (
    booking_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    contact_email VARCHAR(254) NOT NULL,
    contact_phone VARCHAR(20),

    trip_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'HELD',

    total_amount NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    currency CHAR(3) NOT NULL DEFAULT 'INR',

    hold_expires_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_booking_trip_type
        CHECK (trip_type IN ('ONE_WAY', 'ROUND_TRIP', 'MULTI_CITY')),

    CONSTRAINT chk_booking_status
        CHECK (status IN ('HELD', 'CONFIRMED', 'CANCELLED', 'COMPLETED', 'EXPIRED')),

    CONSTRAINT chk_booking_total_amount
        CHECK (total_amount >= 0),

    CONSTRAINT chk_booking_cancelled_at
        CHECK (
            (status = 'CANCELLED' AND cancelled_at IS NOT NULL)
            OR
            (status <> 'CANCELLED' AND cancelled_at IS NULL)
        ),

    CONSTRAINT chk_booking_hold_expiry
        CHECK (
            (status = 'HELD' AND hold_expires_at IS NOT NULL)
            OR
            (status <> 'HELD' AND hold_expires_at IS NULL)
        )
);

-- V9 uses booking_id (not held_by_booking_id) for a specific seat hold/assignment.
ALTER TABLE public.flight_seat
    ADD CONSTRAINT fk_flight_seat_booking
    FOREIGN KEY (booking_id)
    REFERENCES booking(booking_id)
    ON DELETE RESTRICT;

CREATE INDEX idx_booking_status_hold_expires_at
    ON booking(status, hold_expires_at);

COMMENT ON TABLE booking IS
    'Guest reservation parent record. One booking may contain multiple passengers and flight segments. The booking-level PNR is stored in ticket and is created only after successful payment.';

COMMENT ON COLUMN booking.updated_at IS
    'Must be refreshed by the application or an update trigger whenever the booking changes.';
