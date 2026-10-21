-- AirChennai Flyway migration V19
-- Lets a customer hold a specific seat while choosing a seat / paying, BEFORE a booking row exists
-- (booking and booking_passenger need e-mail and passenger identity data that is collected later).
--
-- A hold made from the seat map is a HELD flight_seat row with:
--   booking_id / booking_passenger_id = NULL, selection_token = the browser session, held_until = expiry.
-- When the booking is created, the booking service fills in booking_id / booking_passenger_id and
-- clears selection_token; on payment the status becomes BOOKED.

ALTER TABLE flight_seat
    ADD COLUMN selection_token VARCHAR(36);

ALTER TABLE flight_seat
    DROP CONSTRAINT chk_flight_seat_assignment;

ALTER TABLE flight_seat
    ADD CONSTRAINT chk_flight_seat_assignment
    CHECK (
        -- HELD for a booking passenger
        (
            status = 'HELD'
            AND booking_id IS NOT NULL
            AND booking_passenger_id IS NOT NULL
            AND held_until IS NOT NULL
            AND selection_token IS NULL
        )
        -- HELD from the seat map (no booking yet)
        OR (
            status = 'HELD'
            AND booking_id IS NULL
            AND booking_passenger_id IS NULL
            AND held_until IS NOT NULL
            AND selection_token IS NOT NULL
        )
        OR (
            status = 'BOOKED'
            AND booking_id IS NOT NULL
            AND booking_passenger_id IS NOT NULL
            AND held_until IS NULL
            AND selection_token IS NULL
        )
        OR (
            status IN ('AVAILABLE', 'BLOCKED')
            AND booking_id IS NULL
            AND booking_passenger_id IS NULL
            AND held_until IS NULL
            AND selection_token IS NULL
        )
    );

-- Supports "which seat is this browser session holding on this flight?".
CREATE INDEX idx_flight_seat_selection_token
    ON flight_seat (flight_instance_id, selection_token)
    WHERE selection_token IS NOT NULL;

COMMENT ON COLUMN flight_seat.selection_token IS
    'Random id of the browser session that is holding this seat from the seat map before a booking exists. NULL once a booking owns the seat.';
