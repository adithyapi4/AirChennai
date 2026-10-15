CREATE TABLE employee (
    employee_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    employee_code VARCHAR(20) NOT NULL UNIQUE,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,

    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    phone_no VARCHAR(10) NOT NULL,

    department VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    base_airport_code CHAR(3),
    hired_on DATE NOT NULL,
    created_by BIGINT,
    last_login_at TIMESTAMPTZ,

    CONSTRAINT chk_employee_department
        CHECK (department IN ('COUNTER', 'OPERATIONS', 'FLEET', 'HR')),

    CONSTRAINT chk_employee_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'TERMINATED')),

    CONSTRAINT fk_employee_base_airport
        FOREIGN KEY (base_airport_code)
        REFERENCES airport(code)
        ON DELETE RESTRICT,

    CONSTRAINT fk_employee_created_by
        FOREIGN KEY (created_by)
        REFERENCES employee(employee_id)
        ON DELETE SET NULL
);

CREATE INDEX idx_employee_department
    ON employee(department);

CREATE INDEX idx_employee_status
    ON employee(status);
