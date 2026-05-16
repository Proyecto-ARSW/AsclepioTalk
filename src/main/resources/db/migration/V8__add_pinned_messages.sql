-- Pinned messages: flag inline en la tabla messages. Más simple que una
-- tabla aparte porque solo necesitamos "está fijado o no" y quién lo fijó.
ALTER TABLE talk.messages
    ADD COLUMN pinned    BOOLEAN   NOT NULL DEFAULT FALSE,
    ADD COLUMN pinned_by UUID      NULL,
    ADD COLUMN pinned_at TIMESTAMP NULL;

-- Índice parcial: solo indexa las filas con pinned = TRUE.
-- Como el 99.9% de los mensajes no estarán fijados, esto mantiene el índice
-- pequeño y la consulta "fijados de esta conversación" sigue siendo O(log n)
-- sobre un n minúsculo.
CREATE INDEX idx_messages_pinned
    ON talk.messages(conversation_id, pinned_at DESC)
    WHERE pinned = TRUE;

-- Daniel Useche
