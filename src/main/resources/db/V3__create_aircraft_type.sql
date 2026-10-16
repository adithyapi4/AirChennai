CREATE TABLE aircraft_type (
    aircraft_type_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    manufacturer VARCHAR(100) NOT NULL,
    model VARCHAR(100) NOT NULL,

    max_range_km INTEGER NOT NULL,

    -- Total physical seats and the number belonging to each cabin class.
    total_seats INTEGER NOT NULL,
    economy_seats INTEGER NOT NULL,
    premium_economy_seats INTEGER NOT NULL,
    business_seats INTEGER NOT NULL,
    first_seats INTEGER NOT NULL,

    active BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT uq_aircraft_type_manufacturer_model
        UNIQUE (manufacturer, model),

    CONSTRAINT chk_aircraft_type_range
        CHECK (max_range_km > 0),

    CONSTRAINT chk_aircraft_type_total_seats
        CHECK (total_seats > 0),

    CONSTRAINT chk_aircraft_type_economy_seats
        CHECK (economy_seats >= 0),

    CONSTRAINT chk_aircraft_type_premium_economy_seats
        CHECK (premium_economy_seats >= 0),

    CONSTRAINT chk_aircraft_type_business_seats
        CHECK (business_seats >= 0),

    CONSTRAINT chk_aircraft_type_first_seats
        CHECK (first_seats >= 0),

    CONSTRAINT chk_aircraft_type_cabin_seat_total
        CHECK (
            total_seats =
                economy_seats
                + premium_economy_seats
                + business_seats
                + first_seats
        )
);

COMMENT ON COLUMN aircraft_type.total_seats IS
    'Total physical seat capacity; must equal the sum of all cabin-class capacities.';

COMMENT ON COLUMN aircraft_type.economy_seats IS
    'Number of Economy seats in this aircraft configuration.';

COMMENT ON COLUMN aircraft_type.premium_economy_seats IS
    'Number of Premium Economy seats in this aircraft configuration.';

COMMENT ON COLUMN aircraft_type.business_seats IS
    'Number of Business seats in this aircraft configuration.';

COMMENT ON COLUMN aircraft_type.first_seats IS
    'Number of First Class seats in this aircraft configuration.';
