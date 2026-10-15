CREATE TABLE payment (
    payment_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    booking_id BIGINT NOT NULL,
    amount NUMERIC(12,2) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'INR',

    payment_method VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'INITIATED',

    gateway_reference VARCHAR(100) UNIQUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at TIMESTAMPTZ,

    CONSTRAINT fk_payment_booking
        FOREIGN KEY (booking_id)
        REFERENCES booking(booking_id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_payment_amount
        CHECK (amount > 0),

    CONSTRAINT chk_payment_method
        CHECK (payment_method IN (
            'UPI',
            'CARD',
            'NET_BANKING'
        )),

    CONSTRAINT chk_payment_status
        CHECK (status IN (
            'INITIATED',
            'PENDING',
            'SUCCESS',
            'FAILED',
            'CANCELLED'
        )),

    CONSTRAINT chk_payment_paid_at
        CHECK (
            (status = 'SUCCESS' AND paid_at IS NOT NULL)
            OR
            (status <> 'SUCCESS' AND paid_at IS NULL)
        )
);

CREATE INDEX idx_payment_booking_id
    ON payment(booking_id);
