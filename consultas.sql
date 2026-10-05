-- =========================
-- Consultas do sistema
-- Só leem dados (SELECT): não alteram o banco.
-- Para rodar uma só no pgAdmin, selecione o texto dela e aperte o play.
-- =========================

-- 1) Farmácias que têm TODOS os remédios da receita (busca por id)
-- Exemplo: Losartana (1) e Hidroclorotiazida (2)
SELECT f.nome, f.endereco
FROM Farmacia f
JOIN Estoque e ON e.farmacia_id = f.id
WHERE e.medicamento_id IN (1, 2)
  AND e.quantidade > 0
GROUP BY f.id, f.nome, f.endereco
HAVING COUNT(DISTINCT e.medicamento_id) = 2;

-- 2) Mesma busca, mas pelo nome digitado pelo usuário
SELECT f.nome, f.endereco
FROM Farmacia f
JOIN Estoque e ON e.farmacia_id = f.id
JOIN Medicamento m ON m.id = e.medicamento_id
WHERE e.quantidade > 0
  AND (m.nome ILIKE '%losartana%' OR m.nome ILIKE '%hidroclorotiazida%')
GROUP BY f.id, f.nome, f.endereco
HAVING COUNT(DISTINCT m.id) = 2;

-- 3) Estoque completo, com nomes legíveis (farmácia, endereço, remédio)
SELECT f.nome AS farmacia,
       f.endereco,
       m.nome AS medicamento,
       e.quantidade,
       e.atualizado_em
FROM Estoque e
JOIN Farmacia f ON f.id = e.farmacia_id
JOIN Medicamento m ON m.id = e.medicamento_id
ORDER BY f.nome, m.nome;

-- 4) BUSCA PRINCIPAL DO APP: farmácias que têm TODOS os remédios, da mais perto para a mais longe
-- Posição da Maria (fictícia): -23.5010, -47.4500 (no app vem do GPS do celular)
-- Exemplo com 2 remédios. Com N remédios, a API troca o 2 do HAVING por N.
SELECT f.nome, f.endereco,
       COUNT(DISTINCT m.id) AS encontrados,
       ROUND((6371 * ACOS(LEAST(1,
           COS(RADIANS(-23.5010)) * COS(RADIANS(f.latitude)) *
           COS(RADIANS(f.longitude) - RADIANS(-47.4500)) +
           SIN(RADIANS(-23.5010)) * SIN(RADIANS(f.latitude))
       )))::numeric, 2) AS distancia_km
FROM Farmacia f
JOIN Estoque e ON e.farmacia_id = f.id
JOIN Medicamento m ON m.id = e.medicamento_id
WHERE e.quantidade > 0
  AND m.nome ILIKE ANY (ARRAY['%losartana%', '%hidroclorotiazida%'])
GROUP BY f.id, f.nome, f.endereco, f.latitude, f.longitude
HAVING COUNT(DISTINCT m.id) = 2
ORDER BY distancia_km;

-- 5) PLANO B: se nenhuma farmácia tem tudo, mostra quem tem mais remédios da lista
-- (empate desempatado pela distância). Exemplo com 3 remédios.
SELECT f.nome, f.endereco,
       COUNT(DISTINCT m.id) AS encontrados,
       ROUND((6371 * ACOS(LEAST(1,
           COS(RADIANS(-23.5010)) * COS(RADIANS(f.latitude)) *
           COS(RADIANS(f.longitude) - RADIANS(-47.4500)) +
           SIN(RADIANS(-23.5010)) * SIN(RADIANS(f.latitude))
       )))::numeric, 2) AS distancia_km
FROM Farmacia f
JOIN Estoque e ON e.farmacia_id = f.id
JOIN Medicamento m ON m.id = e.medicamento_id
WHERE e.quantidade > 0
  AND m.nome ILIKE ANY (ARRAY['%losartana%', '%sinvastatina%', '%cloridrato%'])
GROUP BY f.id, f.nome, f.endereco, f.latitude, f.longitude
ORDER BY encontrados DESC, distancia_km;

-- 6) Agendamento completo, com nomes legíveis
SELECT u.nome AS usuario,
       f.nome AS farmacia,
       f.endereco,
       a.data_hora,
       a.status,
       m.nome AS medicamento,
       i.quantidade
FROM Agendamento a
JOIN Usuario u ON u.id = a.usuario_id
JOIN Farmacia f ON f.id = a.farmacia_id
JOIN Agendamento_Item i ON i.agendamento_id = a.id
JOIN Medicamento m ON m.id = i.medicamento_id
ORDER BY a.data_hora, m.nome;