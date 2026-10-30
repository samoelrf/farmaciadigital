import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class Favoritos {

    static boolean trata(HttpExchange troca, String caminho) throws IOException {
        if (!caminho.equals("/api/favoritos")) {
            return false;
        }
        try {
            if ("GET".equals(troca.getRequestMethod())) {
                listar(troca);
            } else if (Login.pedidoPost(troca)) {
                alterar(troca);
            }
        } catch (IllegalArgumentException erro) {
            Login.responderErro(troca, 400, erro.getMessage());
        } catch (SQLException erro) {
            System.err.println("Erro nos favoritos: " + erro);
            Login.responderErro(troca, 500, "Erro interno no servidor.");
        }
        return true;
    }

    private static void listar(HttpExchange troca) throws IOException, SQLException {
        Login.Sessao sessao = quemPodeFavoritar(troca);
        if (sessao == null) {
            return;
        }
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("farmacias", consultar(
                "SELECT f.id, f.nome, f.endereco FROM favorito_farmacia x JOIN farmacia f ON f.id = x.farmacia_id "
                + "WHERE x.conta_id = ? ORDER BY f.nome", sessao.contaId()));
        dados.put("medicamentos", consultar(
                "SELECT m.id, m.nome FROM favorito_medicamento x JOIN medicamento m ON m.id = x.medicamento_id "
                + "WHERE x.conta_id = ? ORDER BY m.nome", sessao.contaId()));
        Login.json(troca, 200, Json.de(dados));
    }

    private static void alterar(HttpExchange troca) throws IOException, SQLException {
        Login.Sessao sessao = quemPodeFavoritar(troca);
        if (sessao == null) {
            return;
        }
        String formulario = Login.lerFormulario(troca);
        if (formulario == null) {
            Login.responderErro(troca, 413, "Pedido grande demais.");
            return;
        }
        String[] tabelas = tabelasDo(Login.campo(formulario, "tipo"));
        long id = numero(Login.campo(formulario, "id"));
        String acao = Login.campo(formulario, "acao");
        if (!acao.equals("adicionar") && !acao.equals("remover")) {
            throw new IllegalArgumentException("Ação inválida.");
        }
        boolean marcar = acao.equals("adicionar");

        try (Connection conexao = Banco.conectar()) {
            if (!existe(conexao, tabelas[2], id)) {
                Login.responderErro(troca, 404, "Não encontramos esse item.");
                return;
            }
            // nome de tabela e coluna vêm da lista fixa, nunca do formulário
            String sql = marcar
                    ? "INSERT INTO " + tabelas[0] + " (conta_id, " + tabelas[1] + ") VALUES (?, ?) ON CONFLICT DO NOTHING"
                    : "DELETE FROM " + tabelas[0] + " WHERE conta_id = ? AND " + tabelas[1] + " = ?";
            try (PreparedStatement comando = conexao.prepareStatement(sql)) {
                comando.setLong(1, sessao.contaId());
                comando.setLong(2, id);
                comando.executeUpdate();
            }
        }
        Login.json(troca, 200, "{\"favorito\":" + marcar + "}");
    }

    private static Login.Sessao quemPodeFavoritar(HttpExchange troca) throws IOException {
        Login.Sessao sessao = Login.sessaoAtual(troca);
        if (sessao == null) {
            Login.responderErro(troca, 401, "Entre na sua conta para favoritar.");
            return null;
        }
        if ("admin".equals(sessao.tipo())) {
            Login.responderErro(troca, 403, "A conta de administrador não tem favoritos.");
            return null;
        }
        return sessao;
    }

    private static String[] tabelasDo(String tipo) {
        return switch (tipo) {
            case "farmacia" -> new String[] {"favorito_farmacia", "farmacia_id", "farmacia"};
            case "medicamento" -> new String[] {"favorito_medicamento", "medicamento_id", "medicamento"};
            default -> throw new IllegalArgumentException("Tipo de favorito inválido.");
        };
    }

    private static long numero(String texto) {
        try {
            long valor = Long.parseLong(texto);
            if (valor > 0) {
                return valor;
            }
        } catch (NumberFormatException erro) {
        }
        throw new IllegalArgumentException("Identificador inválido.");
    }

    private static boolean existe(Connection conexao, String tabela, long id) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement("SELECT 1 FROM " + tabela + " WHERE id = ?")) {
            comando.setLong(1, id);
            try (ResultSet linha = comando.executeQuery()) {
                return linha.next();
            }
        }
    }

    private static List<Map<String, Object>> consultar(String sql, long contaId) throws SQLException {
        List<Map<String, Object>> lista = new ArrayList<>();
        try (Connection conexao = Banco.conectar();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setLong(1, contaId);
            try (ResultSet linhas = comando.executeQuery()) {
                ResultSetMetaData colunas = linhas.getMetaData();
                while (linhas.next()) {
                    Map<String, Object> linha = new LinkedHashMap<>();
                    for (int i = 1; i <= colunas.getColumnCount(); i++) {
                        linha.put(colunas.getColumnLabel(i), linhas.getObject(i));
                    }
                    lista.add(linha);
                }
            }
        }
        return lista;
    }
}
