-- UBS de Sorocaba e Votorantim (49), conferidas no CNES e no Ministério da Saúde
-- rode depois de tabela.sql (pode rodar mais de uma vez)

CREATE TABLE IF NOT EXISTS ubs (
    id INT PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    endereco VARCHAR(200) NOT NULL,
    cidade VARCHAR(50) NOT NULL,
    latitude DECIMAL(9,6) NOT NULL,
    longitude DECIMAL(9,6) NOT NULL
);
ALTER TABLE ubs ADD COLUMN IF NOT EXISTS precisao_posicao VARCHAR(30);

INSERT INTO ubs (id, nome, endereco, cidade, latitude, longitude, precisao_posicao) VALUES
(2034778, 'Centro de Saude Vila Hortencia Sorocaba', 'Rua Teodoro Kaisel, 677, V Hortencia', 'Sorocaba', -23.505870, -47.439508, 'confirmada'),
(2034786, 'Centro de Saude Maria do Carmo Sorocaba', 'Rua Joaquim Ferreira Barbosa, 727, Maria do Carmo', 'Sorocaba', -23.476547, -47.451193, 'confirmada'),
(2034794, 'Unidade de Saude da Familia Vila Barao Sorocaba', 'Rua Afonso Muraro, 41, Vila Barao', 'Sorocaba', -23.491604, -47.483083, 'confirmada'),
(2035847, 'Centro de Saude Vila Haro Sorocaba', 'Rua Aristides da Silva Lobo, 379, Vila Haro', 'Sorocaba', -23.499810, -47.433080, 'confirmada'),
(2044064, 'Unidade de Saude da Familia Nova Esperanca Sorocaba', 'Rua Paula Mayer Cattini, 689, Jardim Nova Esperanca', 'Sorocaba', -23.492164, -47.492747, 'confirmada'),
(2049244, 'Centro de Saude E PA Parque Laranjeiras Sorocaba', 'Rua Sonia Bernuncio, 24, Parque das Laranjeiras', 'Sorocaba', -23.453968, -47.469113, 'confirmada'),
(2049287, 'Unidade de Saude da Familia Cajuru do Sul Sorocaba', 'Avenida Parana, 3719, Cajuru do Sul', 'Sorocaba', -23.399405, -47.379340, 'confirmada'),
(2049295, 'Centro de Saude Nova Sorocaba Sorocaba', 'Avenida Americana, 351, Vila Nova Sorocaba', 'Sorocaba', -23.466832, -47.485213, 'confirmada'),
(2051990, 'Centro de Saude Vila Santana Sorocaba', 'Rua Doutor Deodoro Reis, 150, Vila Santana', 'Sorocaba', -23.488373, -47.459687, 'confirmada'),
(2052032, 'Centro de Saude Mineirao Sorocaba', 'Rua Tenente Erico de Oliveira, 110, Vila Mineirao', 'Sorocaba', -23.468386, -47.462658, 'confirmada'),
(2054043, 'Unidade de Saude da Familia E PA Parque Sao Bento Sorocaba', 'Av Dr Gualberto Moreira, 4985, Parque Sao Bento', 'Sorocaba', -23.435427, -47.505967, 'confirmada'),
(2054353, 'Centro de Saude Barcelona Sorocaba', 'Rua Colombia, 253, Vila Barcelona', 'Sorocaba', -23.519886, -47.439347, 'confirmada'),
(2055139, 'Unidade de Saude da Familia Cerrado Sorocaba', 'Rua Visconde do Rio Branco, 885, Vila Jardini', 'Sorocaba', -23.511583, -47.475805, 'confirmada'),
(2055449, 'Centro de Saude Eden Sorocaba Sorocaba', 'Rua Salvador Leite Marques, 933, Eden', 'Sorocaba', -23.418554, -47.415813, 'confirmada'),
(2055678, 'Centro de Saude Vila Fiori de Sorocaba', 'Rua Andre Manente, S/n, Vila Olimpia', 'Sorocaba', -23.479268, -47.470698, 'confirmada'),
(2055686, 'Centro de Saude Escola Sorocaba', 'Av Pereira Inacio, 500, Lageado', 'Sorocaba', -23.517855, -47.453124, 'aproximada (rua)'),
(2059487, 'Unidade de Saude da Familia Lopes de Oliveira Sorocaba', 'Avenida Riusaku Kanizawa, 795, Lopes de Oliveira', 'Sorocaba', -23.466510, -47.503188, 'confirmada'),
(2059533, 'Unidade de Saude da Familia E PA Aparecidinha Sorocaba', 'Rua Joaquim Machado, 620, Aparecidinha', 'Sorocaba', -23.442049, -47.377068, 'confirmada'),
(2061031, 'Centro de Saude Maria Eugenia Sorocaba', 'Rua Mario Romano, 264, Jardim Maria Eugenia', 'Sorocaba', -23.455921, -47.491287, 'confirmada'),
(2063026, 'Centro de Saude Vila Angelica Sorocaba', 'Rua Gabriel de Lara, 255, Jardim Ana Maria', 'Sorocaba', -23.485627, -47.473650, 'confirmada'),
(2064316, 'Centro de Saude Marcia Mendes Sorocaba', 'Jose Augusto Rabello Junior, 91, Jadim Vera Cruz', 'Sorocaba', -23.513410, -47.494755, 'confirmada'),
(2066890, 'Unidade de Saude da Familia Vila Sabia Sorocaba', 'Rua Dionizio Bueno Sampaio, 91, Vila Zacarias', 'Sorocaba', -23.523194, -47.434876, 'confirmada'),
(2070669, 'Unidade de Saude da Familia Vitoria Regia Sorocaba', 'Rua Francisco da Silva Martins, 35, Vitoria Regia', 'Sorocaba', -23.436410, -47.470225, 'confirmada'),
(2070693, 'Unidade Saude da Familia E PA Brigadeiro Tobias Sorocaba', 'Rua Ana Gomes Correa, 55, Brigadeiro Tobias', 'Sorocaba', -23.507860, -47.370340, 'aproximada (rua)'),
(2087456, 'Unidade Basica de Saude Rio Acima', 'Avenida Otavio Augusto Rangel, 1282, Rio Acima', 'Votorantim', -23.535113, -47.435904, 'confirmada'),
(2087464, 'Unidade Basica de Saude Itapeva', 'Rua Joao Santiago Figueira, 200, Itapeva', 'Votorantim', -23.577312, -47.460283, 'confirmada'),
(2087472, 'Unidade Basica de Saude Vila Nova Votorantim', 'Avenida Pedro Augusto Rangel, 1925, Vila Nova Votorantim', 'Votorantim', -23.538497, -47.414923, 'confirmada'),
(2087499, 'Unidade Basica de Saude Jardim Novo Mundo', 'Rua Abilio Maia, 46, Jardim Novo Mundo', 'Votorantim', -23.539116, -47.503986, 'confirmada'),
(2087510, 'Unidade Basica de Saude Jardim Clarice', 'Rua Mercedes Nardy Arcuri, S/n, Jardim Clarice', 'Votorantim', -23.550346, -47.462718, 'confirmada'),
(2087537, 'Unidade Basica de Saude Parque Bela Vista', 'Avenida Sao Joao, 867, Parque Bela Vista', 'Votorantim', -23.540665, -47.451115, 'confirmada'),
(2087553, 'Unidade Basica de Saude Jardim Serrano', 'Rua Francisco Lopes de Almeida, 76, Jardim Serrano', 'Votorantim', -23.560117, -47.454315, 'confirmada'),
(2087561, 'Unidade Basica de Saude Vila Garcia', 'Avenida Izabel Ferreira Coelho, 271, Vila Garcia', 'Votorantim', -23.530202, -47.430122, 'confirmada'),
(2087588, 'Unidade Estrategia de Saude da Familia do Green Valley', 'Rua Jose Raimundo da Silva, S/n, Green Valley', 'Votorantim', -23.558418, -47.490137, 'confirmada'),
(2087596, 'Unidade Estrategia de Saude da Familia Jardim Tatiana', 'Rua Adriano Maciel de Queiroz, 550, Jardim Tatiana', 'Votorantim', -23.543511, -47.494337, 'confirmada'),
(2690853, 'Unidade de Saude da Familia E PA Habiteto Sorocaba', 'Avenida Itavuvu, 7000, Ana Paula Eleuterio', 'Sorocaba', -23.422010, -47.482060, 'confirmada'),
(2690861, 'Centro de Saude Jardim Simus Sorocaba', 'Alameda dos Lirios, 327, Jardim Simus', 'Sorocaba', -23.502190, -47.495610, 'confirmada'),
(3354768, 'Unidade Basica de Saude Barra Funda', 'Rua Lopes Chaves, S/n, Barra Funda', 'Votorantim', -23.546118, -47.438311, 'aproximada (rua)'),
(3846660, 'Unidade Estrategia de Saude da Familia Jardim Archila', 'Rua Lazara Bueno de Arruda, 68, Jardim Archila', 'Votorantim', -23.532986, -47.444673, 'confirmada'),
(4050576, 'Centro de Saude E PA Sorocaba I Sorocaba', 'Avenida Doutor Americo Figueiredo, 3171, Julio Mesquita Filho', 'Sorocaba', -23.507643, -47.485545, 'confirmada'),
(4443233, 'Unidade Estrategia de Saude da Familia Parque Sao Joao', 'Rua Zilda Tescaro Sbrana, 00, Parque Sao Joao', 'Votorantim', -23.568599, -47.452495, 'confirmada'),
(5702593, 'Unidade de Saude da Familia E PA Ulisses Sorocaba', 'Rua Ferdinando Irineu Corra, S/n, Conjunto Habitaciona', 'Sorocaba', -23.431945, -47.457719, 'confirmada'),
(5878128, 'Unidade de Saude da Familia Paineiras Sorocaba', 'Rua Eliza Stefani Lamos, 130, Parque das Paineiras', 'Sorocaba', -23.446094, -47.472024, 'confirmada'),
(6009433, 'Unidade Estrategia de Saude da Familia Vila Amorim', 'Rua Jose Antonio de Mello, 81, Vila Amorim', 'Votorantim', -23.555965, -47.443547, 'confirmada'),
(6392989, 'Unidade de Saude da Familia Wanel Ville Sorocaba', 'Rua Alexandre Caldini, 442, Wanel Ville', 'Sorocaba', -23.494970, -47.505654, 'confirmada'),
(7114354, 'Centro de Saude E PA Jardim Sao Guilherme Sorocaba', 'Rua Belmiro Moreira Soares, 1100, Jardim Sao Guilherme', 'Sorocaba', -23.445604, -47.490043, 'confirmada'),
(7710747, 'Unidade Estrategia de Saude da Familia Jardim Cristal', 'Rua Elcia Pinto da Rosa, 15, Jardim Cristal', 'Votorantim', -23.578668, -47.465648, 'confirmada'),
(7883110, 'Unidade Estrategia de Saude da Familia Pro Morar', 'Rua Boa Ventura Maganhato, 138, Pro Morar', 'Votorantim', -23.538718, -47.409399, 'confirmada'),
(9132015, 'Unidade de Saude da Familia Jardim Rodrigo', 'Rua Alpheu Castro Santos, 220, Lopes de Oliveira', 'Sorocaba', -23.471235, -47.507108, 'confirmada'),
(9188304, 'Centro de Saude E PA Jardim Caranda Sorocaba', 'Rua Jose Jesus Infanti, 260, Jardim Caranda', 'Sorocaba', -23.496325, -47.510961, 'confirmada')
ON CONFLICT (id) DO UPDATE SET
    nome = EXCLUDED.nome, endereco = EXCLUDED.endereco, cidade = EXCLUDED.cidade,
    latitude = EXCLUDED.latitude, longitude = EXCLUDED.longitude, precisao_posicao = EXCLUDED.precisao_posicao;

DELETE FROM ubs WHERE id NOT IN (2034778, 2034786, 2034794, 2035847, 2044064, 2049244, 2049287, 2049295, 2051990, 2052032, 2054043, 2054353, 2055139, 2055449, 2055678, 2055686, 2059487, 2059533, 2061031, 2063026, 2064316, 2066890, 2070669, 2070693, 2087456, 2087464, 2087472, 2087499, 2087510, 2087537, 2087553, 2087561, 2087588, 2087596, 2690853, 2690861, 3354768, 3846660, 4050576, 4443233, 5702593, 5878128, 6009433, 6392989, 7114354, 7710747, 7883110, 9132015, 9188304);
