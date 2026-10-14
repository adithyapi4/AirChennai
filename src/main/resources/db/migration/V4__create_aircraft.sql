CREATE TABLE aircraft (
    aircraft_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    registration_number VARCHAR(10) NOT NULL UNIQUE,
    msn_number VARCHAR(30) NOT NULL UNIQUE,

    aircraft_type_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    manufactured_year SMALLINT NOT NULL,

    CONSTRAINT fk_aircraft_type
        FOREIGN KEY (aircraft_type_id)
        REFERENCES aircraft_type(aircraft_type_id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_aircraft_status
        CHECK (status IN (
            'ACTIVE',
            'MAINTENANCE',
            'GROUNDED',
            'RETIRED'
        )),

    CONSTRAINT chk_aircraft_manufactured_year
        CHECK (manufactured_year BETWEEN 1900 AND 2100)
);
