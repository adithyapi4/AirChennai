-- AirChennai Flyway migration V2
-- Creates directional direct routes between airports.
-- The airport table must already exist (V1__create_airport.sql).

CREATE TABLE route (
    route_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    origin_code CHAR(3) NOT NULL,
    destination_code CHAR(3) NOT NULL,

    distance_km INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_route_origin
        FOREIGN KEY (origin_code)
        REFERENCES airport(code)
        ON DELETE RESTRICT,

    CONSTRAINT fk_route_destination
        FOREIGN KEY (destination_code)
        REFERENCES airport(code)
        ON DELETE RESTRICT,

    CONSTRAINT uq_route_origin_destination
        UNIQUE (origin_code, destination_code),

    CONSTRAINT chk_route_different_airports
        CHECK (origin_code <> destination_code),

    CONSTRAINT chk_route_positive_distance
        CHECK (distance_km > 0)
);

-- Supports lookups of routes arriving at a given airport.
CREATE INDEX idx_route_destination_code ON route(destination_code);
