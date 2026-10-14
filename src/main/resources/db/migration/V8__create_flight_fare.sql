CREATE TABLE flight_fare (
    flight_fare_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    flight_instance_id BIGINT NOT NULL,
    cabin_class VARCHAR(20) NOT NULL,

    base_fare NUMERIC(12,2) NOT NULL,
    fare_multiplier NUMERIC(5,2) NOT NULL DEFAULT 1.00,
    tax_percent NUMERIC(5,2) NOT NULL DEFAULT 0.00,
    currency CHAR(3) NOT NULL DEFAULT 'INR',

    CONSTRAINT fk_flight_fare_instance
        FOREIGN KEY (flight_instance_id)
        REFERENCES flight_instance(flight_instance_id)
        ON DELETE RESTRICT,

    CONSTRAINT uq_flight_fare_instance_cabin
        UNIQUE (flight_instance_id, cabin_class),

    CONSTRAINT chk_flight_fare_cabin
        CHECK (cabin_class IN (
            'ECONOMY',
            'PREMIUM_ECONOMY',
            'BUSINESS',
            'FIRST'
        )),

    CONSTRAINT chk_flight_fare_base
        CHECK (base_fare > 0),

    CONSTRAINT chk_flight_fare_multiplier
        CHECK (fare_multiplier >= 1.00),

    CONSTRAINT chk_flight_fare_tax_percent
        CHECK (tax_percent BETWEEN 0 AND 100)
);

CREATE INDEX idx_flight_fare_flight_instance_id
    ON flight_fare(flight_instance_id);
