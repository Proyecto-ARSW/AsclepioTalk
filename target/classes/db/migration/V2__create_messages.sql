-- Mensajes de cada conversación
CREATE TABLE messages (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id   UUID        NOT NULL REFERENCES conversations(id),
    sender_id         UUID        NOT NULL,
    sender_name       VARCHAR(200) NOT NULL,
    content_original  TEXT        NOT NULL,
    content_display   TEXT        NOT NULL,
    auto_censored     BOOLEAN     NOT NULL DEFAULT FALSE,
    manually_censored BOOLEAN     NOT NULL DEFAULT FALSE,
    censored_by       UUID,
    censored_by_name  VARCHAR(200),
    censored_at       TIMESTAMP,
    edited            BOOLEAN     NOT NULL DEFAULT FALSE,
    edited_at         TIMESTAMP,
    deleted           BOOLEAN     NOT NULL DEFAULT FALSE,
    deleted_by        UUID,
    deleted_at        TIMESTAMP,
    created_at        TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_messages_conversation ON messages(conversation_id, created_at DESC);
CREATE INDEX idx_messages_sender       ON messages(sender_id);

-- Daniel Useche
