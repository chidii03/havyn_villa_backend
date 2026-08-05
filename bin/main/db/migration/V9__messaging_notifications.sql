-- V9__messaging_notifications.sql
-- Messaging & notifications (prompt 16). Conversations are one thread per
-- (property, guest) pair, optionally linked to a real booking once one exists;
-- notifications are a channel-agnostic (in-app + email) record subscribing to domain
-- events published elsewhere (booking confirmed/cancelled, message sent).

-- ---------------------------------------------------------------------------
-- conversation — host_id/guest_id are plain columns, no FK (mirrors booking.guest_id/
-- property_id: a conversation should stay a readable historical record even if the
-- referenced property/profile later changes). booking_id IS a real, nullable FK
-- (ON DELETE SET NULL, not CASCADE — losing the booking link shouldn't delete the
-- conversation itself, unlike message.conversation_id below).
-- ---------------------------------------------------------------------------
CREATE TABLE conversation (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id     uuid NOT NULL,
    host_id         uuid NOT NULL,
    guest_id        uuid NOT NULL,
    booking_id      uuid REFERENCES booking (id) ON DELETE SET NULL,
    last_message_at timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (property_id, guest_id)
);

CREATE INDEX idx_conversation_host_id ON conversation (host_id, last_message_at DESC);
CREATE INDEX idx_conversation_guest_id ON conversation (guest_id, last_message_at DESC);

-- ---------------------------------------------------------------------------
-- message — conversation_id has a real FK, ON DELETE CASCADE (a message is meaningless
-- without its conversation, same category as property_media.property_id). sender_id is
-- a plain column, same historical-record reasoning as conversation.host_id/guest_id.
-- ---------------------------------------------------------------------------
CREATE TABLE message (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id uuid NOT NULL REFERENCES conversation (id) ON DELETE CASCADE,
    sender_id       uuid NOT NULL,
    body            text NOT NULL,
    read_at         timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_message_conversation_id ON message (conversation_id, created_at ASC);

-- ---------------------------------------------------------------------------
-- notification — user_id has a real FK, ON DELETE CASCADE (deleting a user removes
-- their notifications, same reasoning as favorite.user_id). link_id is a plain,
-- untyped UUID (e.g. a bookingId or conversationId) for client-side deep-linking —
-- deliberately not jsonb; see database/01-data-model.md's session 7 notes on why
-- this schema doesn't introduce jsonb for a single column ahead of a real convention.
-- ---------------------------------------------------------------------------
CREATE TABLE notification (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    type       varchar(30) NOT NULL CHECK (type IN ('BOOKING_CONFIRMED', 'BOOKING_CANCELLED', 'MESSAGE_RECEIVED')),
    title      varchar(200) NOT NULL,
    body       text NOT NULL,
    link_id    uuid,
    read_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_notification_user_id ON notification (user_id, created_at DESC);
CREATE INDEX idx_notification_user_unread ON notification (user_id) WHERE read_at IS NULL;
