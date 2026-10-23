-- rode depois de tabela.sql e dados.sql
CREATE TABLE preco (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    farmacia_id INT NOT NULL,
    medicamento_id INT NOT NULL,
    preco DECIMAL(10,2) NOT NULL CHECK (preco >= 0),
    url_produto VARCHAR(500),
    atualizado_em TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (farmacia_id, medicamento_id),
    FOREIGN KEY (farmacia_id) REFERENCES farmacia(id),
    FOREIGN KEY (medicamento_id) REFERENCES medicamento(id)
);

UPDATE preco SET url_produto = NULL
WHERE url_produto IS NOT NULL AND url_produto !~* '^https?://';
