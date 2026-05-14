-- Conversaciones (individuales o grupales)
CREATE TABLE conversations (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    type        VARCHAR(10) NOT NULL CHECK (type IN ('INDIVIDUAL', 'GROUP')),
    name        VARCHAR(200),
    description TEXT,
    hospital_id INT         NOT NULL,
    created_by  UUID        NOT NULL,
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_conversations_hospital ON conversations(hospital_id, is_active);
CREATE INDEX idx_conversations_creator  ON conversations(created_by);

-- Participantes de cada conversación
CREATE TABLE conversation_participants (
    conversation_id UUID        NOT NULL REFERENCES conversations(id),
    user_id         UUID        NOT NULL,
    user_name       VARCHAR(200) NOT NULL,
    user_rol        VARCHAR(30)  NOT NULL,
    joined_at       TIMESTAMP    NOT NULL DEFAULT now(),
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    PRIMARY KEY (conversation_id, user_id)
);

CREATE INDEX idx_participants_user ON conversation_participants(user_id, is_active);

-- Daniel Useche
