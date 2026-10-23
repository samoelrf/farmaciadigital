import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// admin só é criado pelo CriarAdmin, no terminal
class Admin {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    record Pedido(long id, long contaId, Long farmaciaId, String status,
                  String cnpj, String telefone, String nomeInformado, String enderecoInformado, String cidadeInformada) {}

    static boolean trata(HttpExchange troca, String caminho) throws IOException {
        if (!caminho.equals("/api/admin/resumo") && !caminho.equals("/api/admin/pedidos")) {
            return false;
        }
        try {
            if (!Login.exigirAdmin(troca)) {
                return true;
            }
            Login.Sessao admin = Login.sessaoAtual(troca);
            boolean leitura = "GET".equals(troca.getRequestMethod());
            if (caminho.equals("/api/admin/resumo")) {
                if (leitura) resumo(troca); else Servidor.enviarTexto(troca, 405, "Método não permitido");
            } else if (leitura) {
                listar(troca);
            } else if (Login.pedidoPost(troca)) {
                decidir(troca, admin);
            }
        } catch (IllegalArgumentException erro) {
            Login.responderErro(troca, 400, erro.getMessage());
        } catch (SQLException erro) {
            System.err.println("Erro na área do administrador: " + erro);
            Login.responderErro(troca, 500, "Erro interno no servidor.");
        }
        return true;
    }

    private static void resumo(HttpExchange troca) throws IOException, SQLException {
        Map<String, Object> contas = new LinkedHashMap<>();
        int pendentes;
        int farmaciasInformadas;
        try (Connection conexao = Banco.conectar()) {
            try (PreparedStatement comando = conexao.prepareStatement("SELECT tipo, count(*) FROM conta GROUP BY tipo ORDER BY tipo");
                 ResultSet linhas = comando.executeQuery()) {
                while (linhas.next()) {
                    contas.put(linhas.getString(1), linhas.getLong(2));
                }
            }
            pendentes = contar(conexao, "SELECT count(*) FROM acesso_farmacia WHERE status = 'pendente'");
            farmaciasInformadas = contar(conexao, "SELECT count(DISTINCT farmacia_id) FROM preco WHERE informado_por_conta_id IS NOT NULL");
        }
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("contas", contas);
        resposta.put("pedidosPendentes", pendentes);
        resposta.put("farmaciasComRemediosInformados", farmaciasInformadas);
        Login.json(troca, 200, Json.de(resposta));
    }

    private static void listar(HttpExchange troca) throws IOException, SQLException {
        String status = Rotas.parametro(troca.getRequestURI().getRawQuery(), "status");
        if (status == null) {
            status = "pendente";
        }
        if (!List.of("pendente", "aprovado", "recusado", "todos").contains(status)) {
            throw new IllegalArgumentException("Status inválido. Use pendente, aprovado, recusado ou todos.");
        }
        String sql = "SELECT a.id, c.nome, c.email, a.cnpj_informado, a.telefone_informado, a.nome_informado, "
                + "a.endereco_informado, a.cidade_informada, a.status, a.criado_em, a.decidido_em, d.nome AS decidido_por_nome, "
                + "f.id AS farmacia_id, f.nome AS farmacia_nome, f.endereco AS farmacia_endereco, f.telefone AS farmacia_telefone "
                + "FROM acesso_farmacia a JOIN conta c ON c.id = a.conta_id "
                + "LEFT JOIN conta d ON d.id = a.decidido_por "
                + "LEFT JOIN farmacia f ON f.id = a.farmacia_id "
                + "WHERE (? = 'todos' OR a.status = ?) ORDER BY a.criado_em DESC, a.id DESC";
        List<Map<String, Object>> pedidos = new ArrayList<>();
        try (Connection conexao = Banco.conectar();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, status);
            comando.setString(2, status);
            try (ResultSet linhas = comando.executeQuery()) {
                while (linhas.next()) {
                    Map<String, Object> pedido = new LinkedHashMap<>();
                    pedido.put("id", linhas.getLong("id"));
                    pedido.put("nomeConta", linhas.getString("nome"));
                    pedido.put("emailConta", linhas.getString("email"));
                    pedido.put("cnpj", linhas.getString("cnpj_informado"));
                    pedido.put("telefone", linhas.getString("telefone_informado"));
                    pedido.put("status", linhas.getString("status"));
                    pedido.put("criadoEm", data(linhas.getTimestamp("criado_em")));
                    pedido.put("decididoEm", data(linhas.getTimestamp("decidido_em")));
                    pedido.put("decididoPor", linhas.getString("decidido_por_nome"));
                    Map<String, Object> farmacia = null;
                    if (linhas.getObject("farmacia_id") != null) {
                        farmacia = new LinkedHashMap<>();
                        farmacia.put("id", linhas.getLong("farmacia_id"));
                        farmacia.put("nome", linhas.getString("farmacia_nome"));
                        farmacia.put("endereco", linhas.getString("farmacia_endereco"));
                        farmacia.put("telefone", linhas.getString("farmacia_telefone"));
                    }
                    pedido.put("farmacia", farmacia);
                    pedido.put("nomeInformado", linhas.getString("nome_informado"));
                    pedido.put("enderecoInformado", linhas.getString("endereco_informado"));
                    pedido.put("cidadeInformada", linhas.getString("cidade_informada"));
                    pedidos.add(pedido);
                }
            }
        }
        Login.json(troca, 200, Json.de(pedidos));
    }

    // FOR UPDATE: dois admins não decidem o mesmo pedido ao mesmo tempo
    private static void decidir(HttpExchange troca, Login.Sessao admin) throws IOException, SQLException {
        String formulario = Login.lerFormulario(troca);
        if (formulario == null) {
            Login.responderErro(troca, 413, "Pedido grande demais.");
            return;
        }
        long id = numero(Login.campo(formulario, "id"));
        String acao = Login.campo(formulario, "acao");
        if (!List.of("aprovar", "recusar", "revogar").contains(acao)) {
            throw new IllegalArgumentException("Ação inválida. Use aprovar, recusar ou revogar.");
        }
        try (Connection conexao = Banco.conectar()) {
            conexao.setAutoCommit(false);
            try {
                Pedido pedido = lerPedido(conexao, id);
                if (pedido == null) {
                    conexao.rollback();
                    Login.responderErro(troca, 404, "Pedido não encontrado.");
                    return;
                }
                String novoStatus = novoStatus(pedido.status(), acao);
                if (acao.equals("aprovar")) {
                    aprovar(conexao, pedido, admin.contaId());
                } else {
                    gravarDecisao(conexao, pedido.id(), novoStatus, admin.contaId());
                }
                conexao.commit();
                Login.json(troca, 200, "{\"ok\":true,\"status\":" + Json.de(novoStatus) + "}");
            } catch (IllegalStateException erro) {
                conexao.rollback();
                Login.responderErro(troca, 409, erro.getMessage());
            } catch (SQLException erro) {
                conexao.rollback();
                if ("23505".equals(erro.getSQLState())) {
                    Login.responderErro(troca, 409, "Já existe uma farmácia com este CNPJ. Recuse este pedido e ligue-o à farmácia existente.");
                } else {
                    throw erro;
                }
            }
        }
    }

    private static String novoStatus(String atual, String acao) {
        return switch (acao) {
            case "aprovar" -> {
                if (!atual.equals("pendente")) throw new IllegalStateException("Este pedido já foi decidido.");
                yield "aprovado";
            }
            case "recusar" -> {
                if (!atual.equals("pendente")) throw new IllegalStateException("Este pedido já foi decidido.");
                yield "recusado";
            }
            default -> {
                if (!atual.equals("aprovado")) throw new IllegalStateException("Só dá para revogar um acesso já aprovado.");
                yield "recusado";
            }
        };
    }

    private static void aprovar(Connection conexao, Pedido pedido, long adminId) throws SQLException {
        Long farmacia = pedido.farmaciaId();
        if (farmacia == null) {
            farmacia = criarFarmacia(conexao, pedido);
        }
        try (PreparedStatement comando = conexao.prepareStatement(
                "UPDATE acesso_farmacia SET status = 'aprovado', decidido_em = now(), decidido_por = ?, "
                + "farmacia_id = ? WHERE id = ?")) {
            comando.setLong(1, adminId);
            comando.setLong(2, farmacia);
            comando.setLong(3, pedido.id());
            comando.executeUpdate();
        }
        try (PreparedStatement comando = conexao.prepareStatement(
                "UPDATE conta SET tipo = 'farmacia' WHERE id = ? AND tipo = 'cidadao'")) {
            comando.setLong(1, pedido.contaId());
            comando.executeUpdate();
        }
    }

    private static long criarFarmacia(Connection conexao, Pedido pedido) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(
                "INSERT INTO farmacia (nome, endereco, cnpj, telefone, cidade) VALUES (?, ?, ?, ?, ?) RETURNING id")) {
            comando.setString(1, pedido.nomeInformado());
            comando.setString(2, pedido.enderecoInformado());
            comando.setString(3, pedido.cnpj());
            comando.setString(4, formatarTelefone(pedido.telefone()));
            comando.setString(5, pedido.cidadeInformada());
            try (ResultSet linha = comando.executeQuery()) {
                linha.next();
                return linha.getLong(1);
            }
        }
    }

    private static void gravarDecisao(Connection conexao, long pedidoId, String status, long adminId) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(
                "UPDATE acesso_farmacia SET status = ?, decidido_em = now(), decidido_por = ? WHERE id = ?")) {
            comando.setString(1, status);
            comando.setLong(2, adminId);
            comando.setLong(3, pedidoId);
            comando.executeUpdate();
        }
    }

    private static Pedido lerPedido(Connection conexao, long id) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(
                "SELECT id, conta_id, farmacia_id, status, cnpj_informado, telefone_informado, nome_informado, "
                + "endereco_informado, cidade_informada FROM acesso_farmacia WHERE id = ? FOR UPDATE")) {
            comando.setLong(1, id);
            try (ResultSet linha = comando.executeQuery()) {
                if (!linha.next()) {
                    return null;
                }
                Long farmacia = linha.getObject("farmacia_id") == null ? null : linha.getLong("farmacia_id");
                return new Pedido(linha.getLong("id"), linha.getLong("conta_id"), farmacia, linha.getString("status"),
                        linha.getString("cnpj_informado"), linha.getString("telefone_informado"),
                        linha.getString("nome_informado"), linha.getString("endereco_informado"),
                        linha.getString("cidade_informada"));
            }
        }
    }

    private static int contar(Connection conexao, String sql) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(sql);
             ResultSet linha = comando.executeQuery()) {
            linha.next();
            return linha.getInt(1);
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
        throw new IllegalArgumentException("Identificador de pedido inválido.");
    }

    static String formatarTelefone(String digitos) {
        if (digitos == null) return null;
        if (digitos.length() == 11) return "(" + digitos.substring(0, 2) + ") " + digitos.substring(2, 7) + "-" + digitos.substring(7);
        if (digitos.length() == 10) return "(" + digitos.substring(0, 2) + ") " + digitos.substring(2, 6) + "-" + digitos.substring(6);
        return digitos;
    }

    private static String data(Timestamp valor) {
        return valor == null ? null : DATA.format(valor.toLocalDateTime());
    }
}
