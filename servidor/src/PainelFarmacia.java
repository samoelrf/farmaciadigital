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
        if (!caminho.equals("/api/acesso-farmacia") && !caminho.equals("/api/painel")) {
            return false;
        }
        try {
            boolean leitura = "GET".equals(troca.getRequestMethod());
            if (caminho.equals("/api/acesso-farmacia")) {
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
                // Json.texto não aceita null, por isso Json.de
                Login.json(troca, 200, "{\"status\":" + Json.de(status) + ",\"farmacia\":" + Json.de(farmacia) + "}");
            }
        }
    }

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
        String nome = Login.campo(formulario, "nome").trim();
        String endereco = Login.campo(formulario, "endereco").trim();
        String cidade = Login.campo(formulario, "cidade").trim();

        if (!cnpjValido(cnpj)) {
            throw new IllegalArgumentException("CNPJ inválido. Confira os 14 números.");
        }
        if (telefone.length() < 10 || telefone.length() > 11) {
            throw new IllegalArgumentException("Telefone inválido. Digite com DDD (10 ou 11 números).");
        }
        if (nome.length() > 100 || endereco.length() > 200) {
            throw new IllegalArgumentException("Nome (até 100 caracteres) ou endereço (até 200 caracteres) grande demais.");
        }

        Long farmaciaId = farmaciaPeloCnpj(cnpj);
        if (farmaciaId == null && (nome.length() < 2 || endereco.length() < 5)) {
            throw new IllegalArgumentException("Não achamos este CNPJ na nossa base. Informe o nome e o endereço da farmácia.");
        }
        if (farmaciaId == null && !cidade.equals("Sorocaba") && !cidade.equals("Votorantim")) {
            throw new IllegalArgumentException("Escolha a cidade da farmácia: Sorocaba ou Votorantim.");
        }
        if (acessoAprovado(sessao.contaId()) != null) {
            Login.responderErro(troca, 409, "Você já tem acesso aprovado a uma farmácia.");
            return;
        }

        String sql = "INSERT INTO acesso_farmacia (conta_id, farmacia_id, cnpj_informado, telefone_informado, "
                + "nome_informado, endereco_informado, status, cidade_informada) VALUES (?, ?, ?, ?, ?, ?, 'pendente', ?)";
        try (Connection conexao = Banco.conectar();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setLong(1, sessao.contaId());
            if (farmaciaId == null) comando.setNull(2, java.sql.Types.INTEGER); else comando.setLong(2, farmaciaId);
            comando.setString(3, cnpj);
            comando.setString(4, telefone);
            comando.setString(5, farmaciaId == null ? nome : null);
            comando.setString(6, farmaciaId == null ? endereco : null);
            comando.setString(7, farmaciaId == null ? cidade : null);
            comando.executeUpdate();
        } catch (SQLException erro) {
            if ("23505".equals(erro.getSQLState())) {
                Login.responderErro(troca, 409, "Você já tem um pedido pendente.");
                return;
            }
            throw erro;
        }
        Login.json(troca, 201, "{\"status\":\"pendente\"}");
    }

    private static void painel(HttpExchange troca) throws IOException, SQLException {
        Login.Sessao sessao = quemPode(troca);
        if (sessao == null) {
            return;
        }
        // só conta de farmácia: acesso aprovado sozinho não basta
        if (!"farmacia".equals(sessao.tipo())) {
            Login.responderErro(troca, 403, "Só contas de farmácia têm o painel.");
            return;
        }
        Long farmaciaId = acessoAprovado(sessao.contaId());
        if (farmaciaId == null) {
            Login.responderErro(troca, 403, "Você ainda não tem acesso aprovado a uma farmácia.");
            return;
        }
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
        Login.Sessao sessao = quemPode(troca);
        if (sessao == null) {
            return;
        }
        if (!"farmacia".equals(sessao.tipo())) {
            Login.responderErro(troca, 403, "Só contas de farmácia têm o painel.");
            return;
        }
        Long farmaciaId = acessoAprovado(sessao.contaId());
        if (farmaciaId == null) {
            Login.responderErro(troca, 403, "Você ainda não tem acesso aprovado a uma farmácia.");
            return;
        }
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
                    gravarPreco(conexao, farmaciaId, medicamento, preco, sessao.contaId());
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

    private static Long farmaciaPeloCnpj(String cnpj) throws SQLException {
        try (Connection conexao = Banco.conectar();
             PreparedStatement comando = conexao.prepareStatement("SELECT id FROM farmacia WHERE cnpj = ?")) {
            comando.setString(1, cnpj);
            try (ResultSet linha = comando.executeQuery()) {
                return linha.next() ? linha.getLong("id") : null;
            }
        }
    }

    private static Map<String, Object> dadosDaFarmacia(long farmaciaId) throws SQLException {
        try (Connection conexao = Banco.conectar();
             PreparedStatement comando = conexao.prepareStatement("SELECT id, nome, endereco, latitude IS NOT NULL AS tem_posicao FROM farmacia WHERE id = ?")) {
            comando.setLong(1, farmaciaId);
            try (ResultSet linha = comando.executeQuery()) {
                linha.next();
                Map<String, Object> dados = new LinkedHashMap<>();
                dados.put("id", linha.getLong("id"));
                dados.put("nome", linha.getString("nome"));
                dados.put("endereco", linha.getString("endereco"));
                dados.put("temPosicao", linha.getBoolean("tem_posicao"));
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

    private static void gravarPreco(Connection conexao, long farmacia, long medicamento, BigDecimal preco, long contaId) throws SQLException {
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
