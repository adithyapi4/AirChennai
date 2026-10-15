-- One row maps one passenger to one segment of the booking itinerary.
-- It preserves the seat number even after operational flight_seat inventory is removed.
CREATE TABLE booking_segment_passenger (
    booking_segment_passenger_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    booking_id BIGINT NOT NULL,
    booking_passenger_id BIGINT NOT NULL,
    booking_segment_id BIGINT NOT NULL,
    flight_seat_id BIGINT,

    seat_number VARCHAR(5) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'HELD',

    CONSTRAINT fk_bsp_passenger_booking
        FOREIGN KEY (booking_passenger_id, booking_id)
        REFERENCES booking_passenger(booking_passenger_id, booking_id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_bsp_segment_booking
        FOREIGN KEY (booking_segment_id, booking_id)
        REFERENCES booking_segment(booking_segment_id, booking_id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_bsp_flight_seat
        FOREIGN KEY (flight_seat_id)
        REFERENCES flight_seat(flight_seat_id)
        ON DELETE SET NULL,

    CONSTRAINT chk_bsp_status
        CHECK (status IN (
            'HELD', 'ISSUED', 'CHECKED_IN', 'BOARDED', 'FLOWN',
            'CANCELLED', 'REFUNDED', 'EXPIRED'
        ))
);

-- One active passenger mapping per segment. A cancelled/refunded/expired mapping
-- can remain for history while a replacement mapping is created if necessary.
CREATE UNIQUE INDEX uq_bsp_active_passenger_segment
    ON booking_segment_passenger(booking_passenger_id, booking_segment_id)
    WHERE status NOT IN ('CANCELLED', 'REFUNDED', 'EXPIRED');

-- A live seat cannot be assigned to multiple active passenger-segment rows.
CREATE UNIQUE INDEX uq_bsp_active_flight_seat
    ON booking_segment_passenger(flight_seat_id)
    WHERE flight_seat_id IS NOT NULL
      AND status NOT IN ('CANCELLED', 'REFUNDED', 'EXPIRED');

CREATE INDEX idx_bsp_booking_id
    ON booking_segment_passenger(booking_id);

CREATE INDEX idx_bsp_booking_segment_id
    ON booking_segment_passenger(booking_segment_id);

CREATE INDEX idx_bsp_flight_seat_id
    ON booking_segment_passenger(flight_seat_id);

COMMENT ON COLUMN booking_segment_passenger.seat_number IS
    'Assigned seat label retained as history if flight_seat_id is set to NULL after completed-flight inventory archival.';
