-- Lista de palabras censuradas
CREATE TABLE censored_words (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    word          VARCHAR(100) NOT NULL UNIQUE,
    added_by      UUID        NOT NULL,
    added_by_name VARCHAR(200),
    is_active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP   NOT NULL DEFAULT now()
);

-- Semilla inicial de palabras básicas
INSERT INTO censored_words (word, added_by, added_by_name) VALUES
    ('idiota',      '00000000-0000-0000-0000-000000000000', 'Sistema'),
    ('maldito',     '00000000-0000-0000-0000-000000000000', 'Sistema'),
    ('maldita',     '00000000-0000-0000-0000-000000000000', 'Sistema'),
    ('estupido',    '00000000-0000-0000-0000-000000000000', 'Sistema'),
    ('estupida',    '00000000-0000-0000-0000-000000000000', 'Sistema'),
    ('imbecil',     '00000000-0000-0000-0000-000000000000', 'Sistema'),
    ('pendejo',     '00000000-0000-0000-0000-000000000000', 'Sistema'),
    ('pendeja',     '00000000-0000-0000-0000-000000000000', 'Sistema'),
    ('mierda',      '00000000-0000-0000-0000-000000000000', 'Sistema'),
    ('puta',        '00000000-0000-0000-0000-000000000000', 'Sistema'),
    ('puto',        '00000000-0000-0000-0000-000000000000', 'Sistema'),
    ('hijueputa',   '00000000-0000-0000-0000-000000000000', 'Sistema'),
    ('gonorrea',    '00000000-0000-0000-0000-000000000000', 'Sistema'),
    ('malparido',   '00000000-0000-0000-0000-000000000000', 'Sistema'),
    ('malparida',   '00000000-0000-0000-0000-000000000000', 'Sistema');

-- Daniel Useche
