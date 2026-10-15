CREATE TABLE booking_segment (
    booking_segment_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    flight_instance_id BIGINT NOT NULL,

    segment_order SMALLINT NOT NULL,
    cabin_class VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'HELD',

    CONSTRAINT fk_booking_segment_booking
        FOREIGN KEY (booking_id)
        REFERENCES booking(booking_id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_booking_segment_flight_instance
        FOREIGN KEY (flight_instance_id)
        REFERENCES flight_instance(flight_instance_id)
        ON DELETE RESTRICT,

    CONSTRAINT uq_booking_segment_order
        UNIQUE (booking_id, segment_order),

    -- Supports a composite FK that verifies the segment belongs to the same booking.
    CONSTRAINT uq_booking_segment_id_booking
        UNIQUE (booking_segment_id, booking_id),

    CONSTRAINT chk_booking_segment_order
        CHECK (segment_order > 0),

    CONSTRAINT chk_booking_segment_cabin
        CHECK (cabin_class IN ('ECONOMY', 'PREMIUM_ECONOMY', 'BUSINESS', 'FIRST')),

    CONSTRAINT chk_booking_segment_status
        CHECK (status IN ('HELD', 'CONFIRMED', 'CANCELLED', 'COMPLETED', 'EXPIRED'))
);

CREATE INDEX idx_booking_segment_flight_instance_id
    ON booking_segment(flight_instance_id);
