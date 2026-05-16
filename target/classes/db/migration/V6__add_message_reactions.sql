-- Reacciones emoji por mensaje. La UNIQUE (message_id, user_id, emoji)
-- impide que un mismo usuario añada el mismo emoji dos veces al mismo
-- mensaje (toggle natural) y deja libre a diferentes usuarios reaccionar
-- con el mismo emoji.
CREATE TABLE talk.message_reactions (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id UUID         NOT NULL REFERENCES talk.messages(id) ON DELETE CASCADE,
    user_id    UUID         NOT NULL,
    user_name  VARCHAR(200) NOT NULL,
    emoji      VARCHAR(16)  NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    UNIQUE (message_id, user_id, emoji)
);

-- Lookup principal: traer todas las reacciones de un set de mensajes en lote
-- al armar el listado de la conversación (evita N+1).
CREATE INDEX idx_reactions_message ON talk.message_reactions(message_id);

-- Daniel Useche
