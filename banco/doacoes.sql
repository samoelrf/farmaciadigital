-- =========================
-- Doação de medicamentos
-- Rode depois de servicos_estoque.sql e cadastro_farmacia.sql (pode ser rodado de novo).
-- Doador e quem precisa são contas (tabela conta). A farmácia confere a doação antes de repassar.
-- =========================

-- Doação: uma conta oferece um medicamento que tem em casa, entregue em uma farmácia.
-- 'pendente' até a farmácia conferir lacre, validade e embalagem.
CREATE TABLE IF NOT EXISTS doacao (
    id                  INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conta_id            INTEGER NOT NULL REFERENCES conta(id) ON DELETE CASCADE,
    farmacia_id         INT NOT NULL REFERENCES farmacia(id) ON DELETE CASCADE,
    medicamento_id      INT NOT NULL REFERENCES medicamento(id),
    quantidade_inicial  INT NOT NULL CHECK (quantidade_inicial > 0),
    quantidade_restante INT NOT NULL CHECK (quantidade_restante >= 0),
    validade            DATE NOT NULL,
    status              VARCHAR(12) NOT NULL DEFAULT 'pendente'
                        CHECK (status IN ('pendente', 'disponivel', 'esgotada', 'recusada')),
    criado_em           TIMESTAMP NOT NULL DEFAULT now(),
    decidido_em         TIMESTAMP,
    decidido_por        INTEGER REFERENCES conta(id) ON DELETE SET NULL,
    CHECK (quantidade_restante <= quantidade_inicial)
);

-- Pedido: uma conta precisa de um medicamento e não tem como pagar.
CREATE TABLE IF NOT EXISTS necessidade (
    id                  INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conta_id            INTEGER NOT NULL REFERENCES conta(id) ON DELETE CASCADE,
    medicamento_id      INT NOT NULL REFERENCES medicamento(id),
    quantidade          INT NOT NULL CHECK (quantidade > 0),
    quantidade_restante INT NOT NULL CHECK (quantidade_restante >= 0),
    status              VARCHAR(10) NOT NULL DEFAULT 'aberta'
                        CHECK (status IN ('aberta', 'atendida', 'cancelada')),
    criado_em           TIMESTAMP NOT NULL DEFAULT now(),
    CHECK (quantidade_restante <= quantidade)
);

-- Atendimento: parte de uma doação entregue para um pedido.
-- Cada INSERT aqui passa pelo trigger de validação e depois dá a baixa sozinho.
CREATE TABLE IF NOT EXISTS atendimento_doacao (
    id              INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    necessidade_id  INT NOT NULL REFERENCES necessidade(id) ON DELETE CASCADE,
    doacao_id       INT NOT NULL REFERENCES doacao(id) ON DELETE CASCADE,
    farmacia_id     INT NOT NULL REFERENCES farmacia(id) ON DELETE CASCADE,
    quantidade      INT NOT NULL CHECK (quantidade > 0),
    data_hora       TIMESTAMP NOT NULL DEFAULT now()
);

-- Validação: medicamento igual, mesma farmácia, doação disponível e não vencida, pedido aberto.
CREATE OR REPLACE FUNCTION fn_valida_atendimento_doacao() RETURNS trigger AS $$
DECLARE
    d doacao%ROWTYPE;
    n necessidade%ROWTYPE;
BEGIN
    SELECT * INTO d FROM doacao WHERE id = NEW.doacao_id;
    SELECT * INTO n FROM necessidade WHERE id = NEW.necessidade_id;

    IF d.medicamento_id <> n.medicamento_id THEN
        RAISE EXCEPTION 'O medicamento da doação não é o que foi pedido';
    END IF;
    IF d.farmacia_id <> NEW.farmacia_id THEN
        RAISE EXCEPTION 'Esta doação não está nesta farmácia';
    END IF;
    IF d.status <> 'disponivel' OR d.validade < CURRENT_DATE THEN
        RAISE EXCEPTION 'Doação indisponível ou vencida';
    END IF;
    IF n.status <> 'aberta' THEN
        RAISE EXCEPTION 'Este pedido já foi atendido ou cancelado';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_valida_atendimento_doacao ON atendimento_doacao;
CREATE TRIGGER trg_valida_atendimento_doacao
BEFORE INSERT ON atendimento_doacao
FOR EACH ROW EXECUTE FUNCTION fn_valida_atendimento_doacao();

-- Baixa: desconta da doação e do pedido; muda o status quando zera.
-- Se a quantidade ficar negativa, o CHECK barra a operação.
CREATE OR REPLACE FUNCTION fn_baixa_doacao() RETURNS trigger AS $$
BEGIN
    UPDATE doacao
       SET quantidade_restante = quantidade_restante - NEW.quantidade,
           status = CASE WHEN quantidade_restante - NEW.quantidade = 0 THEN 'esgotada' ELSE status END
     WHERE id = NEW.doacao_id;

    UPDATE necessidade
       SET quantidade_restante = quantidade_restante - NEW.quantidade,
           status = CASE WHEN quantidade_restante - NEW.quantidade = 0 THEN 'atendida' ELSE status END
     WHERE id = NEW.necessidade_id;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_baixa_doacao ON atendimento_doacao;
CREATE TRIGGER trg_baixa_doacao
AFTER INSERT ON atendimento_doacao
FOR EACH ROW EXECUTE FUNCTION fn_baixa_doacao();

CREATE INDEX IF NOT EXISTS idx_doacao_farmacia_medicamento ON doacao (farmacia_id, medicamento_id, status);
CREATE INDEX IF NOT EXISTS idx_necessidade_medicamento ON necessidade (medicamento_id, status);
