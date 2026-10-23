CREATE TABLE farmacia (
    id INT PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    endereco VARCHAR(200) NOT NULL,
    latitude DECIMAL(9,6) NOT NULL,
    longitude DECIMAL(9,6) NOT NULL,
    hora_abertura TIME,
    hora_fechamento TIME,
    credenciada BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE medicamento (
    id INT PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    principio_ativo VARCHAR(100),
    codigo_ean VARCHAR(20),
    farmacia_popular BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE habilitacao (
    id INT PRIMARY KEY,
    farmacia_id INT NOT NULL,
    medicamento_id INT NOT NULL,
    UNIQUE (farmacia_id, medicamento_id),
    FOREIGN KEY (farmacia_id) REFERENCES farmacia(id),
    FOREIGN KEY (medicamento_id) REFERENCES medicamento(id)
);

CREATE TABLE estoque (
    id INT PRIMARY KEY,
    farmacia_id INT NOT NULL,
    medicamento_id INT NOT NULL,
    quantidade INT NOT NULL CHECK (quantidade >= 0),
    atualizado_em TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (farmacia_id, medicamento_id),
    FOREIGN KEY (farmacia_id) REFERENCES farmacia(id),
    FOREIGN KEY (medicamento_id) REFERENCES medicamento(id)
);

CREATE TABLE dispensacao (
    id INT PRIMARY KEY,
    farmacia_id INT NOT NULL,
    medicamento_id INT NOT NULL,
    data DATE NOT NULL,
    quantidade INT NOT NULL CHECK (quantidade > 0),
    FOREIGN KEY (farmacia_id) REFERENCES farmacia(id),
    FOREIGN KEY (medicamento_id) REFERENCES medicamento(id)
);

CREATE TABLE usuario (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    telefone VARCHAR(20) NOT NULL,
    email VARCHAR(100)
);

CREATE TABLE agendamento (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id INT NOT NULL,
    farmacia_id INT NOT NULL,
    data_hora TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'agendado',
    criado_em TIMESTAMP NOT NULL DEFAULT now(),
    CHECK (status IN ('agendado', 'retirado', 'cancelado')),
    FOREIGN KEY (usuario_id) REFERENCES usuario(id),
    FOREIGN KEY (farmacia_id) REFERENCES farmacia(id)
);

CREATE TABLE agendamento_item (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    agendamento_id INT NOT NULL,
    medicamento_id INT NOT NULL,
    quantidade INT NOT NULL CHECK (quantidade > 0),
    FOREIGN KEY (agendamento_id) REFERENCES agendamento(id),
    FOREIGN KEY (medicamento_id) REFERENCES medicamento(id)
);