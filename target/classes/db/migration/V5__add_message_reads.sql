-- Read receipts: registro de qué usuario leyó qué mensaje y cuándo.
-- PK compuesta (message_id, user_id) hace la inserción idempotente —
-- intentar marcar dos veces el mismo mensaje no inserta una fila duplicada.
CREATE TABLE talk.message_reads (
    message_id UUID      NOT NULL REFERENCES talk.messages(id) ON DELETE CASCADE,
    user_id    UUID      NOT NULL,
    read_at    TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (message_id, user_id)
);

-- Acelera "¿qué mensajes ha leído este usuario?" (no usaríamos hoy, pero futuro-friendly).
CREATE INDEX idx_message_reads_user ON talk.message_reads(user_id);

-- Daniel Useche
