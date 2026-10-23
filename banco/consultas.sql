
SELECT f.nome, f.endereco
FROM farmacia f
JOIN estoque e ON e.farmacia_id = f.id
WHERE e.medicamento_id IN (1, 2)
  AND e.quantidade > 0
GROUP BY f.id, f.nome, f.endereco
HAVING COUNT(DISTINCT e.medicamento_id) = 2;

SELECT f.nome, f.endereco
FROM farmacia f
JOIN estoque e ON e.farmacia_id = f.id
JOIN medicamento m ON m.id = e.medicamento_id
WHERE e.quantidade > 0
  AND (m.nome ILIKE '%losartana%' OR m.nome ILIKE '%hidroclorotiazida%')
GROUP BY f.id, f.nome, f.endereco
HAVING COUNT(DISTINCT m.id) = 2;

SELECT f.nome AS farmacia,
       f.endereco,
       m.nome AS medicamento,
       e.quantidade,
       e.atualizado_em
FROM estoque e
JOIN farmacia f ON f.id = e.farmacia_id
JOIN medicamento m ON m.id = e.medicamento_id
ORDER BY f.nome, m.nome;

SELECT f.nome, f.endereco,
       COUNT(DISTINCT m.id) AS encontrados,
       ROUND((6371 * ACOS(LEAST(1,
           COS(RADIANS(-23.5010)) * COS(RADIANS(f.latitude)) *
           COS(RADIANS(f.longitude) - RADIANS(-47.4500)) +
           SIN(RADIANS(-23.5010)) * SIN(RADIANS(f.latitude))
       )))::numeric, 2) AS distancia_km
FROM farmacia f
JOIN estoque e ON e.farmacia_id = f.id
JOIN medicamento m ON m.id = e.medicamento_id
WHERE e.quantidade > 0
  AND m.nome ILIKE ANY (ARRAY['%losartana%', '%hidroclorotiazida%'])
GROUP BY f.id, f.nome, f.endereco, f.latitude, f.longitude
HAVING COUNT(DISTINCT m.id) = 2
ORDER BY distancia_km;

SELECT f.nome, f.endereco,
       COUNT(DISTINCT m.id) AS encontrados,
       ROUND((6371 * ACOS(LEAST(1,
           COS(RADIANS(-23.5010)) * COS(RADIANS(f.latitude)) *
           COS(RADIANS(f.longitude) - RADIANS(-47.4500)) +
           SIN(RADIANS(-23.5010)) * SIN(RADIANS(f.latitude))
       )))::numeric, 2) AS distancia_km
FROM farmacia f
JOIN estoque e ON e.farmacia_id = f.id
JOIN medicamento m ON m.id = e.medicamento_id
WHERE e.quantidade > 0
  AND m.nome ILIKE ANY (ARRAY['%losartana%', '%sinvastatina%', '%cloridrato%'])
GROUP BY f.id, f.nome, f.endereco, f.latitude, f.longitude
ORDER BY encontrados DESC, distancia_km;

SELECT u.nome AS usuario,
       f.nome AS farmacia,
       f.endereco,
       a.data_hora,
       a.status,
       m.nome AS medicamento,
       i.quantidade
FROM agendamento a
JOIN usuario u ON u.id = a.usuario_id
JOIN farmacia f ON f.id = a.farmacia_id
JOIN agendamento_item i ON i.agendamento_id = a.id
JOIN medicamento m ON m.id = i.medicamento_id
ORDER BY a.data_hora, m.nome;