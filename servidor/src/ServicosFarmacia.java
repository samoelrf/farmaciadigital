import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Serviços gratuitos da farmácia (aferição de pressão, teste de hemoglobina...).
// /api/painel/servicos: só a farmácia da conta (o id vem do banco, nunca do pedido).
// /api/servicos?farmacia=ID: público, só os serviços ativos.
class ServicosFarmacia {

    static boolean trata(HttpExchange troca, String caminho) throws IOException {
        boolean painel = caminho.equals("/api/painel/servicos");
        if (!painel && !caminho.equals("/api/servicos")) {
            return false;
        }
        try {
            boolean leitura = "GET".equals(troca.getRequestMethod());
            if (!painel) {
                if (leitura) {
                    publicos(troca);
                } else {
                    Servidor.enviarTexto(troca, 405, "Método não permitido");
                }
            } else if (leitura) {
                listar(troca);
            } else if (Login.pedidoPost(troca)) {
                alterar(troca);
            }
        } catch (IllegalArgumentException erro) {
            Login.responderErro(troca, 400, erro.getMessage());
        } catch (SQLException erro) {
            System.err.println("Erro nos serviços da farmácia: " + erro);
            Login.responderErro(troca, 500, "Erro interno no servidor.");
        }
        return true;
    }

    private static void publicos(HttpExchange troca) throws IOException, SQLException {
        String texto = Login.campo(troca.getRequestURI().getRawQuery() == null ? "" : troca.getRequestURI().getRawQuery(), "farmacia");
        long farmacia;
        try {
            farmacia = Long.parseLong(texto.trim());
        } catch (NumberFormatException erro) {
            throw new IllegalArgumentException("Farmácia inválida.");
        }
        List<Map<String, Object>> lista = servicosDaFarmacia(farmacia, true);
        Login.json(troca, 200, Json.de(lista));
    }

    private static void listar(HttpExchange troca) throws IOException, SQLException {
        PainelFarmacia.Contexto contexto = PainelFarmacia.exigirFarmacia(troca);
        if (contexto == null) {
            return;
        }
        List<Map<String, Object>> catalogo = new ArrayList<>();
        try (Connection conexao = Banco.conectar();
             PreparedStatement comando = conexao.prepareStatement("SELECT id, nome, descricao FROM servico_gratuito ORDER BY id");
             ResultSet linhas = comando.executeQuery()) {
            while (linhas.next()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", linhas.getLong("id"));
                item.put("nome", linhas.getString("nome"));
                item.put("descricao", linhas.getString("descricao"));
                catalogo.add(item);
            }
        }
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("catalogo", catalogo);
        resposta.put("servicos", servicosDaFarmacia(contexto.farmaciaId(), false));
        Login.json(troca, 200, Json.de(resposta));
    }

    private static List<Map<String, Object>> servicosDaFarmacia(long farmacia, boolean soAtivos) throws SQLException {
        String sql = "SELECT fs.id, fs.servico_id, COALESCE(s.nome, fs.nome_personalizado) AS nome, fs.dias_horarios, "
                + "fs.exige_agendamento, fs.observacao, fs.ativo FROM farmacia_servico fs "
                + "LEFT JOIN servico_gratuito s ON s.id = fs.servico_id WHERE fs.farmacia_id = ? "
                + (soAtivos ? "AND fs.ativo AND EXISTS (SELECT 1 FROM farmacia f WHERE f.id = fs.farmacia_id AND f.situacao = 'aprovada') " : "") + "ORDER BY COALESCE(s.nome, fs.nome_personalizado)";
        List<Map<String, Object>> lista = new ArrayList<>();
        try (Connection conexao = Banco.conectar();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setLong(1, farmacia);
            try (ResultSet linhas = comando.executeQuery()) {
                while (linhas.next()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    if (!soAtivos) {
                        item.put("id", linhas.getLong("id"));
                        item.put("servicoId", linhas.getObject("servico_id"));
                        item.put("ativo", linhas.getBoolean("ativo"));
                    }
                    item.put("nome", linhas.getString("nome"));
                    item.put("diasHorarios", linhas.getString("dias_horarios"));
                    item.put("exigeAgendamento", linhas.getBoolean("exige_agendamento"));
                    item.put("observacao", linhas.getString("observacao"));
                    lista.add(item);
                }
            }
        }
        return lista;
    }

    private static void alterar(HttpExchange troca) throws IOException, SQLException {
        PainelFarmacia.Contexto contexto = PainelFarmacia.exigirFarmacia(troca);
        if (contexto == null) {
            return;
        }
        String formulario = Login.lerFormulario(troca);
        if (formulario == null) {
            Login.responderErro(troca, 413, "Pedido grande demais.");
            return;
        }
        String acao = Login.campo(formulario, "acao");
        if (acao.equals("remover")) {
            remover(contexto.farmaciaId(), idDoRegistro(formulario));
        } else if (acao.equals("salvar")) {
            salvar(contexto.farmaciaId(), formulario);
        } else {
            throw new IllegalArgumentException("Ação inválida.");
        }
        Login.json(troca, 200, "{\"ok\":true}");
    }

    private static void salvar(long farmacia, String formulario) throws SQLException {
        String idTexto = Login.campo(formulario, "id").trim();
        String servicoTexto = Login.campo(formulario, "servico").trim();
        String proprio = Login.campo(formulario, "nome_personalizado").trim();
        String dias = Login.campo(formulario, "dias_horarios").trim();
        String observacao = Login.campo(formulario, "observacao").trim();
        boolean agendamento = Login.campo(formulario, "exige_agendamento").equals("sim");
        boolean ativo = !Login.campo(formulario, "ativo").equals("nao");

        if (dias.length() > 200) {
            throw new IllegalArgumentException("Dias e horários: até 200 caracteres.");
        }
        if (observacao.length() > 300) {
            throw new IllegalArgumentException("Observação: até 300 caracteres.");
        }

        Long servicoId = null;
        String nomeProprio = null;
        if (!servicoTexto.isEmpty()) {
            try {
                servicoId = Long.parseLong(servicoTexto);
            } catch (NumberFormatException erro) {
                throw new IllegalArgumentException("Serviço inválido.");
            }
            if (!existeNoCatalogo(servicoId)) {
                throw new IllegalArgumentException("Serviço não encontrado.");
            }
        } else {
            if (proprio.length() < 3 || proprio.length() > 80) {
                throw new IllegalArgumentException("Escolha um serviço da lista ou digite o nome (de 3 a 80 caracteres).");
            }
            nomeProprio = proprio;
        }

        try (Connection conexao = Banco.conectar()) {
            if (idTexto.isEmpty()) {
                try (PreparedStatement comando = conexao.prepareStatement(
                        "INSERT INTO farmacia_servico (farmacia_id, servico_id, nome_personalizado, dias_horarios, "
                                + "exige_agendamento, observacao, ativo) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    comando.setLong(1, farmacia);
                    if (servicoId == null) comando.setNull(2, java.sql.Types.INTEGER); else comando.setLong(2, servicoId);
                    comando.setString(3, nomeProprio);
                    comando.setString(4, dias.isEmpty() ? null : dias);
                    comando.setBoolean(5, agendamento);
                    comando.setString(6, observacao.isEmpty() ? null : observacao);
                    comando.setBoolean(7, ativo);
                    comando.executeUpdate();
                }
            } else {
                long id = idDoRegistro(formulario);
                try (PreparedStatement comando = conexao.prepareStatement(
                        "UPDATE farmacia_servico SET servico_id = ?, nome_personalizado = ?, dias_horarios = ?, "
                                + "exige_agendamento = ?, observacao = ?, ativo = ?, atualizado_em = now() "
                                + "WHERE id = ? AND farmacia_id = ?")) {
                    if (servicoId == null) comando.setNull(1, java.sql.Types.INTEGER); else comando.setLong(1, servicoId);
                    comando.setString(2, nomeProprio);
                    comando.setString(3, dias.isEmpty() ? null : dias);
                    comando.setBoolean(4, agendamento);
                    comando.setString(5, observacao.isEmpty() ? null : observacao);
                    comando.setBoolean(6, ativo);
                    comando.setLong(7, id);
                    comando.setLong(8, farmacia);
                    if (comando.executeUpdate() == 0) {
                        throw new IllegalArgumentException("Serviço não encontrado.");
                    }
                }
            }
        } catch (SQLException erro) {
            if ("23505".equals(erro.getSQLState())) {
                throw new IllegalArgumentException("Este serviço já está cadastrado na sua farmácia.");
            }
            throw erro;
        }
    }

    private static void remover(long farmacia, long id) throws SQLException {
        try (Connection conexao = Banco.conectar();
             PreparedStatement comando = conexao.prepareStatement("DELETE FROM farmacia_servico WHERE id = ? AND farmacia_id = ?")) {
            comando.setLong(1, id);
            comando.setLong(2, farmacia);
            if (comando.executeUpdate() == 0) {
                throw new IllegalArgumentException("Serviço não encontrado.");
            }
        }
    }

    private static long idDoRegistro(String formulario) {
        try {
            long id = Long.parseLong(Login.campo(formulario, "id").trim());
            if (id > 0) {
                return id;
            }
        } catch (NumberFormatException erro) {
        }
        throw new IllegalArgumentException("Serviço inválido.");
    }

    private static boolean existeNoCatalogo(long id) throws SQLException {
        try (Connection conexao = Banco.conectar();
             PreparedStatement comando = conexao.prepareStatement("SELECT 1 FROM servico_gratuito WHERE id = ?")) {
            comando.setLong(1, id);
            try (ResultSet linha = comando.executeQuery()) {
                return linha.next();
            }
        }
    }
}
