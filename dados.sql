-- =========================
-- Dados de exemplo (TODOS FICTÍCIOS)
-- Rode depois do tabela.sql, uma vez só, em banco vazio.
-- =========================

-- Farmácias fictícias (nomes genéricos, sem marcas reais)
INSERT INTO Farmacia (id, nome, endereco, latitude, longitude, hora_abertura, hora_fechamento, credenciada)
VALUES
(1, 'Farmácia Flores',       'Rua das Flores, 300',     -23.501234, -47.450987, '08:00', '22:00', TRUE),
(2, 'Farmácia Central',      'Rua Central, 100',        -23.502345, -47.451234, '07:00', '21:00', TRUE),
(3, 'Farmácia Palmeiras',    'Rua das Palmeiras, 500',  -23.503456, -47.452345, '09:00', '20:00', TRUE),
(4, 'Farmácia Brasil',       'Av. Brasil, 200',         -23.504567, -47.453456, '08:00', '18:00', TRUE),
(5, 'Farmácia Independência','Av. Independência, 400',  -23.505678, -47.454567, '10:00', '22:00', TRUE);

-- Medicamentos: nomes reais do Programa Farmácia Popular, códigos EAN FICTÍCIOS
INSERT INTO Medicamento (id, nome, principio_ativo, codigo_ean, farmacia_popular)
VALUES
(1, 'Losartana Potássica 50mg',      'Losartana',         '7891058001234', TRUE),
(2, 'Hidroclorotiazida 25mg',        'Hidroclorotiazida', '7891058005678', TRUE),
(3, 'Cloridrato de Metformina 500mg','Metformina',        '7891058009012', TRUE),
(4, 'Glibenclamida 5mg',             'Glibenclamida',     '7891058012345', TRUE),
(5, 'Sinvastatina 20mg',             'Sinvastatina',      '7891058016789', TRUE);

-- Habilitações (quais farmácias dispensam quais medicamentos)
INSERT INTO Habilitacao (id, farmacia_id, medicamento_id)
VALUES
(1, 1, 1), -- Flores habilitada para Losartana
(2, 1, 2), -- Flores habilitada para Hidroclorotiazida
(3, 2, 3), -- Central habilitada para Metformina
(4, 3, 4), -- Palmeiras habilitada para Glibenclamida
(5, 4, 5), -- Brasil habilitada para Sinvastatina
(6, 5, 1), -- Independência habilitada para Losartana
(7, 5, 3); -- Independência habilitada para Metformina

-- Estoque (atualizado_em é preenchido sozinho pelo banco)
INSERT INTO Estoque (id, farmacia_id, medicamento_id, quantidade)
VALUES
(1, 1, 1, 50),
(2, 1, 2, 30),
(3, 2, 3, 20),
(4, 3, 4, 10),
(5, 4, 5, 40),
(6, 5, 1, 25),
(7, 5, 3, 15);

-- Dispensações (retiradas de medicamentos)
INSERT INTO Dispensacao (id, farmacia_id, medicamento_id, data, quantidade)
VALUES
(1, 1, 1, '2026-10-01', 5),
(2, 1, 2, '2026-10-01', 3),
(3, 2, 3, '2026-09-30', 2),
(4, 3, 4, '2026-09-29', 1),
(5, 4, 5, '2026-09-28', 4),
(6, 5, 1, '2026-09-27', 2),
(7, 5, 3, '2026-09-27', 3);

-- Usuária de exemplo (dados fictícios). O id 1 é gerado pelo banco.
INSERT INTO Usuario (nome, telefone, email)
VALUES ('Maria', '(15) 99999-0000', 'maria@exemplo.com');

-- Agendamento da Maria na Farmácia Flores (farmácia 1)
INSERT INTO Agendamento (usuario_id, farmacia_id, data_hora)
VALUES (1, 1, '2026-10-07 09:00');

-- Remédios desse agendamento: Losartana (1) e Hidroclorotiazida (2)
INSERT INTO Agendamento_Item (agendamento_id, medicamento_id, quantidade)
VALUES (1, 1, 1),
       (1, 2, 1);