-- Adjuntos de mensajes (imagenes y PDFs hoy; extensible a otros tipos).
-- 1-N para no contaminar `messages` y dejar abierta la extension futura,
-- aunque el service valida 1 maximo por mensaje en esta version.
CREATE TABLE message_attachments (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id    UUID         NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    storage_key   VARCHAR(512) NOT NULL,
    file_name     VARCHAR(255) NOT NULL,
    mime_type     VARCHAR(127) NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    uploaded_by   UUID         NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_message_attachments_message ON message_attachments(message_id);

-- Daniel Useche
