-- One record per booking. This stores the booking-level PNR and final total,
-- not a separate ticket per passenger or flight segment.
CREATE TABLE ticket (
    ticket_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id BIGINT NOT NULL UNIQUE,
    pnr VARCHAR(9) NOT NULL UNIQUE,
    total_amount NUMERIC(12,2) NOT NULL,

    CONSTRAINT fk_ticket_booking
        FOREIGN KEY (booking_id)
        REFERENCES booking(booking_id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_ticket_pnr
        CHECK (pnr ~ '^[0-9]{9}$'),

    CONSTRAINT chk_ticket_total_amount
        CHECK (total_amount >= 0)
);

COMMENT ON TABLE ticket IS
    'One booking-level ticket record. A unique 9-digit PNR and final total are created after successful payment and booking confirmation. Currency is available from booking.currency.';
