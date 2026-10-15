-- AirChennai Flyway migration V6
-- Creates recurring flight schedule templates.
-- V2 (route) and V3 (aircraft_type) must exist first.

CREATE TABLE flight (
    flight_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    flight_number VARCHAR(10) NOT NULL UNIQUE,
    route_id BIGINT NOT NULL,
    aircraft_type_id BIGINT NOT NULL,

    departure_time_local TIME NOT NULL,
    arrival_time_local TIME NOT NULL,
    arrival_day_offset SMALLINT NOT NULL DEFAULT 0,

    -- Bitmask: Monday=1, Tuesday=2, Wednesday=4, Thursday=8,
    -- Friday=16, Saturday=32, Sunday=64. Every day=127.
    days_of_week SMALLINT NOT NULL,

    active BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_flight_route
        FOREIGN KEY (route_id)
        REFERENCES route(route_id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_flight_aircraft_type
        FOREIGN KEY (aircraft_type_id)
        REFERENCES aircraft_type(aircraft_type_id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_flight_arrival_day_offset
        CHECK (arrival_day_offset IN (0, 1)),

    CONSTRAINT chk_flight_days_of_week
        CHECK (days_of_week BETWEEN 1 AND 127)
);

-- Supports searches for schedules using a route and checks by aircraft type.
CREATE INDEX idx_flight_route_id ON flight(route_id);

COMMENT ON COLUMN flight.days_of_week IS
    'Operating-day bitmask: Monday=1, Tuesday=2, Wednesday=4, Thursday=8, Friday=16, Saturday=32, Sunday=64.';

COMMENT ON COLUMN flight.arrival_day_offset IS
    'Number of calendar days after the departure date (in the origin airport local calendar) on which the flight arrives; for this India-only network, local airport time zones use IST.';
