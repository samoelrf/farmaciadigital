import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// responde a mesma coisa, exista a conta ou não
class RedefinirSenha {

    private static final Duration VALIDADE_LINK = Duration.ofMinutes(30);
    private static final int MAXIMO_PEDIDOS = 3;
    private static final Duration JANELA_PEDIDOS = Duration.ofMinutes(30);
    private static final String MENSAGEM_GENERICA =
            "Se existir uma conta com esse e-mail, enviamos um link para redefinir a senha. Ele vale por 30 minutos.";
    private static final String LINK_INVALIDO = "Link inválido ou vencido. Peça um novo.";
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private static final Map<String, List<Instant>> pedidos = new ConcurrentHashMap<>();

    record Pedido(long id, long contaId, String email) {}

    static boolean trata(HttpExchange troca, String caminho) throws IOException {
        try {
            switch (caminho) {
                case "/api/trocar-senha" -> { if (Login.pedidoPost(troca)) trocarSenha(troca); }
                case "/api/esqueci-senha" -> { if (Login.pedidoPost(troca)) esqueciSenha(troca); }
                case "/api/redefinir-senha" -> { if (Login.pedidoPost(troca)) redefinirSenha(troca); }
                default -> { return false; }
            }
        } catch (IllegalArgumentException erro) {
            Login.responderErro(troca, 400, erro.getMessage());
        } catch (SQLException | GeneralSecurityException erro) {
            System.err.println("Erro na redefinição de senha: " + erro);
            Login.responderErro(troca, 500, "Erro interno no servidor.");
        }
        return true;
    }

    private static void trocarSenha(HttpExchange troca) throws IOException, SQLException, GeneralSecurityException {
        Login.Sessao sessao = Login.sessaoAtual(troca);
        if (sessao == null) {
            Login.responderErro(troca, 401, "Entre na sua conta para trocar a senha.");
            return;
        }
        String formulario = Login.lerFormulario(troca);
        if (formulario == null) {
            Login.responderErro(troca, 413, "Pedido grande demais.");
            return;
        }
        String atual = Login.campo(formulario, "senha_atual");
        String nova = Login.campo(formulario, "nova");
        String repetir = Login.campo(formulario, "repetir");

        if (Login.bloqueado(sessao.email())) {
            Login.responderErro(troca, 429, "Muitas tentativas. Espere 10 minutos e tente de novo.");
            return;
        }
        Login.Conta conta = Login.buscarConta(sessao.email());
        if (conta == null || !Senha.confere(atual, conta.sal(), conta.hash())) {
            Login.registrarErroSenha(sessao.email());
            throw new IllegalArgumentException("A senha atual está errada.");
        }
        validarNovaSenha(nova, repetir);
        if (nova.equals(atual)) {
            throw new IllegalArgumentException("A senha nova precisa ser diferente da atual.");
        }
        gravarSenha(conta.id(), nova);
        Login.encerrarSessoesDaConta(conta.id(), Login.codigoDaSessao(troca));
        Login.json(troca, 200, "{\"ok\":true}");
    }

    private static void esqueciSenha(HttpExchange troca) throws IOException, SQLException {
        String formulario = Login.lerFormulario(troca);
        if (formulario == null) {
            Login.responderErro(troca, 413, "Pedido grande demais.");
            return;
        }
        String email = Login.normalizarEmail(Login.campo(formulario, "email"));
        Login.Conta conta = Login.buscarConta(email);
        if (permitirPedido(email) && conta != null) {
            new Thread(() -> enviarLink(conta)).start();
        }
        Login.json(troca, 200, "{\"mensagem\":" + Json.texto(MENSAGEM_GENERICA) + "}");
    }

    private static void enviarLink(Login.Conta conta) {
        try {
            String codigo = criarPedido(conta.id());
            String link = siteUrl() + "/redefinir-senha.html?codigo=" + codigo;
            if (Email.configurado()) {
                String texto = "Olá, " + conta.nome() + ". Para criar uma senha nova no Farmácia Digital, abra o link: "
                        + link + "\n\nEle vale por 30 minutos. Se não foi você, ignore este e-mail.";
                Email.enviar(conta.email(), "Redefinir sua senha", texto);
            } else {
                System.out.println("E-mail não configurado; link de redefinição: " + link);
            }
        } catch (Exception erro) {
            System.err.println("Não consegui enviar o link de redefinição: " + erro.getClass().getSimpleName());
        }
    }

    private static void redefinirSenha(HttpExchange troca) throws IOException, SQLException, GeneralSecurityException {
        String formulario = Login.lerFormulario(troca);
        if (formulario == null) {
            Login.responderErro(troca, 413, "Pedido grande demais.");
            return;
        }
        String codigo = Login.campo(formulario, "codigo");
        String nova = Login.campo(formulario, "nova");
        String repetir = Login.campo(formulario, "repetir");
        if (codigo.isEmpty()) {
            throw new IllegalArgumentException(LINK_INVALIDO);
        }
        validarNovaSenha(nova, repetir);

        Pedido pedido = pedidoValido(codigo);
        // marca como usado antes de trocar: dois cliques no mesmo link, só um passa
        if (pedido == null || !marcarUsado(pedido.id())) {
            throw new IllegalArgumentException(LINK_INVALIDO);
        }
        gravarSenha(pedido.contaId(), nova);
        Login.encerrarSessoesDaConta(pedido.contaId(), null);
        Login.zerarTentativas(pedido.email());
        Login.json(troca, 200, "{\"ok\":true}");
    }

    private static void validarNovaSenha(String nova, String repetir) {
        String erro = Senha.validar(nova);
        if (erro != null) {
            throw new IllegalArgumentException(erro);
        }
        if (!nova.equals(repetir)) {
            throw new IllegalArgumentException("A repetição não é igual à senha nova.");
        }
    }

    static void gravarSenha(long contaId, String nova) throws SQLException, GeneralSecurityException {
        String sal = Senha.novoSal();
        String hash = Senha.hash(nova, sal);
        try (Connection conexao = Banco.conectar();
             PreparedStatement comando = conexao.prepareStatement(
                     "UPDATE conta SET senha_hash = ?, senha_sal = ? WHERE id = ?")) {
            comando.setString(1, hash);
            comando.setString(2, sal);
            comando.setLong(3, contaId);
            comando.executeUpdate();
        }
    }

    static String criarPedido(long contaId) throws SQLException {
        String codigo = novoCodigo();
        try (Connection conexao = Banco.conectar()) {
            conexao.setAutoCommit(false);
            try (PreparedStatement invalidar = conexao.prepareStatement(
                    "UPDATE redefinicao_senha SET usado_em = ? WHERE conta_id = ? AND usado_em IS NULL");
                 PreparedStatement inserir = conexao.prepareStatement(
                         "INSERT INTO redefinicao_senha (conta_id, codigo_hash, expira_em) VALUES (?, ?, ?)")) {
                LocalDateTime agora = LocalDateTime.now();
                invalidar.setObject(1, agora);
                invalidar.setLong(2, contaId);
                invalidar.executeUpdate();
                inserir.setLong(1, contaId);
                inserir.setString(2, hashDoCodigo(codigo));
                inserir.setObject(3, agora.plus(VALIDADE_LINK));
                inserir.executeUpdate();
                conexao.commit();
            } catch (SQLException erro) {
                conexao.rollback();
                throw erro;
            }
        }
        return codigo;
    }

    private static Pedido pedidoValido(String codigo) throws SQLException {
        try (Connection conexao = Banco.conectar();
             PreparedStatement comando = conexao.prepareStatement(
                     "SELECT r.id, r.conta_id, c.email FROM redefinicao_senha r JOIN conta c ON c.id = r.conta_id "
                     + "WHERE r.codigo_hash = ? AND r.usado_em IS NULL AND r.expira_em > ?")) {
            comando.setString(1, hashDoCodigo(codigo));
            comando.setObject(2, LocalDateTime.now());
            try (ResultSet linha = comando.executeQuery()) {
                if (!linha.next()) {
                    return null;
                }
                return new Pedido(linha.getLong("id"), linha.getLong("conta_id"), linha.getString("email"));
            }
        }
    }

    private static boolean marcarUsado(long pedidoId) throws SQLException {
        try (Connection conexao = Banco.conectar();
             PreparedStatement comando = conexao.prepareStatement(
                     "UPDATE redefinicao_senha SET usado_em = ? WHERE id = ? AND usado_em IS NULL")) {
            comando.setObject(1, LocalDateTime.now());
            comando.setLong(2, pedidoId);
            return comando.executeUpdate() == 1;
        }
    }

    private static boolean permitirPedido(String email) {
        Instant agora = Instant.now();
        pedidos.values().removeIf(l -> l.stream().allMatch(t -> agora.isAfter(t.plus(JANELA_PEDIDOS))));
        List<Instant> recentes = new ArrayList<>();
        pedidos.compute(email, (chave, antigos) -> {
            List<Instant> lista = new ArrayList<>();
            if (antigos != null) {
                for (Instant t : antigos) {
                    if (agora.isBefore(t.plus(JANELA_PEDIDOS))) {
                        lista.add(t);
                    }
                }
            }
            recentes.addAll(lista);
            if (lista.size() < MAXIMO_PEDIDOS) {
                lista.add(agora);
            }
            return lista;
        });
        return recentes.size() < MAXIMO_PEDIDOS;
    }

    private static String novoCodigo() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // guarda só o hash do código: quem ler o banco não consegue usar o link
    static String hashDoCodigo(String codigo) {
        try {
            byte[] resumo = MessageDigest.getInstance("SHA-256").digest(codigo.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumo);
        } catch (java.security.NoSuchAlgorithmException erro) {
            throw new IllegalStateException("SHA-256 indisponível", erro);
        }
    }

    // vem de SITE_URL, nunca do Host: o cabeçalho pode ser forjado
    private static String siteUrl() {
        String url = Email.ambiente("SITE_URL");
        return url != null ? url : "http://localhost:8080";
    }
}
