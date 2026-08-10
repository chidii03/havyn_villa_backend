CREATE TABLE support_chat_message (
    id         uuid PRIMARY KEY,
    user_id    uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    role       varchar(20) NOT NULL CHECK (role IN ('USER', 'ASSISTANT')),
    body       text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_support_chat_message_user_created ON support_chat_message (user_id, created_at ASC);
