import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Estoque por lote da farmácia da conta (/api/painel/estoque). O id da farmácia vem do banco, nunca do pedido.
// Cada lote tem validade, quantidade e preço. A quantidade e o preço que a busca pública usa
// (tabelas estoque e preco) são recalculados a cada alteração: só entram lotes que não venceram.
class EstoqueFarmacia {

    private static final int DIAS_AVISO_VALIDADE = 30;
    private static final Set<String> TIPOS = Set.of("referencia", "generico", "similar");

    static boolean trata(HttpExchange troca, String caminho) throws IOException {
        if (!caminho.equals("/api/painel/estoque")) {
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
            System.err.println("Erro no estoque da farmácia: " + erro);
            Login.responderErro(troca, 500, "Erro interno no servidor.");
        }
        return true;
    }

    // ----- leitura -----

    private static void listar(HttpExchange troca) throws IOException, SQLException {
        PainelFarmacia.Contexto contexto = PainelFarmacia.exigirFarmacia(troca);
        if (contexto == null) {
            return;
        }
        long farmacia = contexto.farmaciaId();
        LocalDate hoje = LocalDate.now();

        Map<Long, Map<String, Object>> remedios = new LinkedHashMap<>();
        Map<Long, List<Map<String, Object>>> lotesPorRemedio = new LinkedHashMap<>();
        Map<Long, Integer> validos = new LinkedHashMap<>();
        Map<Long, Integer> registradas = new LinkedHashMap<>();

        String sqlRemedios = "SELECT m.id, m.nome, m.apresentacao, m.tipo, m.exige_receita, "
                + "COALESCE(e.estoque_minimo, 0) AS minimo, COALESCE(e.quantidade, 0) AS qtd_registrada, p.preco "
                + "FROM (SELECT medicamento_id FROM estoque WHERE farmacia_id = ? "
                + "      UNION SELECT medicamento_id FROM estoque_lote WHERE farmacia_id = ? "
                + "      UNION SELECT medicamento_id FROM preco WHERE farmacia_id = ?) par "
                + "JOIN medicamento m ON m.id = par.medicamento_id "
                + "LEFT JOIN estoque e ON e.farmacia_id = ? AND e.medicamento_id = m.id "
                + "LEFT JOIN preco p ON p.farmacia_id = ? AND p.medicamento_id = m.id "
                + "ORDER BY m.nome, m.apresentacao";
        String sqlLotes = "SELECT id, medicamento_id, lote, validade, quantidade, preco_custo, preco_venda, fornecedor, entrada_em "
                + "FROM estoque_lote WHERE farmacia_id = ? ORDER BY validade, lote";

        try (Connection conexao = Banco.conectar()) {
            try (PreparedStatement comando = conexao.prepareStatement(sqlRemedios)) {
                for (int i = 1; i <= 5; i++) {
                    comando.setLong(i, farmacia);
                }
                try (ResultSet linhas = comando.executeQuery()) {
                    while (linhas.next()) {
                        Map<String, Object> remedio = new LinkedHashMap<>();
                        long id = linhas.getLong("id");
                        remedio.put("id", id);
                        remedio.put("nome", linhas.getString("nome"));
                        remedio.put("apresentacao", linhas.getString("apresentacao"));
                        remedio.put("tipo", linhas.getString("tipo"));
                        remedio.put("exigeReceita", linhas.getBoolean("exige_receita"));
                        remedio.put("minimo", linhas.getInt("minimo"));
                        remedio.put("precoAtual", linhas.getBigDecimal("preco"));
                        remedios.put(id, remedio);
                        lotesPorRemedio.put(id, new ArrayList<>());
                        validos.put(id, 0);
                        registradas.put(id, linhas.getInt("qtd_registrada"));
                    }
                }
            }
            int vencidos = 0;
            int vencemEmBreve = 0;
            try (PreparedStatement comando = conexao.prepareStatement(sqlLotes)) {
                comando.setLong(1, farmacia);
                try (ResultSet linhas = comando.executeQuery()) {
                    while (linhas.next()) {
                        long remedioId = linhas.getLong("medicamento_id");
                        LocalDate validade = linhas.getDate("validade").toLocalDate();
                        long dias = ChronoUnit.DAYS.between(hoje, validade);
                        String situacao = dias < 0 ? "vencido" : dias <= DIAS_AVISO_VALIDADE ? "vence_em_breve" : "ok";
                        int quantidade = linhas.getInt("quantidade");
                        Map<String, Object> lote = new LinkedHashMap<>();
                        lote.put("id", linhas.getLong("id"));
                        lote.put("lote", linhas.getString("lote"));
                        lote.put("validade", validade.toString());
                        lote.put("diasParaVencer", dias);
                        lote.put("situacao", situacao);
                        lote.put("quantidade", quantidade);
                        lote.put("precoVenda", linhas.getBigDecimal("preco_venda"));
                        lote.put("precoCusto", linhas.getBigDecimal("preco_custo"));
                        lote.put("fornecedor", linhas.getString("fornecedor"));
                        lote.put("entradaEm", linhas.getDate("entrada_em").toLocalDate().toString());
                        List<Map<String, Object>> lista = lotesPorRemedio.get(remedioId);
                        if (lista != null) {
                            lista.add(lote);
                        }
                        if (situacao.equals("vencido")) {
                            if (quantidade > 0) vencidos++;
                        } else {
                            validos.merge(remedioId, quantidade, Integer::sum);
                            if (situacao.equals("vence_em_breve") && quantidade > 0) vencemEmBreve++;
                        }
                    }
                }
            }
            int abaixoDoMinimo = 0;
            for (Map.Entry<Long, Map<String, Object>> par : remedios.entrySet()) {
                Map<String, Object> remedio = par.getValue();
                // sem lote cadastrado (dado antigo): vale a quantidade que já estava registrada
                boolean semLote = lotesPorRemedio.get(par.getKey()).isEmpty();
                int disponivel = semLote ? registradas.get(par.getKey()) : validos.get(par.getKey());
                int minimo = (Integer) remedio.get("minimo");
                boolean abaixo = minimo > 0 && disponivel < minimo;
                if (abaixo) abaixoDoMinimo++;
                remedio.put("quantidadeValida", disponivel);
                remedio.put("semLote", semLote);
                remedio.put("abaixoDoMinimo", abaixo);
                remedio.put("lotes", lotesPorRemedio.get(par.getKey()));
            }
            Map<String, Object> resumo = new LinkedHashMap<>();
            resumo.put("lotesVencidos", vencidos);
            resumo.put("lotesVencendo", vencemEmBreve);
            resumo.put("abaixoDoMinimo", abaixoDoMinimo);
            resumo.put("diasAviso", DIAS_AVISO_VALIDADE);
            Map<String, Object> resposta = new LinkedHashMap<>();
            resposta.put("resumo", resumo);
            resposta.put("remedios", new ArrayList<>(remedios.values()));
            Login.json(troca, 200, Json.de(resposta));
        }
    }

    // ----- escrita -----

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
        long farmacia = contexto.farmaciaId();

        try (Connection conexao = Banco.conectar()) {
            conexao.setAutoCommit(false);
            try {
                switch (acao) {
                    case "salvar" -> salvarLote(conexao, contexto, formulario);
                    case "remover" -> removerLote(conexao, contexto, formulario);
                    case "minimo" -> {
                        long remedio = remedioExistente(conexao, Login.campo(formulario, "medicamento"));
                        Integer minimo = inteiro(Login.campo(formulario, "estoque_minimo"), "Estoque mínimo", 100000);
                        if (minimo == null) {
                            throw new IllegalArgumentException("Informe o estoque mínimo.");
                        }
                        sincronizar(conexao, farmacia, remedio, contexto.contaId(), minimo);
                    }
                    default -> throw new IllegalArgumentException("Ação inválida.");
                }
                conexao.commit();
            } catch (IllegalArgumentException | SQLException erro) {
                conexao.rollback();
                if (erro instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                    throw new IllegalArgumentException("Já existe um remédio com esse nome e apresentação.");
                }
                throw erro;
            }
        }
        Login.json(troca, 200, "{\"ok\":true}");
    }

    private static void salvarLote(Connection conexao, PainelFarmacia.Contexto contexto, String formulario) throws SQLException {
        String lote = Login.campo(formulario, "lote").trim();
        if (lote.isEmpty() || lote.length() > 40) {
            throw new IllegalArgumentException("Informe o lote (até 40 caracteres).");
        }
        LocalDate validade;
        try {
            validade = LocalDate.parse(Login.campo(formulario, "validade").trim());
        } catch (DateTimeParseException erro) {
            throw new IllegalArgumentException("Informe a validade do lote.");
        }
        LocalDate hoje = LocalDate.now();
        if (validade.isAfter(hoje.plusYears(15))) {
            throw new IllegalArgumentException("Validade muito distante. Confira a data.");
        }
        Integer quantidade = inteiro(Login.campo(formulario, "quantidade"), "Quantidade", 1000000);
        if (quantidade == null) {
            throw new IllegalArgumentException("Informe a quantidade.");
        }
        if (validade.isBefore(hoje) && quantidade > 0) {
            throw new IllegalArgumentException("Este lote já venceu. Informe quantidade 0 ou remova o lote.");
        }
        BigDecimal precoVenda = preco(Login.campo(formulario, "preco_venda"), "Preço de venda", true);
        BigDecimal precoCusto = preco(Login.campo(formulario, "preco_custo"), "Preço de custo", false);
        String fornecedor = Login.campo(formulario, "fornecedor").trim();
        if (fornecedor.length() > 100) {
            throw new IllegalArgumentException("Fornecedor: até 100 caracteres.");
        }
        Integer minimo = inteiro(Login.campo(formulario, "estoque_minimo"), "Estoque mínimo", 100000);

        long remedio = medicamentoDoFormulario(conexao, contexto.farmaciaId(), formulario);

        try (PreparedStatement comando = conexao.prepareStatement(
                "INSERT INTO estoque_lote (farmacia_id, medicamento_id, lote, validade, quantidade, preco_custo, preco_venda, "
                        + "fornecedor, atualizado_em) VALUES (?, ?, ?, ?, ?, ?, ?, ?, now()) "
                        + "ON CONFLICT (farmacia_id, medicamento_id, lote) DO UPDATE SET validade = EXCLUDED.validade, "
                        + "quantidade = EXCLUDED.quantidade, preco_custo = EXCLUDED.preco_custo, "
                        + "preco_venda = EXCLUDED.preco_venda, fornecedor = EXCLUDED.fornecedor, atualizado_em = now()")) {
            comando.setLong(1, contexto.farmaciaId());
            comando.setLong(2, remedio);
            comando.setString(3, lote);
            comando.setDate(4, Date.valueOf(validade));
            comando.setInt(5, quantidade);
            comando.setBigDecimal(6, precoCusto);
            comando.setBigDecimal(7, precoVenda);
            comando.setString(8, fornecedor.isEmpty() ? null : fornecedor);
            comando.executeUpdate();
        }
        sincronizar(conexao, contexto.farmaciaId(), remedio, contexto.contaId(), minimo);
    }

    private static void removerLote(Connection conexao, PainelFarmacia.Contexto contexto, String formulario) throws SQLException {
        long id;
        try {
            id = Long.parseLong(Login.campo(formulario, "lote_id").trim());
        } catch (NumberFormatException erro) {
            throw new IllegalArgumentException("Lote inválido.");
        }
        long remedio;
        try (PreparedStatement comando = conexao.prepareStatement(
                "DELETE FROM estoque_lote WHERE id = ? AND farmacia_id = ? RETURNING medicamento_id")) {
            comando.setLong(1, id);
            comando.setLong(2, contexto.farmaciaId());
            try (ResultSet linha = comando.executeQuery()) {
                if (!linha.next()) {
                    throw new IllegalArgumentException("Lote não encontrado.");
                }
                remedio = linha.getLong(1);
            }
        }
        sincronizar(conexao, contexto.farmaciaId(), remedio, contexto.contaId(), null);
    }

    // Recalcula o que a busca pública enxerga: quantidade = soma dos lotes que não venceram;
    // preço = o do lote que vence primeiro (o que deve sair primeiro), se ainda houver lote com saldo.
    private static void sincronizar(Connection conexao, long farmacia, long remedio, long contaId, Integer minimo) throws SQLException {
        int total;
        try (PreparedStatement comando = conexao.prepareStatement(
                "SELECT COALESCE(SUM(quantidade), 0) FROM estoque_lote "
                        + "WHERE farmacia_id = ? AND medicamento_id = ? AND validade >= current_date")) {
            comando.setLong(1, farmacia);
            comando.setLong(2, remedio);
            try (ResultSet linha = comando.executeQuery()) {
                linha.next();
                total = linha.getInt(1);
            }
        }
        String atualizaMinimo = minimo == null ? "" : ", estoque_minimo = EXCLUDED.estoque_minimo";
        try (PreparedStatement comando = conexao.prepareStatement(
                "INSERT INTO estoque (farmacia_id, medicamento_id, quantidade, estoque_minimo, atualizado_em) "
                        + "VALUES (?, ?, ?, ?, now()) ON CONFLICT (farmacia_id, medicamento_id) "
                        + "DO UPDATE SET quantidade = EXCLUDED.quantidade, atualizado_em = now()" + atualizaMinimo)) {
            comando.setLong(1, farmacia);
            comando.setLong(2, remedio);
            comando.setInt(3, total);
            comando.setInt(4, minimo == null ? 0 : minimo);
            comando.executeUpdate();
        }
        try (PreparedStatement comando = conexao.prepareStatement(
                "SELECT preco_venda FROM estoque_lote WHERE farmacia_id = ? AND medicamento_id = ? "
                        + "AND validade >= current_date AND quantidade > 0 ORDER BY validade, id LIMIT 1")) {
            comando.setLong(1, farmacia);
            comando.setLong(2, remedio);
            try (ResultSet linha = comando.executeQuery()) {
                if (linha.next()) {
                    PainelFarmacia.gravarPreco(conexao, farmacia, remedio, linha.getBigDecimal(1), contaId);
                }
            }
        }
    }

    // ----- remédio: um que já existe, ou um novo cadastrado pela farmácia -----

    private static long medicamentoDoFormulario(Connection conexao, long farmacia, String formulario) throws SQLException {
        String escolhido = Login.campo(formulario, "medicamento").trim();
        if (!escolhido.isEmpty()) {
            return remedioExistente(conexao, escolhido);
        }
        String nome = Login.campo(formulario, "novo_nome").trim();
        String apresentacao = Login.campo(formulario, "novo_apresentacao").trim();
        String principio = Login.campo(formulario, "novo_principio_ativo").trim();
        String tipo = Login.campo(formulario, "novo_tipo").trim();
        boolean receita = Login.campo(formulario, "novo_exige_receita").equals("sim");
        if (nome.length() < 3 || nome.length() > 100) {
            throw new IllegalArgumentException("Escolha o remédio na busca ou digite o nome (de 3 a 100 caracteres).");
        }
        if (apresentacao.length() > 100 || principio.length() > 100) {
            throw new IllegalArgumentException("Apresentação e princípio ativo: até 100 caracteres.");
        }
        if (!tipo.isEmpty() && !TIPOS.contains(tipo)) {
            throw new IllegalArgumentException("Tipo inválido. Use referência, genérico ou similar.");
        }
        // mesmo nome e apresentação: reaproveita o que já existe, para o catálogo não duplicar
        try (PreparedStatement comando = conexao.prepareStatement(
                "SELECT id FROM medicamento WHERE lower(nome) = lower(?) AND lower(COALESCE(apresentacao, '')) = lower(?) LIMIT 1")) {
            comando.setString(1, nome);
            comando.setString(2, apresentacao);
            try (ResultSet linha = comando.executeQuery()) {
                if (linha.next()) {
                    return linha.getLong(1);
                }
            }
        }
        try (PreparedStatement comando = conexao.prepareStatement(
                "INSERT INTO medicamento (nome, principio_ativo, apresentacao, tipo, exige_receita, farmacia_popular, "
                        + "criado_por_farmacia_id) VALUES (?, ?, ?, ?, ?, FALSE, ?) RETURNING id")) {
            comando.setString(1, nome);
            comando.setString(2, principio.isEmpty() ? null : principio);
            comando.setString(3, apresentacao.isEmpty() ? null : apresentacao);
            comando.setString(4, tipo.isEmpty() ? null : tipo);
            comando.setBoolean(5, receita);
            comando.setLong(6, farmacia);
            try (ResultSet linha = comando.executeQuery()) {
                linha.next();
                return linha.getLong(1);
            }
        }
    }

    private static long remedioExistente(Connection conexao, String texto) throws SQLException {
        long id;
        try {
            id = Long.parseLong(texto.trim());
        } catch (NumberFormatException erro) {
            throw new IllegalArgumentException("Remédio inválido.");
        }
        try (PreparedStatement comando = conexao.prepareStatement("SELECT 1 FROM medicamento WHERE id = ?")) {
            comando.setLong(1, id);
            try (ResultSet linha = comando.executeQuery()) {
                if (!linha.next()) {
                    throw new IllegalArgumentException("Remédio não encontrado.");
                }
            }
        }
        return id;
    }

    // ----- validação -----

    // vazio devolve null; valor fora de 0..maximo ou não numérico é erro
    private static Integer inteiro(String texto, String rotulo, int maximo) {
        if (texto.trim().isEmpty()) {
            return null;
        }
        try {
            int valor = Integer.parseInt(texto.trim());
            if (valor >= 0 && valor <= maximo) {
                return valor;
            }
        } catch (NumberFormatException erro) {
        }
        throw new IllegalArgumentException(rotulo + " precisa ser um número inteiro de 0 a " + maximo + ".");
    }

    private static BigDecimal preco(String texto, String rotulo, boolean obrigatorio) {
        if (texto.trim().isEmpty()) {
            if (obrigatorio) {
                throw new IllegalArgumentException(rotulo + ": informe o valor, por exemplo 12,50.");
            }
            return null;
        }
        try {
            BigDecimal valor = new BigDecimal(texto.trim().replace(",", "."));
            if (valor.signum() < 0 || valor.compareTo(new BigDecimal("9999.99")) > 0 || valor.scale() > 2) {
                throw new IllegalArgumentException(rotulo + " precisa estar entre 0 e 9999,99 (com até 2 casas).");
            }
            return valor;
        } catch (NumberFormatException erro) {
            throw new IllegalArgumentException(rotulo + " precisa ser um número, por exemplo 12,50.");
        }
    }
}
