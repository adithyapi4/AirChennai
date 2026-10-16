CREATE TABLE booking_segment (
    booking_segment_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    flight_instance_id BIGINT NOT NULL,

    segment_order SMALLINT NOT NULL,
    cabin_class VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'HELD',

    CONSTRAINT fk_booking_segment_booking
        FOREIGN KEY (booking_id)
        REFERENCES public.booking(booking_id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_booking_segment_flight_instance
        FOREIGN KEY (flight_instance_id)
        REFERENCES public.flight_instance(flight_instance_id)
        ON DELETE RESTRICT,

    CONSTRAINT uq_booking_segment_order
        UNIQUE (booking_id, segment_order),

    -- A booking cannot contain the same flight instance more than once.
    CONSTRAINT uq_booking_segment_booking_instance
        UNIQUE (booking_id, flight_instance_id),

    -- Required target key for the FK below, which also validates cabin class.
    CONSTRAINT uq_booking_segment_booking_instance_cabin
        UNIQUE (booking_id, flight_instance_id, cabin_class),

    CONSTRAINT chk_booking_segment_order
        CHECK (segment_order > 0),

    CONSTRAINT chk_booking_segment_cabin
        CHECK (cabin_class IN ('ECONOMY', 'PREMIUM_ECONOMY', 'BUSINESS', 'FIRST')),

    CONSTRAINT chk_booking_segment_status
        CHECK (status IN ('HELD', 'CONFIRMED', 'CANCELLED', 'COMPLETED', 'EXPIRED'))
);

CREATE INDEX idx_booking_segment_flight_instance_id
    ON booking_segment(flight_instance_id);

-- A specifically held/booked seat must belong to a flight instance and cabin
-- included in that booking. Available/blocked seats have booking_id NULL, so
-- they do not reference a booking segment.
ALTER TABLE public.flight_seat
    ADD CONSTRAINT fk_flight_seat_booking_segment_cabin
    FOREIGN KEY (booking_id, flight_instance_id, cabin_class)
    REFERENCES booking_segment(booking_id, flight_instance_id, cabin_class)
    ON DELETE RESTRICT;
