CREATE TABLE booking_email_log (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id          uuid NOT NULL REFERENCES booking (id) ON DELETE CASCADE,
    booking_reference_id varchar(20),
    recipient_email     varchar(255) NOT NULL,
    status              varchar(20) NOT NULL CHECK (status IN ('ATTEMPTED', 'SUCCESSFUL', 'FAILED')),
    failure_reason      text,
    retry_attempts      integer NOT NULL DEFAULT 0,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_booking_email_log_status ON booking_email_log (status, created_at DESC);
CREATE INDEX idx_booking_email_log_reference ON booking_email_log (booking_reference_id);
CREATE INDEX idx_booking_email_log_recipient ON booking_email_log (recipient_email);

CREATE TABLE support_ticket (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    booking_reference_id varchar(20),
    summary              text NOT NULL,
    source_message       text NOT NULL,
    status               varchar(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'REVIEWING', 'RESOLVED')),
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_support_ticket_status ON support_ticket (status, created_at DESC);
CREATE INDEX idx_support_ticket_user ON support_ticket (user_id, created_at DESC);
CREATE INDEX idx_support_ticket_reference ON support_ticket (booking_reference_id);
