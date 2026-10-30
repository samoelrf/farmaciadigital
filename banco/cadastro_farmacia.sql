-- Cadastro de farmácia com aprovação do administrador.
-- Rode depois de login.sql e servicos_estoque.sql (e de demo.sql, se for usar). Pode rodar mais de uma vez.
--
-- A farmácia que se cadastra ganha o registro na hora, com situacao = 'pendente': ela já pode lançar
-- estoque e serviços no painel, mas NÃO aparece no mapa, na busca de preços, nos favoritos nem nos
-- serviços públicos até o administrador aprovar (situacao = 'aprovada'). Recusada ou revogada = 'recusada'.
-- Farmácias que já existiam ficam 'aprovada' (padrão da coluna).

ALTER TABLE farmacia ADD COLUMN IF NOT EXISTS situacao VARCHAR(10) NOT NULL DEFAULT 'aprovada';
ALTER TABLE farmacia DROP CONSTRAINT IF EXISTS farmacia_situacao_check;
ALTER TABLE farmacia ADD CONSTRAINT farmacia_situacao_check CHECK (situacao IN ('pendente', 'aprovada', 'recusada'));

-- cadastro_proprio: a farmácia nasceu de um cadastro feito por uma conta (e não da base pública de farmácias)
ALTER TABLE farmacia ADD COLUMN IF NOT EXISTS cadastro_proprio BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE farmacia ADD COLUMN IF NOT EXISTS cadastrado_por_conta_id INT REFERENCES conta(id) ON DELETE SET NULL;

ALTER TABLE farmacia ADD COLUMN IF NOT EXISTS bairro VARCHAR(80);
ALTER TABLE farmacia ADD COLUMN IF NOT EXISTS cep VARCHAR(8);
