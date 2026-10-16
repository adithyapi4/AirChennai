CREATE TABLE booking_passenger (
    booking_passenger_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id BIGINT NOT NULL,

    title VARCHAR(10),
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    date_of_birth DATE NOT NULL,
    gender CHAR(1) NOT NULL,
    passenger_type VARCHAR(10) NOT NULL,
    nationality VARCHAR(100),
    aadhaar_number_encrypted BYTEA NOT NULL,

    CONSTRAINT fk_booking_passenger_booking
        FOREIGN KEY (booking_id)
        REFERENCES public.booking(booking_id)
        ON DELETE RESTRICT,

    -- Composite key for validating that a seat's passenger belongs to its booking.
    CONSTRAINT uq_booking_passenger_id_booking
        UNIQUE (booking_passenger_id, booking_id),

    CONSTRAINT chk_booking_passenger_gender
        CHECK (gender IN ('M', 'F')),

    CONSTRAINT chk_booking_passenger_type
        CHECK (passenger_type IN ('ADULT', 'CHILD', 'INFANT'))
);

CREATE INDEX idx_booking_passenger_booking_id
    ON booking_passenger(booking_id);

-- A held/booked physical seat must reference a passenger belonging to that same booking.
-- AVAILABLE/BLOCKED seats have both columns NULL, so the composite FK is not applied to them.
ALTER TABLE public.flight_seat
    ADD CONSTRAINT fk_flight_seat_passenger_booking
    FOREIGN KEY (booking_passenger_id, booking_id)
    REFERENCES booking_passenger(booking_passenger_id, booking_id)
    ON DELETE RESTRICT;

COMMENT ON COLUMN booking_passenger.aadhaar_number_encrypted IS
    'Application-encrypted Aadhaar ciphertext stored as bytes. Use synthetic test data in demos; never store plaintext Aadhaar.';
