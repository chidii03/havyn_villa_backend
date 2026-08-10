CREATE SEQUENCE booking_reference_sequence START WITH 1 INCREMENT BY 1;

ALTER TABLE booking
    ADD COLUMN reference_id varchar(20);

CREATE UNIQUE INDEX uq_booking_reference_id
    ON booking (reference_id)
    WHERE reference_id IS NOT NULL;
