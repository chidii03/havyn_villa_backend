-- V2__users.sql
-- Accounts, profiles, and RBAC role assignment. No fake/business data — this only
-- creates structure; rows are created at runtime through the auth API.

CREATE TABLE app_user (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email             varchar(255) NOT NULL UNIQUE,
    password_hash     varchar(255) NOT NULL,
    status            varchar(30) NOT NULL DEFAULT 'PENDING_VERIFICATION'
                          CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED')),
    email_verified_at timestamptz,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE profile (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid NOT NULL UNIQUE REFERENCES app_user (id) ON DELETE CASCADE,
    full_name  varchar(120) NOT NULL,
    phone      varchar(30),
    avatar_url text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

-- RBAC assignment (Guest = unauthenticated, so there's no row for it).
CREATE TABLE user_role (
    user_id uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    role_id uuid NOT NULL REFERENCES role (id) ON DELETE RESTRICT,
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX idx_user_role_user_id ON user_role (user_id);
