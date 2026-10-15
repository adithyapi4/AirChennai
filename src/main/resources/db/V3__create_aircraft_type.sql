CREATE TABLE aircraft_type (
    aircraft_type_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    manufacturer VARCHAR(100) NOT NULL,
    model VARCHAR(100) NOT NULL,

    max_range_km INTEGER NOT NULL,
    total_seats INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT uq_aircraft_type_manufacturer_model
        UNIQUE (manufacturer, model),

    CONSTRAINT chk_aircraft_type_range
        CHECK (max_range_km > 0),

    CONSTRAINT chk_aircraft_type_seats
        CHECK (total_seats > 0)
);
