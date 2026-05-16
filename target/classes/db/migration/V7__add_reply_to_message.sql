-- Reply / quote: campo opcional que apunta al mensaje al que se está respondiendo.
-- ON DELETE SET NULL → si el mensaje original se elimina (físicamente; el soft-delete
-- no dispara la FK), las respuestas se conservan pero pierden el ancla.
ALTER TABLE talk.messages
    ADD COLUMN reply_to_message_id UUID NULL
        REFERENCES talk.messages(id) ON DELETE SET NULL;

-- Útil para "mostrar todas las respuestas a este mensaje" en futuras vistas.
CREATE INDEX idx_messages_reply_to ON talk.messages(reply_to_message_id);

-- Daniel Useche
