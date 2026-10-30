import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class PainelFarmacia {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    static boolean trata(HttpExchange troca, String caminho) throws IOException {
        boolean verificar = caminho.equals("/api/acesso-farmacia/verificar");
        if (!verificar && !caminho.equals("/api/acesso-farmacia") && !caminho.equals("/api/painel")) {
            return false;
        }
        try {
            boolean leitura = "GET".equals(troca.getRequestMethod());
            if (verificar) {
                if (leitura) {
                    verificarCnpj(troca);
                } else {
                    Servidor.enviarTexto(troca, 405, "Método não permitido");
                }
            } else if (caminho.equals("/api/acesso-farmacia")) {
                if (leitura) {
                    estadoDoPedido(troca);
                } else if (Login.pedidoPost(troca)) {
                    pedirAcesso(troca);
                }
            } else {
                if (leitura) {
                    painel(troca);
                } else if (Login.pedidoPost(troca)) {
                    alterarPainel(troca);
                }
            }
        } catch (IllegalArgumentException erro) {
            Login.responderErro(troca, 400, erro.getMessage());
        } catch (SQLException erro) {
            System.err.println("Erro na conta da farmácia: " + erro);
            Login.responderErro(troca, 500, "Erro interno no servidor.");
        }
        return true;
    }

    private static void estadoDoPedido(HttpExchange troca) throws IOException, SQLException {
        Login.Sessao sessao = quemPode(troca);
        if (sessao == null) {
            return;
        }
        String sql = "SELECT a.status, COALESCE(f.nome, a.nome_informado) AS farmacia "
                + "FROM acesso_farmacia a LEFT JOIN farmacia f ON f.id = a.farmacia_id "
                + "WHERE a.conta_id = ? ORDER BY a.criado_em DESC, a.id DESC LIMIT 1";
        try (Connection conexao = Banco.conectar();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setLong(1, sessao.contaId());
            try (ResultSet linha = comando.executeQuery()) {
                String status = "nenhum";
                String farmacia = null;
                if (linha.next()) {
                    status = linha.getString("status");
                    farmacia = linha.getString("farmacia");
                }
                // painel: a pessoa já pode abrir o painel (aprovada, ou cadastro novo ainda em análise)
                Acesso acesso = acessoDoPainel(sessao.contaId());
                boolean painel = acesso != null && (acesso.pendente() || "farmacia".equals(sessao.tipo()));
                // Json.texto não aceita null, por isso Json.de
                Login.json(troca, 200, "{\"status\":" + Json.de(status) + ",\"farmacia\":" + Json.de(farmacia)
                        + ",\"painel\":" + painel + "}");
            }
        }
    }

    // Diz se o CNPJ já está na base, para a tela decidir se pede o endereço.
    private static void verificarCnpj(HttpExchange troca) throws IOException, SQLException {
        Login.Sessao sessao = quemPode(troca);
        if (sessao == null) {
            return;
        }
        String consulta = troca.getRequestURI().getRawQuery() == null ? "" : troca.getRequestURI().getRawQuery();
        String cnpj = Login.campo(consulta, "cnpj").replaceAll("\\D", "");
        if (!cnpjValido(cnpj)) {
            throw new IllegalArgumentException("CNPJ inválido. Confira os 14 números.");
        }
        Existente existente = farmaciaPeloCnpj(cnpj);
        String estado;
        String nome = null;
        if (existente == null || reaproveitavel(existente, sessao.contaId())) {
            estado = "nova";
        } else if ("aprovada".equals(existente.situacao())) {
            estado = "base";
            nome = dadosDaFarmacia(existente.id()).get("nome").toString();
        } else {
            estado = "pendente".equals(existente.situacao()) ? "em_analise" : "indisponivel";
        }
        Login.json(troca, 200, "{\"estado\":" + Json.de(estado) + ",\"nome\":" + Json.de(nome) + "}");
    }

    private static final double[] CAIXA_CIDADES = {-47.62, -23.70, -47.25, -23.30}; // oeste, sul, leste, norte

    private record Dados(String nome, String endereco, String bairro, String cidade, String cep,
                         BigDecimal latitude, BigDecimal longitude, String precisao) {}

    // Cadastro da farmácia. CNPJ novo: cria a farmácia já com endereço e posição, mas "pendente" (fica fora do
    // mapa e das buscas até o administrador aprovar) e a conta já pode usar o painel. CNPJ que já está na base
    // pública: vira um pedido de acesso, e o painel só abre depois da aprovação.
    private static void pedirAcesso(HttpExchange troca) throws IOException, SQLException {
        Login.Sessao sessao = quemPode(troca);
        if (sessao == null) {
            return;
        }
        String formulario = Login.lerFormulario(troca);
        if (formulario == null) {
            Login.responderErro(troca, 413, "Pedido grande demais.");
            return;
        }
        String cnpj = Login.campo(formulario, "cnpj").replaceAll("\\D", "");
        String telefone = Login.campo(formulario, "telefone").replaceAll("\\D", "");

        if (!cnpjValido(cnpj)) {
            throw new IllegalArgumentException("CNPJ inválido. Confira os 14 números.");
        }
        if (telefone.length() < 10 || telefone.length() > 11) {
            throw new IllegalArgumentException("Telefone inválido. Digite com DDD (10 ou 11 números).");
        }
        if (acessoAprovado(sessao.contaId()) != null) {
            Login.responderErro(troca, 409, "Você já tem acesso aprovado a uma farmácia.");
            return;
        }
        if (temPedidoPendente(sessao.contaId())) {
            Login.responderErro(troca, 409, "Você já tem um pedido pendente.");
            return;
        }

        Existente existente = farmaciaPeloCnpj(cnpj);
        boolean nova = existente == null || reaproveitavel(existente, sessao.contaId());
        if (!nova && !"aprovada".equals(existente.situacao())) {
            throw new IllegalArgumentException("pendente".equals(existente.situacao())
                    ? "Este CNPJ já tem um cadastro aguardando aprovação."
                    : "Este CNPJ já foi usado em outro cadastro. Fale com a nossa equipe.");
        }
        Dados dados = nova ? lerDadosDaFarmacia(formulario) : null;

        try (Connection conexao = Banco.conectar()) {
            conexao.setAutoCommit(false);
            try {
                Long farmaciaId = nova ? null : existente.id();
                if (existente == null) {
                    farmaciaId = inserirFarmacia(conexao, dados, cnpj, telefone, sessao.contaId());
                } else if (nova) {
                    farmaciaId = existente.id();
                    reenviarFarmacia(conexao, farmaciaId, dados, telefone);
                }
                try (PreparedStatement comando = conexao.prepareStatement(
                        "INSERT INTO acesso_farmacia (conta_id, farmacia_id, cnpj_informado, telefone_informado, status) "
                                + "VALUES (?, ?, ?, ?, 'pendente')")) {
                    comando.setLong(1, sessao.contaId());
                    comando.setLong(2, farmaciaId);
                    comando.setString(3, cnpj);
                    comando.setString(4, telefone);
                    comando.executeUpdate();
                }
                conexao.commit();
            } catch (SQLException erro) {
                conexao.rollback();
                if ("23505".equals(erro.getSQLState())) {
                    Login.responderErro(troca, 409, "Este CNPJ ou esta conta já tem um cadastro em andamento.");
                    return;
                }
                throw erro;
            }
        }
        Login.json(troca, 201, "{\"status\":\"pendente\",\"painel\":" + nova + "}");
    }

    private static Dados lerDadosDaFarmacia(String formulario) {
        String nome = Login.campo(formulario, "nome").trim();
        String logradouro = Login.campo(formulario, "endereco").trim();
        String bairro = Login.campo(formulario, "bairro").trim();
        String cidade = Login.campo(formulario, "cidade").trim();
        String cep = Login.campo(formulario, "cep").replaceAll("\\D", "");

        if (nome.length() < 2 || nome.length() > 100) {
            throw new IllegalArgumentException("Informe o nome da farmácia (até 100 caracteres).");
        }
        if (logradouro.length() < 5 || logradouro.length() > 140) {
            throw new IllegalArgumentException("Informe o endereço com rua e número (de 5 a 140 caracteres).");
        }
        if (bairro.length() < 2 || bairro.length() > 60) {
            throw new IllegalArgumentException("Informe o bairro (até 60 caracteres).");
        }
        if (!cidade.equals("Sorocaba") && !cidade.equals("Votorantim")) {
            throw new IllegalArgumentException("Escolha a cidade da farmácia: Sorocaba ou Votorantim.");
        }
        if (!cep.isEmpty() && cep.length() != 8) {
            throw new IllegalArgumentException("CEP inválido. Digite os 8 números.");
        }
        BigDecimal latitude = coordenada(Login.campo(formulario, "latitude"), CAIXA_CIDADES[1], CAIXA_CIDADES[3]);
        BigDecimal longitude = coordenada(Login.campo(formulario, "longitude"), CAIXA_CIDADES[0], CAIXA_CIDADES[2]);
        if (latitude == null || longitude == null) {
            throw new IllegalArgumentException("Localize a farmácia no mapa antes de enviar: use o botão \"Localizar no mapa\" e escolha o endereço.");
        }
        String precisao = Login.campo(formulario, "precisao").equals("exata") ? "ponto do endereço" : "aproximada (rua)";
        return new Dados(nome, logradouro + " - " + bairro, bairro, cidade, cep.isEmpty() ? null : cep, latitude, longitude, precisao);
    }

    // vazio devolve null; fora da caixa de Sorocaba e Votorantim (ou não numérico) é erro
    private static BigDecimal coordenada(String texto, double minimo, double maximo) {
        if (texto.trim().isEmpty()) {
            return null;
        }
        try {
            BigDecimal valor = new BigDecimal(texto.trim());
            if (valor.doubleValue() < minimo || valor.doubleValue() > maximo) {
                throw new IllegalArgumentException("A localização escolhida fica fora de Sorocaba e Votorantim.");
            }
            return valor.setScale(6, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException erro) {
            throw new IllegalArgumentException("Localização inválida. Escolha o endereço de novo.");
        }
    }

    private static long inserirFarmacia(Connection conexao, Dados dados, String cnpj, String telefone, long contaId) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(
                "INSERT INTO farmacia (nome, endereco, cnpj, telefone, cidade, bairro, cep, latitude, longitude, "
                        + "precisao_posicao, situacao, cadastro_proprio, cadastrado_por_conta_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'pendente', TRUE, ?) RETURNING id")) {
            comando.setString(1, dados.nome());
            comando.setString(2, dados.endereco());
            comando.setString(3, cnpj);
            comando.setString(4, Admin.formatarTelefone(telefone));
            comando.setString(5, dados.cidade());
            comando.setString(6, dados.bairro());
            comando.setString(7, dados.cep());
            comando.setBigDecimal(8, dados.latitude());
            comando.setBigDecimal(9, dados.longitude());
            comando.setString(10, dados.precisao());
            comando.setLong(11, contaId);
            try (ResultSet linha = comando.executeQuery()) {
                linha.next();
                return linha.getLong(1);
            }
        }
    }

    // cadastro recusado que a mesma conta corrigiu e mandou de novo: o estoque e os serviços já lançados ficam
    private static void reenviarFarmacia(Connection conexao, long farmaciaId, Dados dados, String telefone) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(
                "UPDATE farmacia SET nome = ?, endereco = ?, telefone = ?, cidade = ?, bairro = ?, cep = ?, latitude = ?, "
                        + "longitude = ?, precisao_posicao = ?, situacao = 'pendente' WHERE id = ?")) {
            comando.setString(1, dados.nome());
            comando.setString(2, dados.endereco());
            comando.setString(3, Admin.formatarTelefone(telefone));
            comando.setString(4, dados.cidade());
            comando.setString(5, dados.bairro());
            comando.setString(6, dados.cep());
            comando.setBigDecimal(7, dados.latitude());
            comando.setBigDecimal(8, dados.longitude());
            comando.setString(9, dados.precisao());
            comando.setLong(10, farmaciaId);
            comando.executeUpdate();
        }
    }

    private static boolean temPedidoPendente(long contaId) throws SQLException {
        try (Connection conexao = Banco.conectar();
             PreparedStatement comando = conexao.prepareStatement(
                     "SELECT 1 FROM acesso_farmacia WHERE conta_id = ? AND status = 'pendente'")) {
            comando.setLong(1, contaId);
            try (ResultSet linha = comando.executeQuery()) {
                return linha.next();
            }
        }
    }

    private static void painel(HttpExchange troca) throws IOException, SQLException {
        Contexto contexto = exigirFarmacia(troca);
        if (contexto == null) {
            return;
        }
        long farmaciaId = contexto.farmaciaId();
        // o id da farmácia vem do banco, nunca do pedido
        String sql = "SELECT m.id AS medicamento_id, m.nome, e.quantidade, p.preco, "
                + "GREATEST(e.atualizado_em, p.atualizado_em) AS atualizado_em, p.informado_por_conta_id "
                + "FROM (SELECT medicamento_id FROM estoque WHERE farmacia_id = ? "
                + "      UNION SELECT medicamento_id FROM preco WHERE farmacia_id = ?) par "
                + "JOIN medicamento m ON m.id = par.medicamento_id "
                + "LEFT JOIN estoque e ON e.farmacia_id = ? AND e.medicamento_id = par.medicamento_id "
                + "LEFT JOIN preco p ON p.farmacia_id = ? AND p.medicamento_id = par.medicamento_id "
                + "ORDER BY m.nome";
        List<Map<String, Object>> remedios = new ArrayList<>();
        try (Connection conexao = Banco.conectar();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            for (int i = 1; i <= 4; i++) {
                comando.setLong(i, farmaciaId);
            }
            try (ResultSet linhas = comando.executeQuery()) {
                while (linhas.next()) {
                    Map<String, Object> remedio = new LinkedHashMap<>();
                    remedio.put("id", linhas.getLong("medicamento_id"));
                    remedio.put("nome", linhas.getString("nome"));
                    remedio.put("preco", linhas.getBigDecimal("preco"));
                    Integer quantidade = linhas.getObject("quantidade", Integer.class);
                    remedio.put("disponivel", quantidade != null && quantidade > 0);
                    java.sql.Timestamp atualizado = linhas.getTimestamp("atualizado_em");
                    remedio.put("atualizadoEm", atualizado == null ? null : DATA.format(atualizado.toLocalDateTime()));
                    remedio.put("informadoPelaFarmacia", linhas.getObject("informado_por_conta_id") != null);
                    remedios.add(remedio);
                }
            }
        }
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("farmacia", dadosDaFarmacia(farmaciaId));
        resposta.put("remedios", remedios);
        Login.json(troca, 200, Json.de(resposta));
    }

    private static void alterarPainel(HttpExchange troca) throws IOException, SQLException {
        Contexto contexto = exigirFarmacia(troca);
        if (contexto == null) {
            return;
        }
        long farmaciaId = contexto.farmaciaId();
        String formulario = Login.lerFormulario(troca);
        if (formulario == null) {
            Login.responderErro(troca, 413, "Pedido grande demais.");
            return;
        }
        long medicamento = numero(Login.campo(formulario, "medicamento"));
        if (!medicamentoExiste(medicamento)) {
            throw new IllegalArgumentException("Remédio não encontrado.");
        }
        String acao = Login.campo(formulario, "acao");

        try (Connection conexao = Banco.conectar()) {
            conexao.setAutoCommit(false);
            try {
                if (acao.equals("remover")) {
                    apagarPar(conexao, "estoque", farmaciaId, medicamento);
                    apagarPar(conexao, "preco", farmaciaId, medicamento);
                } else if (acao.equals("salvar")) {
                    BigDecimal preco = precoValido(Login.campo(formulario, "preco"));
                    String disponivel = Login.campo(formulario, "disponivel");
                    if (!disponivel.equals("sim") && !disponivel.equals("nao")) {
                        throw new IllegalArgumentException("Informe se o remédio está disponível (sim ou não).");
                    }
                    gravarEstoque(conexao, farmaciaId, medicamento, disponivel.equals("sim") ? 1 : 0);
                    gravarPreco(conexao, farmaciaId, medicamento, preco, contexto.contaId());
                } else {
                    throw new IllegalArgumentException("Ação inválida.");
                }
                conexao.commit();
            } catch (IllegalArgumentException | SQLException erro) {
                conexao.rollback();
                throw erro;
            }
        }
        Login.json(troca, 200, "{\"ok\":true}");
    }

    record Contexto(long farmaciaId, long contaId, boolean pendente) {
    }

    // farmacia_id que a conta pode operar no painel. "pendente" = cadastro novo da própria conta, ainda em
    // análise: já pode lançar estoque e serviços, mas a farmácia fica oculta ao público até a aprovação.
    record Acesso(long farmaciaId, boolean pendente) {
    }

    static Acesso acessoDoPainel(long contaId) throws SQLException {
        String sql = "SELECT a.farmacia_id, a.status FROM acesso_farmacia a JOIN farmacia f ON f.id = a.farmacia_id "
                + "WHERE a.conta_id = ? AND a.farmacia_id IS NOT NULL "
                + "AND (a.status = 'aprovado' OR (a.status = 'pendente' AND f.cadastro_proprio AND f.situacao = 'pendente')) "
                + "ORDER BY (a.status = 'aprovado') DESC, COALESCE(a.decidido_em, a.criado_em) DESC LIMIT 1";
        try (Connection conexao = Banco.conectar();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setLong(1, contaId);
            try (ResultSet linha = comando.executeQuery()) {
                return linha.next() ? new Acesso(linha.getLong("farmacia_id"), !"aprovado".equals(linha.getString("status"))) : null;
            }
        }
    }

    // Para as telas do painel (estoque, serviços): responde o erro e devolve null quando a conta não pode.
    // O id da farmácia vem do banco, nunca do pedido.
    static Contexto exigirFarmacia(HttpExchange troca) throws IOException, SQLException {
        Login.Sessao sessao = quemPode(troca);
        if (sessao == null) {
            return null;
        }
        Acesso acesso = acessoDoPainel(sessao.contaId());
        if (acesso == null) {
            Login.responderErro(troca, 403, "Você ainda não tem acesso a uma farmácia.");
            return null;
        }
        // acesso aprovado só vale para conta de farmácia; cadastro novo em análise vale já
        if (!acesso.pendente() && !"farmacia".equals(sessao.tipo())) {
            Login.responderErro(troca, 403, "Só contas de farmácia têm o painel.");
            return null;
        }
        return new Contexto(acesso.farmaciaId(), sessao.contaId(), acesso.pendente());
    }

    private static Login.Sessao quemPode(HttpExchange troca) throws IOException {
        Login.Sessao sessao = Login.sessaoAtual(troca);
        if (sessao == null) {
            Login.responderErro(troca, 401, "Entre na sua conta para continuar.");
            return null;
        }
        if ("admin".equals(sessao.tipo())) {
            Login.responderErro(troca, 403, "A conta de administrador não tem painel de farmácia.");
            return null;
        }
        return sessao;
    }

    private static Long acessoAprovado(long contaId) throws SQLException {
        try (Connection conexao = Banco.conectar();
             PreparedStatement comando = conexao.prepareStatement(
                     "SELECT farmacia_id FROM acesso_farmacia WHERE conta_id = ? AND status = 'aprovado' "
                     + "AND farmacia_id IS NOT NULL ORDER BY decidido_em DESC LIMIT 1")) {
            comando.setLong(1, contaId);
            try (ResultSet linha = comando.executeQuery()) {
                return linha.next() ? linha.getLong("farmacia_id") : null;
            }
        }
    }

    private record Existente(long id, String situacao, boolean cadastroProprio, Long donoContaId) {}

    private static Existente farmaciaPeloCnpj(String cnpj) throws SQLException {
        try (Connection conexao = Banco.conectar();
             PreparedStatement comando = conexao.prepareStatement(
                     "SELECT id, situacao, cadastro_proprio, cadastrado_por_conta_id FROM farmacia WHERE cnpj = ?")) {
            comando.setString(1, cnpj);
            try (ResultSet linha = comando.executeQuery()) {
                if (!linha.next()) {
                    return null;
                }
                Long dono = linha.getObject("cadastrado_por_conta_id") == null ? null : linha.getLong("cadastrado_por_conta_id");
                return new Existente(linha.getLong("id"), linha.getString("situacao"), linha.getBoolean("cadastro_proprio"), dono);
            }
        }
    }

    // cadastro recusado da própria conta pode ser corrigido e reenviado
    private static boolean reaproveitavel(Existente existente, long contaId) {
        return "recusada".equals(existente.situacao()) && existente.cadastroProprio()
                && existente.donoContaId() != null && existente.donoContaId() == contaId;
    }

    private static Map<String, Object> dadosDaFarmacia(long farmaciaId) throws SQLException {
        try (Connection conexao = Banco.conectar();
             PreparedStatement comando = conexao.prepareStatement("SELECT id, nome, endereco, latitude IS NOT NULL AS tem_posicao, situacao, cadastro_proprio FROM farmacia WHERE id = ?")) {
            comando.setLong(1, farmaciaId);
            try (ResultSet linha = comando.executeQuery()) {
                linha.next();
                Map<String, Object> dados = new LinkedHashMap<>();
                dados.put("id", linha.getLong("id"));
                dados.put("nome", linha.getString("nome"));
                dados.put("endereco", linha.getString("endereco"));
                dados.put("temPosicao", linha.getBoolean("tem_posicao"));
                dados.put("situacao", linha.getString("situacao"));
                dados.put("cadastroProprio", linha.getBoolean("cadastro_proprio"));
                return dados;
            }
        }
    }

    private static boolean medicamentoExiste(long id) throws SQLException {
        try (Connection conexao = Banco.conectar();
             PreparedStatement comando = conexao.prepareStatement("SELECT 1 FROM medicamento WHERE id = ?")) {
            comando.setLong(1, id);
            try (ResultSet linha = comando.executeQuery()) {
                return linha.next();
            }
        }
    }

    private static BigDecimal precoValido(String texto) {
        try {
            BigDecimal preco = new BigDecimal(texto.trim().replace(",", "."));
            if (preco.signum() < 0 || preco.compareTo(new BigDecimal("9999.99")) > 0 || preco.scale() > 2) {
                throw new IllegalArgumentException("O preço precisa estar entre 0 e 9999,99 (com até 2 casas).");
            }
            return preco;
        } catch (NumberFormatException erro) {
            throw new IllegalArgumentException("O preço precisa ser um número, por exemplo 12,50.");
        }
    }

    private static long numero(String texto) {
        try {
            long valor = Long.parseLong(texto.trim());
            if (valor > 0) {
                return valor;
            }
        } catch (NumberFormatException erro) {
        }
        throw new IllegalArgumentException("Remédio inválido.");
    }

    private static void gravarEstoque(Connection conexao, long farmacia, long medicamento, int quantidade) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(
                "INSERT INTO estoque (farmacia_id, medicamento_id, quantidade, atualizado_em) VALUES (?, ?, ?, now()) "
                + "ON CONFLICT (farmacia_id, medicamento_id) DO UPDATE SET quantidade = EXCLUDED.quantidade, atualizado_em = now()")) {
            comando.setLong(1, farmacia);
            comando.setLong(2, medicamento);
            comando.setInt(3, quantidade);
            comando.executeUpdate();
        }
    }

    static void gravarPreco(Connection conexao, long farmacia, long medicamento, BigDecimal preco, long contaId) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(
                "INSERT INTO preco (farmacia_id, medicamento_id, preco, atualizado_em, informado_por_conta_id) "
                + "VALUES (?, ?, ?, now(), ?) "
                + "ON CONFLICT (farmacia_id, medicamento_id) DO UPDATE SET preco = EXCLUDED.preco, "
                + "atualizado_em = now(), informado_por_conta_id = EXCLUDED.informado_por_conta_id")) {
            comando.setLong(1, farmacia);
            comando.setLong(2, medicamento);
            comando.setBigDecimal(3, preco);
            comando.setLong(4, contaId);
            comando.executeUpdate();
        }
    }

    private static void apagarPar(Connection conexao, String tabela, long farmacia, long medicamento) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(
                "DELETE FROM " + tabela + " WHERE farmacia_id = ? AND medicamento_id = ?")) {
            comando.setLong(1, farmacia);
            comando.setLong(2, medicamento);
            comando.executeUpdate();
        }
    }

    static boolean cnpjValido(String cnpj) {
        if (!cnpj.matches("\\d{14}") || cnpj.chars().distinct().count() == 1) {
            return false;
        }
        int[] pesos1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] pesos2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        return digitoCnpj(cnpj, pesos1) == cnpj.charAt(12) - '0'
                && digitoCnpj(cnpj, pesos2) == cnpj.charAt(13) - '0';
    }

    private static int digitoCnpj(String cnpj, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) {
            soma += (cnpj.charAt(i) - '0') * pesos[i];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
