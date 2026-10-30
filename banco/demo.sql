-- dados de demonstração: não rode em banco real, a senha das contas é conhecida
-- rode depois de login.sql (pode rodar mais de uma vez)
-- senha de todas as contas de demo: Demo@2026

INSERT INTO conta (nome, email, senha_hash, senha_sal, tipo, aceite_lgpd_em) VALUES
    ('Administrador (demonstração)', 'admin@demo.farmaciadigital.local', '00fZa9U6z8KhiE9eqhG+EHdnofTd6UpdbMguUl4EmY8=', 'Ie8226YQgj9dRoHcJj4wFw==', 'admin', NULL),
    ('Cidadão (demonstração)', 'cidadao@demo.farmaciadigital.local', '8HJeqrFbDHFR+nOpki4RvZaQUYRrH1H/w6DiLZzE73Q=', '5kqowl7vKcRVa2KjswKH2Q==', 'cidadao', now()),
    ('Farmácia (demonstração)', 'farmacia@demo.farmaciadigital.local', 'gXBcZ+4/mY/g+XIU0ndj3KGjU2E5/8dN2TFL/XWJNAw=', 'HYXbsSB3FArcWjVcWrl+xg==', 'farmacia', NULL)
ON CONFLICT DO NOTHING;

INSERT INTO medicamento (id, nome, principio_ativo) VALUES
    (6, 'Dipirona Sódica 500mg', 'Dipirona sódica'),
    (7, 'Omeprazol 20mg', 'Omeprazol'),
    (8, 'Paracetamol 750mg', 'Paracetamol'),
    (9, 'Amoxicilina 500mg', 'Amoxicilina'),
    (10, 'Ibuprofeno 400mg', 'Ibuprofeno'),
    (11, 'Captopril 25mg', 'Captopril'),
    (12, 'Atenolol 50mg', 'Atenolol'),
    (13, 'Anlodipino 5mg', 'Anlodipino'),
    (14, 'Azitromicina 500mg', 'Azitromicina'),
    (15, 'Loratadina 10mg', 'Loratadina'),
    (16, 'Salbutamol 100mcg (spray)', 'Salbutamol'),
    (17, 'Diclofenaco Sódico 50mg', 'Diclofenaco sódico'),
    (18, 'Prednisona 20mg', 'Prednisona'),
    (19, 'Fluconazol 150mg', 'Fluconazol'),
    (20, 'Metronidazol 250mg', 'Metronidazol')
ON CONFLICT DO NOTHING;

INSERT INTO estoque (id, farmacia_id, medicamento_id, quantidade, atualizado_em)
SELECT 1000 + row_number() OVER (ORDER BY f.id, m.id), f.id, m.id, (f.id * 7 + m.id * 3) % 40, now()
FROM farmacia f
CROSS JOIN medicamento m
WHERE f.cidade IS NULL AND m.id BETWEEN 6 AND 20
ON CONFLICT DO NOTHING;

INSERT INTO preco (farmacia_id, medicamento_id, preco, url_produto, atualizado_em)
SELECT e.farmacia_id, e.medicamento_id,
       (8 + ((e.farmacia_id * 13 + e.medicamento_id * 17) % 80) + 0.90)::numeric(10, 2),
       NULL, now()
FROM estoque e
JOIN farmacia f ON f.id = e.farmacia_id
WHERE f.cidade IS NULL
ON CONFLICT DO NOTHING;

INSERT INTO acesso_farmacia (conta_id, farmacia_id, cnpj_informado, telefone_informado, status, decidido_em)
SELECT c.id, 1, NULL, '(15) 99999-1111', 'aprovado', now()
FROM conta c
WHERE c.email = 'farmacia@demo.farmaciadigital.local'
  AND NOT EXISTS (SELECT 1 FROM acesso_farmacia a WHERE a.conta_id = c.id AND a.status = 'aprovado');
