-- rode depois de tabela.sql, farmacias_reais.sql e ubs.sql

-- a senha nunca fica guardada: só o hash e o sal
CREATE TABLE IF NOT EXISTS conta (
    id              INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome            VARCHAR(100) NOT NULL,
    email           VARCHAR(150) NOT NULL UNIQUE,
    senha_hash      VARCHAR(200) NOT NULL,
    senha_sal       VARCHAR(64) NOT NULL,
    tipo            VARCHAR(10) NOT NULL CHECK (tipo IN ('cidadao', 'farmacia', 'admin')),
    criado_em       TIMESTAMP NOT NULL DEFAULT now(),
    aceite_lgpd_em  TIMESTAMP
);

CREATE TABLE IF NOT EXISTS acesso_farmacia (
    id                  INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conta_id            INTEGER NOT NULL REFERENCES conta(id) ON DELETE CASCADE,
    farmacia_id         INTEGER REFERENCES farmacia(id),
    cnpj_informado      VARCHAR(14),
    telefone_informado  VARCHAR(30),
    nome_informado      VARCHAR(100),
    endereco_informado  VARCHAR(200),
    status              VARCHAR(10) NOT NULL DEFAULT 'pendente' CHECK (status IN ('pendente', 'aprovado', 'recusado')),
    criado_em           TIMESTAMP NOT NULL DEFAULT now(),
    decidido_em         TIMESTAMP,
    decidido_por        INTEGER REFERENCES conta(id)
);

CREATE TABLE IF NOT EXISTS favorito_farmacia (
    conta_id    INTEGER NOT NULL REFERENCES conta(id) ON DELETE CASCADE,
    farmacia_id INTEGER NOT NULL REFERENCES farmacia(id) ON DELETE CASCADE,
    PRIMARY KEY (conta_id, farmacia_id)
);

CREATE TABLE IF NOT EXISTS favorito_medicamento (
    conta_id       INTEGER NOT NULL REFERENCES conta(id) ON DELETE CASCADE,
    medicamento_id INTEGER NOT NULL REFERENCES medicamento(id) ON DELETE CASCADE,
    PRIMARY KEY (conta_id, medicamento_id)
);

CREATE TABLE IF NOT EXISTS favorito_ubs (
    conta_id INTEGER NOT NULL REFERENCES conta(id) ON DELETE CASCADE,
    ubs_id   INTEGER NOT NULL REFERENCES ubs(id) ON DELETE CASCADE,
    PRIMARY KEY (conta_id, ubs_id)
);

ALTER TABLE preco ADD COLUMN IF NOT EXISTS informado_por_conta_id INTEGER REFERENCES conta(id) ON DELETE SET NULL;

CREATE SEQUENCE IF NOT EXISTS estoque_id_seq;
SELECT setval('estoque_id_seq', COALESCE((SELECT MAX(id) FROM estoque), 0) + 1, false);
ALTER TABLE estoque ALTER COLUMN id SET DEFAULT nextval('estoque_id_seq');

CREATE UNIQUE INDEX IF NOT EXISTS acesso_pendente_unico ON acesso_farmacia (conta_id) WHERE status = 'pendente';

CREATE TABLE IF NOT EXISTS redefinicao_senha (
    id           INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conta_id     INTEGER NOT NULL REFERENCES conta(id) ON DELETE CASCADE,
    codigo_hash  VARCHAR(64) NOT NULL UNIQUE,
    expira_em    TIMESTAMP NOT NULL,
    usado_em     TIMESTAMP
);

ALTER TABLE farmacia ALTER COLUMN latitude DROP NOT NULL;
ALTER TABLE farmacia ALTER COLUMN longitude DROP NOT NULL;

CREATE SEQUENCE IF NOT EXISTS farmacia_id_seq;
SELECT setval('farmacia_id_seq', COALESCE((SELECT MAX(id) FROM farmacia), 0) + 1, false);
ALTER TABLE farmacia ALTER COLUMN id SET DEFAULT nextval('farmacia_id_seq');

ALTER TABLE acesso_farmacia DROP CONSTRAINT IF EXISTS acesso_farmacia_decidido_por_fkey;
ALTER TABLE acesso_farmacia ADD CONSTRAINT acesso_farmacia_decidido_por_fkey
    FOREIGN KEY (decidido_por) REFERENCES conta(id) ON DELETE SET NULL;

ALTER TABLE acesso_farmacia ADD COLUMN IF NOT EXISTS cidade_informada VARCHAR(20);
