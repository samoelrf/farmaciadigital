import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// sessão só na memória: reiniciar o servidor desloga todo mundo
class Login {

    static final int TAMANHO_CORPO_MAXIMO = 10_240;
    private static final Duration VALIDADE_SESSAO = Duration.ofHours(8);
    private static final int MAXIMO_ERROS = 5;
    private static final Duration JANELA_ERROS = Duration.ofMinutes(10);
    private static final Duration TEMPO_BLOQUEIO = Duration.ofMinutes(10);
    private static final String COOKIE_APAGADO = "sessao=; HttpOnly; SameSite=Lax; Path=/; Max-Age=0";
    // e-mail que não existe também faz o hash, para a resposta levar o mesmo tempo
    private static final String SAL_FALSO = Senha.novoSal();
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private static final Map<String, Sessao> sessoes = new ConcurrentHashMap<>();
    private static final Map<String, Tentativas> tentativas = new ConcurrentHashMap<>();

    record Sessao(long contaId, String nome, String email, String tipo, Instant expira) {}
    record Tentativas(int erros, Instant desde, Instant bloqueadoAte) {}
    record Conta(long id, String nome, String email, String tipo, String hash, String sal) {}

    static boolean trata(HttpExchange troca, String caminho) throws IOException {
        try {
            switch (caminho) {
                case "/api/cadastro" -> { if (pedidoPost(troca)) cadastro(troca); }
                case "/api/entrar" -> { if (pedidoPost(troca)) entrar(troca); }
                case "/api/sair" -> { if (pedidoPost(troca)) sair(troca); }
                case "/api/apagar-conta" -> { if (pedidoPost(troca)) apagarConta(troca); }
                case "/api/eu" -> eu(troca);
                default -> { return false; }
            }
        } catch (IllegalArgumentException erro) {
            responderErro(troca, 400, "Dados em formato errado.");
        } catch (SQLException | GeneralSecurityException erro) {
            System.err.println("Erro no login em " + caminho + ": " + erro);
            responderErro(troca, 500, "Erro interno no servidor.");
        }
        return true;
    }

    // esconder o link no site não protege nada: quem protege é o servidor
    static boolean exigirAdmin(HttpExchange troca) throws IOException {
        Sessao sessao = sessaoAtual(troca);
        if (sessao == null) {
            responderErro(troca, 401, "Entre com uma conta de administrador.");
            return false;
        }
        if (!"admin".equals(sessao.tipo())) {
            responderErro(troca, 403, "Só o administrador pode ver esta página.");
            return false;
        }
        return true;
    }

    // precisa aceitar a política de privacidade (LGPD)
    private static void cadastro(HttpExchange troca) throws IOException, SQLException, GeneralSecurityException {
        String formulario = lerFormulario(troca);
        if (formulario == null) {
            responderErro(troca, 413, "Pedido grande demais.");
            return;
        }
        String nome = campo(formulario, "nome").trim();
        String email = normalizarEmail(campo(formulario, "email"));
        String senha = campo(formulario, "senha");

        String erroDados = validarDados(nome, email, senha);
        if (erroDados != null) {
            responderErro(troca, 400, erroDados);
            return;
        }
        if (!"sim".equals(campo(formulario, "aceite"))) {
            responderErro(troca, 400, "Para criar a conta, marque que leu e aceita a política de privacidade.");
            return;
        }
        try {
            criarConta(nome, email, senha, "cidadao");
            json(troca, 201, "{\"ok\":true}");
        } catch (SQLException erro) {
            if ("23505".equals(erro.getSQLState())) {
                responderErro(troca, 409, "Já existe uma conta com este e-mail.");
            } else {
                throw erro;
            }
        }
    }

    private static void entrar(HttpExchange troca) throws IOException, SQLException, GeneralSecurityException {
        String formulario = lerFormulario(troca);
        if (formulario == null) {
            responderErro(troca, 413, "Pedido grande demais.");
            return;
        }
        String email = normalizarEmail(campo(formulario, "email"));
        String senha = campo(formulario, "senha");
        Instant agora = Instant.now();

        Tentativas anteriores = tentativas.get(email);
        if (anteriores != null && anteriores.bloqueadoAte() != null && agora.isBefore(anteriores.bloqueadoAte())) {
            responderErro(troca, 429, "Muitas tentativas. Espere 10 minutos e tente de novo.");
            return;
        }

        Conta conta = buscarConta(email);
        boolean senhaCerta = false;
        if (conta == null) {
            Senha.hash(senha, SAL_FALSO);   // gasta o mesmo tempo de uma conta real
        } else {
            senhaCerta = Senha.confere(senha, conta.sal(), conta.hash());
        }
        if (!senhaCerta) {
            registrarErro(email, agora);
            // mesma mensagem para e-mail inexistente e senha errada: não revela quem tem conta
            responderErro(troca, 401, "E-mail ou senha incorretos.");
            return;
        }

        tentativas.remove(email);
        sessoes.values().removeIf(s -> s.expira().isBefore(agora));
        String codigo = novoCodigoSessao();
        sessoes.put(codigo, new Sessao(conta.id(), conta.nome(), conta.email(), conta.tipo(), agora.plus(VALIDADE_SESSAO)));
        troca.getResponseHeaders().add("Set-Cookie", cookieDaSessao(codigo));
        json(troca, 200, dadosJson(conta.nome(), conta.email(), conta.tipo()));
    }

    private static void sair(HttpExchange troca) throws IOException {
        String codigo = codigoDaSessao(troca);
        if (codigo != null) {
            sessoes.remove(codigo);
        }
        troca.getResponseHeaders().add("Set-Cookie", COOKIE_APAGADO);
        json(troca, 200, "{\"ok\":true}");
    }

    private static void eu(HttpExchange troca) throws IOException {
        if (!"GET".equals(troca.getRequestMethod())) {
            Servidor.enviarTexto(troca, 405, "Método não permitido");
            return;
        }
        Sessao sessao = sessaoAtual(troca);
        if (sessao == null) {
            responderErro(troca, 401, "Você não entrou.");
            return;
        }
        json(troca, 200, dadosJson(sessao.nome(), sessao.email(), sessao.tipo()));
    }

    private static void apagarConta(HttpExchange troca) throws IOException, SQLException, GeneralSecurityException {
        Sessao sessao = sessaoAtual(troca);
        if (sessao == null) {
            responderErro(troca, 401, "Você não entrou.");
            return;
        }
        if ("admin".equals(sessao.tipo())) {
            responderErro(troca, 403, "Conta de administrador não pode ser apagada por aqui.");
            return;
        }
        String formulario = lerFormulario(troca);
        if (formulario == null) {
            responderErro(troca, 413, "Pedido grande demais.");
            return;
        }
        if (bloqueado(sessao.email())) {
            responderErro(troca, 429, "Muitas tentativas. Espere 10 minutos e tente de novo.");
            return;
        }
        Conta conta = buscarConta(sessao.email());
        if (conta == null || !Senha.confere(campo(formulario, "senha"), conta.sal(), conta.hash())) {
            registrarErroSenha(sessao.email());
            responderErro(troca, 401, "Senha incorreta.");
            return;
        }
        try (Connection conexao = Banco.conectar();
             PreparedStatement comando = conexao.prepareStatement("DELETE FROM conta WHERE id = ?")) {
            comando.setLong(1, conta.id());
            comando.executeUpdate();
        }
        sessoes.values().removeIf(s -> s.contaId() == conta.id());
        troca.getResponseHeaders().add("Set-Cookie", COOKIE_APAGADO);
        json(troca, 200, "{\"ok\":true}");
    }

    static void encerrarSessoesDaConta(long contaId, String manter) {
        sessoes.entrySet().removeIf(e -> e.getValue().contaId() == contaId && !e.getKey().equals(manter));
    }

    static void registrarErroSenha(String email) {
        registrarErro(email, Instant.now());
    }

    static boolean bloqueado(String email) {
        Tentativas t = tentativas.get(email);
        return t != null && t.bloqueadoAte() != null && Instant.now().isBefore(t.bloqueadoAte());
    }

    static void zerarTentativas(String email) {
        tentativas.remove(email);
    }


    static String validarDados(String nome, String email, String senha) {
        if (nome.length() < 2 || nome.length() > 100) {
            return "O nome precisa ter entre 2 e 100 caracteres.";
        }
        if (email.length() > 150 || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            return "Digite um e-mail válido.";
        }
        return Senha.validar(senha);
    }

    static String normalizarEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    static void criarConta(String nome, String email, String senha, String tipo)
            throws SQLException, GeneralSecurityException {
        String sal = Senha.novoSal();
        String hash = Senha.hash(senha, sal);
        try (Connection conexao = Banco.conectar();
             PreparedStatement comando = conexao.prepareStatement(
                     "INSERT INTO conta (nome, email, senha_hash, senha_sal, tipo, aceite_lgpd_em) "
                     + "VALUES (?, ?, ?, ?, ?, CASE WHEN ? = 'cidadao' THEN now() ELSE NULL END)")) {
            comando.setString(1, nome);
            comando.setString(2, email);
            comando.setString(3, hash);
            comando.setString(4, sal);
            comando.setString(5, tipo);
            comando.setString(6, tipo);
            comando.executeUpdate();
        }
    }


    // só POST, e o Origin precisa ser o próprio site (contra CSRF)
    static boolean pedidoPost(HttpExchange troca) throws IOException {
        if (!"POST".equals(troca.getRequestMethod())) {
            Servidor.enviarTexto(troca, 405, "Método não permitido");
            return false;
        }
        if (!origemDoProprioSite(troca)) {
            responderErro(troca, 403, "Pedido recusado.");
            return false;
        }
        return true;
    }

    private static boolean origemDoProprioSite(HttpExchange troca) {
        String origem = troca.getRequestHeaders().getFirst("Origin");
        String host = troca.getRequestHeaders().getFirst("Host");
        return origem != null && host != null && (origem.equals("http://" + host) || origem.equals("https://" + host));
    }

    static String lerFormulario(HttpExchange troca) throws IOException {
        byte[] bytes = troca.getRequestBody().readNBytes(TAMANHO_CORPO_MAXIMO + 1);
        if (bytes.length > TAMANHO_CORPO_MAXIMO) {
            return null;
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static String campo(String formulario, String nome) {
        String valor = Rotas.parametro(formulario, nome);
        return valor == null ? "" : valor;
    }

    static Conta buscarConta(String email) throws SQLException {
        try (Connection conexao = Banco.conectar();
             PreparedStatement comando = conexao.prepareStatement(
                     "SELECT id, nome, email, tipo, senha_hash, senha_sal FROM conta WHERE email = ?")) {
            comando.setString(1, email);
            try (ResultSet linha = comando.executeQuery()) {
                if (!linha.next()) {
                    return null;
                }
                return new Conta(linha.getLong("id"), linha.getString("nome"), linha.getString("email"),
                        linha.getString("tipo"), linha.getString("senha_hash"), linha.getString("senha_sal"));
            }
        }
    }

    private static void registrarErro(String email, Instant agora) {
        tentativas.values().removeIf(t -> agora.isAfter(t.desde().plus(JANELA_ERROS))
                && (t.bloqueadoAte() == null || agora.isAfter(t.bloqueadoAte())));
        tentativas.compute(email, (chave, antigas) -> {
            if (antigas == null || agora.isAfter(antigas.desde().plus(JANELA_ERROS))) {
                return new Tentativas(1, agora, null);
            }
            int erros = antigas.erros() + 1;
            Instant bloqueadoAte = erros >= MAXIMO_ERROS ? agora.plus(TEMPO_BLOQUEIO) : null;
            return new Tentativas(erros, antigas.desde(), bloqueadoAte);
        });
    }

    static Sessao sessaoAtual(HttpExchange troca) {
        String codigo = codigoDaSessao(troca);
        if (codigo == null) {
            return null;
        }
        Sessao sessao = sessoes.get(codigo);
        if (sessao == null) {
            return null;
        }
        if (Instant.now().isAfter(sessao.expira())) {
            sessoes.remove(codigo);
            return null;
        }
        // o tipo vem do banco a cada pedido: se o admin aprovar a farmácia, vale sem sair da conta
        try {
            String tipoAtual = tipoNoBanco(sessao.contaId());
            if (tipoAtual == null) {
                sessoes.remove(codigo);
                return null;
            }
            if (!tipoAtual.equals(sessao.tipo())) {
                sessao = new Sessao(sessao.contaId(), sessao.nome(), sessao.email(), tipoAtual, sessao.expira());
                sessoes.put(codigo, sessao);
            }
            return sessao;
        } catch (SQLException erro) {
            System.err.println("Não consegui conferir o tipo da conta: " + erro.getClass().getSimpleName());
            return null;
        }
    }

    static String tipoNoBanco(long contaId) throws SQLException {
        try (Connection conexao = Banco.conectar();
             PreparedStatement comando = conexao.prepareStatement("SELECT tipo FROM conta WHERE id = ?")) {
            comando.setLong(1, contaId);
            try (ResultSet linha = comando.executeQuery()) {
                return linha.next() ? linha.getString("tipo") : null;
            }
        }
    }

    static String codigoDaSessao(HttpExchange troca) {
        String cookies = troca.getRequestHeaders().getFirst("Cookie");
        if (cookies == null) {
            return null;
        }
        for (String par : cookies.split(";")) {
            String limpo = par.trim();
            if (limpo.startsWith("sessao=")) {
                return limpo.substring("sessao=".length());
            }
        }
        return null;
    }

    private static String novoCodigoSessao() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String cookieDaSessao(String codigo) {
        return "sessao=" + codigo + "; HttpOnly; SameSite=Lax; Path=/; Max-Age=" + VALIDADE_SESSAO.toSeconds();
    }

    static void json(HttpExchange troca, int status, String texto) throws IOException {
        Servidor.enviar(troca, status, "application/json; charset=utf-8", texto.getBytes(StandardCharsets.UTF_8));
    }

    static void responderErro(HttpExchange troca, int status, String mensagem) throws IOException {
        json(troca, status, "{\"erro\":" + Json.texto(mensagem) + "}");
    }

    private static String dadosJson(String nome, String email, String tipo) {
        return "{\"nome\":" + Json.texto(nome) + ",\"email\":" + Json.texto(email) + ",\"tipo\":" + Json.texto(tipo) + "}";
    }
}
