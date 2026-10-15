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
        REFERENCES booking(booking_id)
        ON DELETE RESTRICT,

    -- Supports a composite FK that verifies the passenger belongs to the same booking.
    CONSTRAINT uq_booking_passenger_id_booking
        UNIQUE (booking_passenger_id, booking_id),

    CONSTRAINT chk_booking_passenger_gender
        CHECK (gender IN ('M', 'F')),

    CONSTRAINT chk_booking_passenger_type
        CHECK (passenger_type IN ('ADULT', 'CHILD', 'INFANT'))
);

CREATE INDEX idx_booking_passenger_booking_id
    ON booking_passenger(booking_id);

COMMENT ON COLUMN booking_passenger.aadhaar_number_encrypted IS
    'Application-encrypted Aadhaar ciphertext stored as bytes. Use synthetic test data in demos; never store plaintext Aadhaar.';
