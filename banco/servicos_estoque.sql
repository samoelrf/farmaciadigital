-- Serviços gratuitos da farmácia e estoque por lote (validade, quantidade, preço).
-- Rode depois de login.sql (e de demo.sql, se for usar). Pode rodar mais de uma vez.

-- ----- serviços gratuitos -----
CREATE TABLE IF NOT EXISTS servico_gratuito (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome VARCHAR(80) NOT NULL UNIQUE,
    descricao VARCHAR(300)
);

INSERT INTO servico_gratuito (nome, descricao) VALUES
    ('Aferição de pressão arterial', 'Medida da pressão com aparelho digital.'),
    ('Teste de glicemia capilar', 'Medida do açúcar no sangue por furo no dedo.'),
    ('Teste de hemoglobina', 'Medida da hemoglobina no sangue.'),
    ('Teste de colesterol', 'Medida do colesterol por furo no dedo.'),
    ('Peso, altura e IMC', 'Pesagem, medida de altura e cálculo do IMC.'),
    ('Temperatura e saturação de oxigênio', 'Medida da temperatura e da oximetria.'),
    ('Orientação farmacêutica', 'Conversa com o farmacêutico sobre o uso dos remédios.'),
    ('Descarte de medicamentos vencidos', 'Recebimento de remédios vencidos para descarte correto.')
ON CONFLICT (nome) DO NOTHING;

-- servico_id preenchido = serviço do catálogo; nome_personalizado preenchido = serviço que a farmácia digitou
CREATE TABLE IF NOT EXISTS farmacia_servico (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    farmacia_id INT NOT NULL REFERENCES farmacia(id) ON DELETE CASCADE,
    servico_id INT REFERENCES servico_gratuito(id),
    nome_personalizado VARCHAR(80),
    dias_horarios VARCHAR(200),
    exige_agendamento BOOLEAN NOT NULL DEFAULT FALSE,
    observacao VARCHAR(300),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    atualizado_em TIMESTAMP NOT NULL DEFAULT now(),
    CHECK ((servico_id IS NULL) <> (nome_personalizado IS NULL))
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_farmacia_servico_catalogo
    ON farmacia_servico (farmacia_id, servico_id) WHERE servico_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS ux_farmacia_servico_proprio
    ON farmacia_servico (farmacia_id, lower(nome_personalizado)) WHERE nome_personalizado IS NOT NULL;

-- ----- remédios cadastrados pela própria farmácia -----
CREATE SEQUENCE IF NOT EXISTS medicamento_id_seq;
SELECT setval('medicamento_id_seq', COALESCE((SELECT MAX(id) FROM medicamento), 0) + 1, false);
ALTER TABLE medicamento ALTER COLUMN id SET DEFAULT nextval('medicamento_id_seq');

ALTER TABLE medicamento ADD COLUMN IF NOT EXISTS apresentacao VARCHAR(100);
ALTER TABLE medicamento ADD COLUMN IF NOT EXISTS tipo VARCHAR(12);
ALTER TABLE medicamento ADD COLUMN IF NOT EXISTS exige_receita BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE medicamento ADD COLUMN IF NOT EXISTS criado_por_farmacia_id INT REFERENCES farmacia(id) ON DELETE SET NULL;
ALTER TABLE medicamento DROP CONSTRAINT IF EXISTS medicamento_tipo_check;
ALTER TABLE medicamento ADD CONSTRAINT medicamento_tipo_check CHECK (tipo IN ('referencia', 'generico', 'similar'));

-- ----- estoque por lote -----
CREATE TABLE IF NOT EXISTS estoque_lote (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    farmacia_id INT NOT NULL REFERENCES farmacia(id) ON DELETE CASCADE,
    medicamento_id INT NOT NULL REFERENCES medicamento(id),
    lote VARCHAR(40) NOT NULL,
    validade DATE NOT NULL,
    quantidade INT NOT NULL CHECK (quantidade >= 0),
    preco_custo NUMERIC(10,2) CHECK (preco_custo >= 0),
    preco_venda NUMERIC(10,2) NOT NULL CHECK (preco_venda >= 0),
    fornecedor VARCHAR(100),
    entrada_em DATE NOT NULL DEFAULT current_date,
    atualizado_em TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (farmacia_id, medicamento_id, lote)
);

-- alerta de reposição: o painel avisa quando a quantidade válida fica abaixo disto
ALTER TABLE estoque ADD COLUMN IF NOT EXISTS estoque_minimo INT NOT NULL DEFAULT 0;
ALTER TABLE estoque DROP CONSTRAINT IF EXISTS estoque_minimo_check;
ALTER TABLE estoque ADD CONSTRAINT estoque_minimo_check CHECK (estoque_minimo >= 0);
