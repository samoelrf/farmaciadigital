import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Doação de medicamentos e pedidos de quem não tem como pagar.
// Público: GET /api/doacoes/farmacias.
// Conta logada (cidadão ou farmácia): /api/doacoes, /api/necessidades e as listas "minhas".
// Farmácia aprovada: /api/painel/doacoes. O id da farmácia vem da sessão e do banco, nunca do pedido.
class Doacoes {

    private static final int QUANTIDADE_MAXIMA_DOACAO = 10000;
    private static final int QUANTIDADE_MAXIMA_PEDIDO = 1000;
    private static final int PEDIDOS_ABERTOS_POR_CONTA = 5;
    private static final int DIAS_MINIMOS_DE_VALIDADE = 30;

    static boolean trata(HttpExchange troca, String caminho) throws IOException {
        switch (caminho) {
            case "/api/doacoes/farmacias", "/api/doacoes/minhas", "/api/doacoes",
                 "/api/necessidades/minhas", "/api/necessidades", "/api/necessidades/cancelar",
                 "/api/painel/doacoes" -> { }
            default -> {
                return false;
            }
        }
        try {
            boolean leitura = "GET".equals(troca.getRequestMethod());
            switch (caminho) {
                case "/api/doacoes/farmacias" -> {
                    if (leitura) farmacias(troca); else Servidor.enviarTexto(troca, 405, "Método não permitido");
                }
                case "/api/doacoes/minhas" -> {
                    if (leitura) minhasDoacoes(troca); else Servidor.enviarTexto(troca, 405, "Método não permitido");
                }
                case "/api/necessidades/minhas" -> {
                    if (leitura) minhasNecessidades(troca); else Servidor.enviarTexto(troca, 405, "Método não permitido");
                }
                case "/api/doacoes" -> {
                    if (!leitura && Login.pedidoPost(troca)) criarDoacao(troca);
                    else if (leitura) Servidor.enviarTexto(troca, 405, "Método não permitido");
                }
                case "/api/necessidades" -> {
                    if (!leitura && Login.pedidoPost(troca)) criarNecessidade(troca);
                    else if (leitura) Servidor.enviarTexto(troca, 405, "Método não permitido");
                }
                case "/api/necessidades/cancelar" -> {
                    if (!leitura && Login.pedidoPost(troca)) cancelarNecessidade(troca);
                    else if (leitura) Servidor.enviarTexto(troca, 405, "Método não permitido");
                }
                case "/api/painel/doacoes" -> {
                    if (leitura) painel(troca); else if (Login.pedidoPost(troca)) acaoPainel(troca);
                }
                default -> Servidor.enviarTexto(troca, 404, "Página não encontrada");
            }
        } catch (IllegalArgumentException erro) {
            Login.responderErro(troca, 400, erro.getMessage());
        } catch (SQLException erro) {
            System.err.println("Erro nas doações em " + caminho + ": " + erro);
            Login.responderErro(troca, 500, "Erro interno no servidor.");
        }
        return true;
    }

    // ----- público -----

    private static void farmacias(HttpExchange troca) throws IOException, SQLException {
        String sql = "SELECT id, nome, endereco FROM farmacia WHERE situacao = 'aprovada' ORDER BY nome";
        List<Object> lista = new ArrayList<>();
        try (Connection conexao = Banco.conectar();
             PreparedStatement comando = conexao.prepareStatement(sql);
             ResultSet linhas = comando.executeQuery()) {
            while (linhas.next()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", linhas.getLong("id"));
                item.put("nome", linhas.getString("nome"));
                item.put("endereco", linhas.getString("endereco"));
                lista.add(item);
            }
        }
        Login.json(troca, 200, Json.de(lista));
    }

    // ----- conta logada: doador e quem precisa -----

    private static void criarDoacao(HttpExchange troca) throws IOException, SQLException {
        Login.Sessao sessao = usuario(troca);
        if (sessao == null) {
            return;
        }
        String formulario = formulario(troca);
        if (formulario == null) {
            return;
        }
        long medicamento = idObrigatorio(Login.campo(formulario, "medicamento"), "Escolha o remédio.");
        long farmacia = idObrigatorio(Login.campo(formulario, "farmacia"), "Escolha a farmácia onde vai entregar.");
        int quantidade = inteiro(Login.campo(formulario, "quantidade"), "Quantidade", 1, QUANTIDADE_MAXIMA_DOACAO);
        LocalDate validade = data(Login.campo(formulario, "validade"));
        if (validade.isBefore(LocalDate.now().plusDays(DIAS_MINIMOS_DE_VALIDADE))) {
            throw new IllegalArgumentException("A validade precisa ser de pelo menos "
                    + DIAS_MINIMOS_DE_VALIDADE + " dias à frente. Medicamento perto de vencer não é repassado.");
        }

        try (Connection conexao = Banco.conectar()) {
            if (!medicamentoExiste(conexao, medicamento)) {
                throw new IllegalArgumentException("Remédio não encontrado.");
            }
            if (!farmaciaAprovada(conexao, farmacia)) {
                throw new IllegalArgumentException("Esta farmácia não está disponível para receber doações.");
            }
            String sql = "INSERT INTO doacao (conta_id, farmacia_id, medicamento_id, quantidade_inicial, "
                    + "quantidade_restante, validade) VALUES (?, ?, ?, ?, ?, ?) RETURNING id";
            try (PreparedStatement comando = conexao.prepareStatement(sql)) {
                comando.setLong(1, sessao.contaId());
                comando.setLong(2, farmacia);
                comando.setLong(3, medicamento);
                comando.setInt(4, quantidade);
                comando.setInt(5, quantidade);
                comando.setDate(6, java.sql.Date.valueOf(validade));
                try (ResultSet linha = comando.executeQuery()) {
                    linha.next();
                    Login.json(troca, 201, "{\"ok\":true,\"id\":" + linha.getLong(1) + "}");
                }
            }
        }
    }

    private static void minhasDoacoes(HttpExchange troca) throws IOException, SQLException {
        Login.Sessao sessao = usuario(troca);
        if (sessao == null) {
            return;
        }
        String sql = "SELECT d.id, m.nome AS medicamento, f.nome AS farmacia, d.quantidade_inicial, "
                + "d.quantidade_restante, d.validade, d.status, d.criado_em "
                + "FROM doacao d JOIN medicamento m ON m.id = d.medicamento_id "
                + "JOIN farmacia f ON f.id = d.farmacia_id "
                + "WHERE d.conta_id = ? ORDER BY d.criado_em DESC LIMIT 50";
        List<Object> lista = new ArrayList<>();
        try (Connection conexao = Banco.conectar();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setLong(1, sessao.contaId());
            try (ResultSet linhas = comando.executeQuery()) {
                while (linhas.next()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", linhas.getLong("id"));
                    item.put("medicamento", linhas.getString("medicamento"));
                    item.put("farmacia", linhas.getString("farmacia"));
                    item.put("quantidadeInicial", linhas.getInt("quantidade_inicial"));
                    item.put("quantidadeRestante", linhas.getInt("quantidade_restante"));
                    item.put("validade", linhas.getDate("validade").toLocalDate().toString());
                    item.put("status", linhas.getString("status"));
                    item.put("criadoEm", linhas.getTimestamp("criado_em").toLocalDateTime().toString());
                    lista.add(item);
                }
            }
        }
        Login.json(troca, 200, Json.de(lista));
    }

    private static void criarNecessidade(HttpExchange troca) throws IOException, SQLException {
        Login.Sessao sessao = usuario(troca);
        if (sessao == null) {
            return;
        }
        String formulario = formulario(troca);
        if (formulario == null) {
            return;
        }
        long medicamento = idObrigatorio(Login.campo(formulario, "medicamento"), "Escolha o remédio que você precisa.");
        int quantidade = inteiro(Login.campo(formulario, "quantidade"), "Quantidade", 1, QUANTIDADE_MAXIMA_PEDIDO);

        try (Connection conexao = Banco.conectar()) {
            if (!medicamentoExiste(conexao, medicamento)) {
                throw new IllegalArgumentException("Remédio não encontrado.");
            }
            if (contarPedidosAbertos(conexao, sessao.contaId()) >= PEDIDOS_ABERTOS_POR_CONTA) {
                throw new IllegalArgumentException("Você já tem " + PEDIDOS_ABERTOS_POR_CONTA
                        + " pedidos abertos. Cancele um antes de pedir outro.");
            }
            String sql = "INSERT INTO necessidade (conta_id, medicamento_id, quantidade, quantidade_restante) "
                    + "VALUES (?, ?, ?, ?) RETURNING id";
            try (PreparedStatement comando = conexao.prepareStatement(sql)) {
                comando.setLong(1, sessao.contaId());
                comando.setLong(2, medicamento);
                comando.setInt(3, quantidade);
                comando.setInt(4, quantidade);
                try (ResultSet linha = comando.executeQuery()) {
                    linha.next();
                    Login.json(troca, 201, "{\"ok\":true,\"id\":" + linha.getLong(1) + "}");
                }
            }
        }
    }

    private static void minhasNecessidades(HttpExchange troca) throws IOException, SQLException {
        Login.Sessao sessao = usuario(troca);
        if (sessao == null) {
            return;
        }
        String sql = "SELECT n.id, m.nome AS medicamento, n.quantidade, n.quantidade_restante, n.status, n.criado_em "
                + "FROM necessidade n JOIN medicamento m ON m.id = n.medicamento_id "
                + "WHERE n.conta_id = ? ORDER BY n.criado_em DESC LIMIT 50";
        List<Object> lista = new ArrayList<>();
        try (Connection conexao = Banco.conectar();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setLong(1, sessao.contaId());
            try (ResultSet linhas = comando.executeQuery()) {
                while (linhas.next()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", linhas.getLong("id"));
                    item.put("medicamento", linhas.getString("medicamento"));
                    item.put("quantidade", linhas.getInt("quantidade"));
                    item.put("quantidadeRestante", linhas.getInt("quantidade_restante"));
                    item.put("status", linhas.getString("status"));
                    item.put("criadoEm", linhas.getTimestamp("criado_em").toLocalDateTime().toString());
                    lista.add(item);
                }
            }
        }
        Login.json(troca, 200, Json.de(lista));
    }

    private static void cancelarNecessidade(HttpExchange troca) throws IOException, SQLException {
        Login.Sessao sessao = usuario(troca);
        if (sessao == null) {
            return;
        }
        String formulario = formulario(troca);
        if (formulario == null) {
            return;
        }
        long id = idObrigatorio(Login.campo(formulario, "id"), "Pedido inválido.");
        String sql = "UPDATE necessidade SET status = 'cancelada' "
                + "WHERE id = ? AND conta_id = ? AND status = 'aberta'";
        try (Connection conexao = Banco.conectar();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setLong(1, id);
            comando.setLong(2, sessao.contaId());
            if (comando.executeUpdate() == 0) {
                throw new IllegalArgumentException("Pedido não encontrado ou já fechado.");
            }
        }
        Login.json(troca, 200, "{\"ok\":true}");
    }

    // ----- painel da farmácia -----

    private static void painel(HttpExchange troca) throws IOException, SQLException {
        PainelFarmacia.Contexto contexto = farmaciaAprovadaDaConta(troca);
        if (contexto == null) {
            return;
        }
        long farmacia = contexto.farmaciaId();
        Map<String, Object> resposta = new LinkedHashMap<>();
        try (Connection conexao = Banco.conectar()) {
            resposta.put("resumo", resumo(conexao, farmacia));
            resposta.put("pendentes", pendentes(conexao, farmacia));
            resposta.put("pedidos", pedidos(conexao, farmacia));
        }
        Login.json(troca, 200, Json.de(resposta));
    }

    private static List<Object> resumo(Connection conexao, long farmacia) throws SQLException {
        String sql = "SELECT m.id AS medicamento_id, m.nome AS medicamento, "
                + "COALESCE(doa.total, 0) AS doacoes_disponiveis, COALESCE(nec.total, 0) AS pedidos_abertos, "
                + "LEAST(COALESCE(doa.total, 0), COALESCE(nec.total, 0)) AS pode_atender "
                + "FROM medicamento m "
                + "LEFT JOIN (SELECT medicamento_id, SUM(quantidade_restante) AS total FROM doacao "
                + "           WHERE farmacia_id = ? AND status = 'disponivel' AND validade >= CURRENT_DATE "
                + "           GROUP BY medicamento_id) doa ON doa.medicamento_id = m.id "
                + "LEFT JOIN (SELECT medicamento_id, SUM(quantidade_restante) AS total FROM necessidade "
                + "           WHERE status = 'aberta' GROUP BY medicamento_id) nec ON nec.medicamento_id = m.id "
                + "WHERE doa.total IS NOT NULL OR nec.total IS NOT NULL "
                + "ORDER BY pode_atender DESC, m.nome";
        List<Object> lista = new ArrayList<>();
        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setLong(1, farmacia);
            try (ResultSet linhas = comando.executeQuery()) {
                while (linhas.next()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("medicamentoId", linhas.getLong("medicamento_id"));
                    item.put("medicamento", linhas.getString("medicamento"));
                    item.put("doacoesDisponiveis", linhas.getLong("doacoes_disponiveis"));
                    item.put("pedidosAbertos", linhas.getLong("pedidos_abertos"));
                    item.put("podeAtender", linhas.getLong("pode_atender"));
                    lista.add(item);
                }
            }
        }
        return lista;
    }

    private static List<Object> pendentes(Connection conexao, long farmacia) throws SQLException {
        String sql = "SELECT d.id, m.nome AS medicamento, d.quantidade_inicial, d.validade, d.criado_em "
                + "FROM doacao d JOIN medicamento m ON m.id = d.medicamento_id "
                + "WHERE d.farmacia_id = ? AND d.status = 'pendente' ORDER BY d.criado_em";
        List<Object> lista = new ArrayList<>();
        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setLong(1, farmacia);
            try (ResultSet linhas = comando.executeQuery()) {
                while (linhas.next()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", linhas.getLong("id"));
                    item.put("medicamento", linhas.getString("medicamento"));
                    item.put("quantidade", linhas.getInt("quantidade_inicial"));
                    item.put("validade", linhas.getDate("validade").toLocalDate().toString());
                    item.put("criadoEm", linhas.getTimestamp("criado_em").toLocalDateTime().toString());
                    lista.add(item);
                }
            }
        }
        return lista;
    }

    // Só o primeiro nome e a quantidade: o telefone e o e-mail não saem do banco nesta tela.
    private static List<Object> pedidos(Connection conexao, long farmacia) throws SQLException {
        String sql = "SELECT n.id, n.medicamento_id, m.nome AS medicamento, n.quantidade_restante, "
                + "split_part(c.nome, ' ', 1) AS primeiro_nome, n.criado_em, "
                + "(SELECT SUM(d.quantidade_restante) FROM doacao d "
                + "  WHERE d.farmacia_id = ? AND d.medicamento_id = n.medicamento_id "
                + "    AND d.status = 'disponivel' AND d.validade >= CURRENT_DATE) AS estoque_doado "
                + "FROM necessidade n "
                + "JOIN medicamento m ON m.id = n.medicamento_id "
                + "JOIN conta c ON c.id = n.conta_id "
                + "WHERE n.status = 'aberta' "
                + "  AND EXISTS (SELECT 1 FROM doacao d WHERE d.farmacia_id = ? AND d.medicamento_id = n.medicamento_id "
                + "              AND d.status = 'disponivel' AND d.validade >= CURRENT_DATE "
                + "              AND d.quantidade_restante > 0) "
                + "ORDER BY n.criado_em";
        List<Object> lista = new ArrayList<>();
        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setLong(1, farmacia);
            comando.setLong(2, farmacia);
            try (ResultSet linhas = comando.executeQuery()) {
                while (linhas.next()) {
                    long estoque = linhas.getLong("estoque_doado");
                    long restante = linhas.getLong("quantidade_restante");
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("necessidadeId", linhas.getLong("id"));
                    item.put("medicamentoId", linhas.getLong("medicamento_id"));
                    item.put("medicamento", linhas.getString("medicamento"));
                    item.put("quantidadeRestante", restante);
                    item.put("pessoa", linhas.getString("primeiro_nome"));
                    item.put("podeAtender", Math.min(estoque, restante));
                    item.put("criadoEm", linhas.getTimestamp("criado_em").toLocalDateTime().toString());
                    lista.add(item);
                }
            }
        }
        return lista;
    }

    private static void acaoPainel(HttpExchange troca) throws IOException, SQLException {
        PainelFarmacia.Contexto contexto = farmaciaAprovadaDaConta(troca);
        if (contexto == null) {
            return;
        }
        String formulario = formulario(troca);
        if (formulario == null) {
            return;
        }
        String acao = Login.campo(formulario, "acao");
        long farmacia = contexto.farmaciaId();
        long conta = contexto.contaId();

        try (Connection conexao = Banco.conectar()) {
            switch (acao) {
                case "confirmar", "recusar" -> {
                    long doacao = idObrigatorio(Login.campo(formulario, "doacao"), "Doação inválida.");
                    String novoStatus = acao.equals("confirmar") ? "disponivel" : "recusada";
                    decidirDoacao(conexao, farmacia, conta, doacao, novoStatus);
                    Login.json(troca, 200, "{\"ok\":true}");
                }
                case "atender" -> {
                    long necessidade = idObrigatorio(Login.campo(formulario, "necessidade"), "Pedido inválido.");
                    String quantidadeTexto = Login.campo(formulario, "quantidade");
                    int entregue = atender(conexao, farmacia, necessidade,
                            quantidadeTexto.isBlank() ? null : inteiro(quantidadeTexto, "Quantidade", 1, QUANTIDADE_MAXIMA_PEDIDO));
                    Login.json(troca, 200, "{\"ok\":true,\"entregue\":" + entregue + "}");
                }
                default -> throw new IllegalArgumentException("Ação desconhecida.");
            }
        }
    }

    private static void decidirDoacao(Connection conexao, long farmacia, long conta, long doacao, String novoStatus)
            throws SQLException {
        String sql = "UPDATE doacao SET status = ?, decidido_em = now(), decidido_por = ? "
                + "WHERE id = ? AND farmacia_id = ? AND status = 'pendente'";
        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, novoStatus);
            comando.setLong(2, conta);
            comando.setLong(3, doacao);
            comando.setLong(4, farmacia);
            if (comando.executeUpdate() == 0) {
                throw new IllegalArgumentException("Doação não encontrada ou já foi decidida.");
            }
        }
    }

    // Entrega do estoque doado desta farmácia para um pedido. Usa as doações que vencem primeiro.
    // Cada INSERT passa pelo trigger do banco, que valida e dá a baixa.
    private static int atender(Connection conexao, long farmacia, long necessidade, Integer pedido) throws SQLException {
        conexao.setAutoCommit(false);
        try {
            long medicamento;
            int restante;
            try (PreparedStatement comando = conexao.prepareStatement(
                    "SELECT medicamento_id, quantidade_restante, status FROM necessidade WHERE id = ? FOR UPDATE")) {
                comando.setLong(1, necessidade);
                try (ResultSet linha = comando.executeQuery()) {
                    if (!linha.next() || !"aberta".equals(linha.getString("status"))) {
                        throw new IllegalArgumentException("Este pedido não está mais aberto.");
                    }
                    medicamento = linha.getLong("medicamento_id");
                    restante = linha.getInt("quantidade_restante");
                }
            }
            int faltam = pedido == null ? restante : Math.min(pedido, restante);

            List<long[]> doacoes = new ArrayList<>();
            try (PreparedStatement comando = conexao.prepareStatement(
                    "SELECT id, quantidade_restante FROM doacao "
                            + "WHERE farmacia_id = ? AND medicamento_id = ? AND status = 'disponivel' "
                            + "  AND validade >= CURRENT_DATE AND quantidade_restante > 0 "
                            + "ORDER BY validade, id FOR UPDATE")) {
                comando.setLong(1, farmacia);
                comando.setLong(2, medicamento);
                try (ResultSet linhas = comando.executeQuery()) {
                    while (linhas.next()) {
                        doacoes.add(new long[] { linhas.getLong("id"), linhas.getLong("quantidade_restante") });
                    }
                }
            }

            int entregue = 0;
            String sqlAtendimento = "INSERT INTO atendimento_doacao (necessidade_id, doacao_id, farmacia_id, quantidade) "
                    + "VALUES (?, ?, ?, ?)";
            for (long[] doacao : doacoes) {
                if (faltam == 0) {
                    break;
                }
                int tirar = (int) Math.min(doacao[1], faltam);
                try (PreparedStatement comando = conexao.prepareStatement(sqlAtendimento)) {
                    comando.setLong(1, necessidade);
                    comando.setLong(2, doacao[0]);
                    comando.setLong(3, farmacia);
                    comando.setInt(4, tirar);
                    comando.executeUpdate();
                }
                faltam -= tirar;
                entregue += tirar;
            }
            if (entregue == 0) {
                throw new IllegalArgumentException("Não há estoque doado deste remédio disponível nesta farmácia.");
            }
            conexao.commit();
            return entregue;
        } catch (SQLException erro) {
            conexao.rollback();
            // erro levantado pelo trigger (SQLSTATE P0001): a mensagem é para a tela
            if ("P0001".equals(erro.getSQLState())) {
                throw new IllegalArgumentException(erro.getMessage().trim());
            }
            throw erro;
        } catch (IllegalArgumentException erro) {
            conexao.rollback();
            throw erro;
        } finally {
            conexao.setAutoCommit(true);
        }
    }

    // ----- ajudantes -----

    // Conta logada e não administrador. Responde o erro e devolve null quando não pode.
    private static Login.Sessao usuario(HttpExchange troca) throws IOException {
        Login.Sessao sessao = Login.sessaoAtual(troca);
        if (sessao == null) {
            Login.responderErro(troca, 401, "Entre na sua conta para continuar.");
            return null;
        }
        if ("admin".equals(sessao.tipo())) {
            Login.responderErro(troca, 403, "A conta de administrador não faz doações nem pedidos.");
            return null;
        }
        return sessao;
    }

    // Painel de doações: conta de farmácia com acesso aprovado. Responde o erro e devolve null quando não pode.
    private static PainelFarmacia.Contexto farmaciaAprovadaDaConta(HttpExchange troca) throws IOException, SQLException {
        PainelFarmacia.Contexto contexto = PainelFarmacia.exigirFarmacia(troca);
        if (contexto == null) {
            return null;
        }
        if (contexto.pendente()) {
            Login.responderErro(troca, 403, "As doações só ficam disponíveis depois que o administrador aprovar a farmácia.");
            return null;
        }
        return contexto;
    }

    private static String formulario(HttpExchange troca) throws IOException {
        String formulario = Login.lerFormulario(troca);
        if (formulario == null) {
            Login.responderErro(troca, 413, "Pedido grande demais.");
        }
        return formulario;
    }

    private static boolean medicamentoExiste(Connection conexao, long id) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement("SELECT 1 FROM medicamento WHERE id = ?")) {
            comando.setLong(1, id);
            try (ResultSet linha = comando.executeQuery()) {
                return linha.next();
            }
        }
    }

    private static boolean farmaciaAprovada(Connection conexao, long id) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(
                "SELECT 1 FROM farmacia WHERE id = ? AND situacao = 'aprovada'")) {
            comando.setLong(1, id);
            try (ResultSet linha = comando.executeQuery()) {
                return linha.next();
            }
        }
    }

    private static int contarPedidosAbertos(Connection conexao, long conta) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(
                "SELECT COUNT(*) FROM necessidade WHERE conta_id = ? AND status = 'aberta'")) {
            comando.setLong(1, conta);
            try (ResultSet linha = comando.executeQuery()) {
                linha.next();
                return linha.getInt(1);
            }
        }
    }

    private static long idObrigatorio(String texto, String mensagem) {
        try {
            long id = Long.parseLong(texto.trim());
            if (id > 0) {
                return id;
            }
        } catch (NumberFormatException erro) {
        }
        throw new IllegalArgumentException(mensagem);
    }

    private static int inteiro(String texto, String nome, int minimo, int maximo) {
        try {
            int valor = Integer.parseInt(texto.trim());
            if (valor >= minimo && valor <= maximo) {
                return valor;
            }
        } catch (NumberFormatException erro) {
        }
        throw new IllegalArgumentException(nome + " precisa ser um número entre " + minimo + " e " + maximo + ".");
    }

    private static LocalDate data(String texto) {
        try {
            return LocalDate.parse(texto.trim());
        } catch (DateTimeParseException | NullPointerException erro) {
            throw new IllegalArgumentException("Informe a validade no formato dia/mês/ano.");
        }
    }
}
