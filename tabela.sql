-- =========================
-- Tabela de Farmácias
-- =========================
CREATE TABLE Farmacia (
    id INT PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    endereco VARCHAR(200) NOT NULL,
    latitude DECIMAL(9,6) NOT NULL,   -- localização para calcular distância
    longitude DECIMAL(9,6) NOT NULL,  -- localização para calcular distância
    hora_abertura TIME,               -- horário de abertura
    hora_fechamento TIME,             -- horário de fechamento
    credenciada BOOLEAN NOT NULL DEFAULT TRUE  -- se participa do programa
);

-- =========================
-- Tabela de Medicamentos
-- =========================
CREATE TABLE Medicamento (
    id INT PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    principio_ativo VARCHAR(100),
    codigo_ean VARCHAR(20),           -- códigos fictícios nos dados de exemplo
    farmacia_popular BOOLEAN NOT NULL DEFAULT TRUE
);

-- =========================
-- Tabela de Habilitação (quais farmácias dispensam quais medicamentos)
-- =========================
CREATE TABLE Habilitacao (
    id INT PRIMARY KEY,
    farmacia_id INT NOT NULL,
    medicamento_id INT NOT NULL,
    UNIQUE (farmacia_id, medicamento_id),  -- sem repetir o mesmo par
    FOREIGN KEY (farmacia_id) REFERENCES Farmacia(id),
    FOREIGN KEY (medicamento_id) REFERENCES Medicamento(id)
);

-- =========================
-- Tabela de Estoque
-- =========================
CREATE TABLE Estoque (
    id INT PRIMARY KEY,
    farmacia_id INT NOT NULL,
    medicamento_id INT NOT NULL,
    quantidade INT NOT NULL CHECK (quantidade >= 0),  -- nunca negativa
    atualizado_em TIMESTAMP NOT NULL DEFAULT now(),   -- última atualização do estoque
    UNIQUE (farmacia_id, medicamento_id),  -- um registro por farmácia e remédio
    FOREIGN KEY (farmacia_id) REFERENCES Farmacia(id),
    FOREIGN KEY (medicamento_id) REFERENCES Medicamento(id)
);

-- =========================
-- Tabela de Dispensação
-- =========================
CREATE TABLE Dispensacao (
    id INT PRIMARY KEY,
    farmacia_id INT NOT NULL,
    medicamento_id INT NOT NULL,
    data DATE NOT NULL,
    quantidade INT NOT NULL CHECK (quantidade > 0),
    FOREIGN KEY (farmacia_id) REFERENCES Farmacia(id),
    FOREIGN KEY (medicamento_id) REFERENCES Medicamento(id)
);

-- =========================
-- Tabela de Usuários
-- =========================
CREATE TABLE Usuario (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,  -- o banco gera o id sozinho
    nome VARCHAR(100) NOT NULL,
    telefone VARCHAR(20) NOT NULL,   -- usado para SMS de confirmação
    email VARCHAR(100)
);

-- =========================
-- Tabela de Agendamentos
-- =========================
CREATE TABLE Agendamento (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id INT NOT NULL,
    farmacia_id INT NOT NULL,
    data_hora TIMESTAMP NOT NULL,    -- dia e horário da retirada
    status VARCHAR(20) NOT NULL DEFAULT 'agendado',
    criado_em TIMESTAMP NOT NULL DEFAULT now(),
    CHECK (status IN ('agendado', 'retirado', 'cancelado')),
    FOREIGN KEY (usuario_id) REFERENCES Usuario(id),
    FOREIGN KEY (farmacia_id) REFERENCES Farmacia(id)
);

-- =========================
-- Remédios de cada agendamento
-- =========================
CREATE TABLE Agendamento_Item (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    agendamento_id INT NOT NULL,
    medicamento_id INT NOT NULL,
    quantidade INT NOT NULL CHECK (quantidade > 0),
    FOREIGN KEY (agendamento_id) REFERENCES Agendamento(id),
    FOREIGN KEY (medicamento_id) REFERENCES Medicamento(id)
);