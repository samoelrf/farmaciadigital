import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class TesteSimples {

    private static int falhas = 0;
    private static String base;
    private static final List<String> emailsCriados = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        verificar("Json escapa aspas e quebra de linha",
                Json.texto("Ação \"x\"\n").equals("\"Ação \\\"x\\\"\\n\""));
        verificar("Senha confere a senha certa e recusa a errada", senhaConfere());
        verificar("Senha recusa senha com menos de 8 caracteres", Senha.validar("1234567") != null);

        HttpServer servidor = Servidor.criar(0, Path.of("..", "site"));
        servidor.start();
        base = "http://localhost:" + servidor.getAddress().getPort();
        try {
            testarDados();
            testarLogin();
            testarB2(base);
            testarRedefinicao(base);
            testarAreaAdmin(base);
        } finally {
            servidor.stop(0);
            apagarContasDeTeste();
        }

        System.out.println(falhas == 0 ? "TODOS OS TESTES PASSARAM" : falhas + " FALHA(S)");
        System.exit(falhas == 0 ? 0 : 1);
    }

    // ----- rotas de dados -----

    private static void testarDados() throws Exception {
        HttpResponse<String> pontos = get("/api/pontos", null);
        verificar("GET /api/pontos responde 200 com tipoFarmacia e fonteHorario",
                pontos.statusCode() == 200 && pontos.body().contains("\"tipoFarmacia\"") && pontos.body().contains("\"fonteHorario\""));
        verificar("GET /api/pontos tem o campo popular", pontos.body().contains("\"popular\""));
        verificar("existem farmácias com popular=true", pontos.body().contains("\"popular\":true"));
        verificar("GET /api/pontos não devolve mais UBS", !pontos.body().contains("\"tipo\":\"ubs\""));

        HttpResponse<String> remedios = get("/api/medicamentos?nome=losar", null);
        verificar("GET /api/medicamentos acha a Losartana (id 1)",
                remedios.statusCode() == 200 && remedios.body().contains("\"id\":1"));

        HttpResponse<String> precos = get("/api/precos?medicamento=1", null);
        verificar("GET /api/precos responde 200 com lista", precos.statusCode() == 200 && precos.body().startsWith("["));
        verificar("GET /api/precos sem medicamento dá 400", get("/api/precos", null).statusCode() == 400);

        verificar("GET /api/catalogo sem entrar dá 401", get("/api/catalogo", null).statusCode() == 401);

        verificar("caminho com .. dá 404", cru("/../compilar.bat") == 404);
        verificar("caminho com %2e%2e dá 404", cru("/%2e%2e/compilar.bat") == 404);
        verificar("rota que não existe dá 404 em texto", get("/api/nao-existe", null).body().equals("Página não encontrada"));
    }

    // ----- login -----

    private static void testarLogin() throws Exception {
        String email = novoEmail("cidadao");
        String origem = base;

        HttpResponse<String> cadastro = post("/api/cadastro", origem,
                form("nome", "Ana Teste", "email", email, "senha", "Senha@12345", "aceite", "sim"));
        verificar("cadastro cria a conta (201)", cadastro.statusCode() == 201);
        verificar("e-mail repetido é recusado (409)",
                post("/api/cadastro", origem, form("nome", "Ana Teste", "email", email, "senha", "Senha@12345", "aceite", "sim")).statusCode() == 409);
        verificar("cadastro sem aceitar a política dá 400",
                post("/api/cadastro", origem, form("nome", "Outra", "email", novoEmail("sem-aceite"), "senha", "Senha@12345", "aceite", "")).statusCode() == 400);
        verificar("POST sem Origin dá 403",
                post("/api/entrar", null, form("email", email, "senha", "Senha@12345")).statusCode() == 403);
        verificar("POST com Origin de outro site dá 403",
                post("/api/entrar", "http://site-estranho.exemplo", form("email", email, "senha", "Senha@12345")).statusCode() == 403);

        HttpResponse<String> errada = post("/api/entrar", origem, form("email", email, "senha", "errada123"));
        verificar("senha errada dá 401 com a mensagem genérica",
                errada.statusCode() == 401 && errada.body().contains("E-mail ou senha incorretos."));

        HttpResponse<String> entrou = post("/api/entrar", origem, form("email", "  " + email.toUpperCase() + " ", "senha", "Senha@12345"));
        verificar("entrar com e-mail em maiúsculas e espaços dá 200", entrou.statusCode() == 200);
        String cookie = cookieDe(entrou);
        verificar("cookie de sessão é HttpOnly e SameSite=Lax",
                entrou.headers().firstValue("Set-Cookie").orElse("").contains("HttpOnly")
                        && entrou.headers().firstValue("Set-Cookie").orElse("").contains("SameSite=Lax"));
        verificar("/api/eu com o cookie mostra a conta",
                get("/api/eu", cookie).statusCode() == 200 && get("/api/eu", cookie).body().contains(email));
        verificar("cidadão não vê o catálogo (403)", get("/api/catalogo", cookie).statusCode() == 403);

        verificar("sair dá 200", post("/api/sair", origem, "", cookie).statusCode() == 200);
        verificar("/api/eu depois de sair dá 401", get("/api/eu", cookie).statusCode() == 401);

        String novoCookie = cookieDe(post("/api/entrar", origem, form("email", email, "senha", "Senha@12345")));
        verificar("cada login gera um código de sessão novo", !novoCookie.equals(cookie) && !novoCookie.isEmpty());

        verificar("apagar conta com senha errada dá 401",
                post("/api/apagar-conta", origem, form("senha", "errada123"), novoCookie).statusCode() == 401);
        verificar("apagar conta com a senha certa dá 200",
                post("/api/apagar-conta", origem, form("senha", "Senha@12345"), novoCookie).statusCode() == 200);
        verificar("depois de apagada, a conta não entra mais (401)",
                post("/api/entrar", origem, form("email", email, "senha", "Senha@12345")).statusCode() == 401);

        testarBloqueio(origem);
        testarAdmin(origem);
    }

    private static void testarBloqueio(String origem) throws Exception {
        String email = novoEmail("bloqueio");
        post("/api/cadastro", origem, form("nome", "Bloqueio Teste", "email", email, "senha", "Senha@12345", "aceite", "sim"));
        for (int i = 0; i < 5; i++) {
            post("/api/entrar", origem, form("email", email, "senha", "errada" + i + "xyz"));
        }
        HttpResponse<String> sexta = post("/api/entrar", origem, form("email", email, "senha", "Senha@12345"));
        verificar("6ª tentativa bloqueia mesmo com a senha certa (429)", sexta.statusCode() == 429);
    }

    private static void testarAdmin(String origem) throws Exception {
        String email = novoEmail("admin");
        Login.criarConta("Admin Teste", email, "Senha@12345", "admin");
        String cookie = cookieDe(post("/api/entrar", origem, form("email", email, "senha", "Senha@12345")));
        HttpResponse<String> catalogo = get("/api/catalogo", cookie);
        verificar("administrador vê /api/catalogo (200) com campos farmacia e medicamento",
                catalogo.statusCode() == 200 && catalogo.body().contains("\"farmacia\"") && catalogo.body().contains("\"medicamento\""));
    }

    // ----- favoritos e painel da farmácia -----

    private static void testarB2(String origem) throws Exception {
        String email = novoEmail("favorito");
        post("/api/cadastro", origem, form("nome", "Favorito Teste", "email", email, "senha", "Senha@12345", "aceite", "sim"));
        String cookie = cookieDe(post("/api/entrar", origem, form("email", email, "senha", "Senha@12345")));

        verificar("favoritos sem entrar dá 401", get("/api/favoritos", null).statusCode() == 401);
        verificar("favoritar uma farmácia dá 200",
                post("/api/favoritos", origem, form("tipo", "farmacia", "id", "1", "acao", "adicionar"), cookie).statusCode() == 200);
        verificar("farmácia favoritada aparece na lista", get("/api/favoritos", cookie).body().contains("Farmácia Flores"));
        verificar("desfavoritar tira da lista",
                post("/api/favoritos", origem, form("tipo", "farmacia", "id", "1", "acao", "remover"), cookie).statusCode() == 200
                        && !get("/api/favoritos", cookie).body().contains("Farmácia Flores"));
        verificar("tipo de favorito inválido dá 400",
                post("/api/favoritos", origem, form("tipo", "outra", "id", "1", "acao", "adicionar"), cookie).statusCode() == 400);
        verificar("favorito de item que não existe dá 404",
                post("/api/favoritos", origem, form("tipo", "farmacia", "id", "999999", "acao", "adicionar"), cookie).statusCode() == 404);

        verificar("painel sem acesso aprovado dá 403", get("/api/painel", cookie).statusCode() == 403);
        verificar("CNPJ inválido dá 400",
                post("/api/acesso-farmacia", origem, form("cnpj", "11111111111111", "telefone", "15999991111"), cookie).statusCode() == 400);
        verificar("CNPJ fora da base sem nome e endereço dá 400",
                post("/api/acesso-farmacia", origem, form("cnpj", "11222333000181", "telefone", "15999991111"), cookie).statusCode() == 400);
        verificar("farmácia nova sem cidade válida dá 400", post("/api/acesso-farmacia", origem,
                form("cnpj", "11222333000181", "telefone", "15999991111", "nome", "Drogaria Teste", "endereco", "Rua Teste, 10",
                        "cidade", "Campinas"), cookie).statusCode() == 400);
        verificar("pedido de acesso fica pendente (201)",
                post("/api/acesso-farmacia", origem, form("cnpj", "11222333000181", "telefone", "15999991111",
                        "nome", "Drogaria Teste", "endereco", "Rua Teste, 10", "cidade", "Sorocaba"), cookie).statusCode() == 201);
        verificar("segundo pedido pendente é recusado (409)",
                post("/api/acesso-farmacia", origem, form("cnpj", "11222333000181", "telefone", "15999991111",
                        "nome", "Outra", "endereco", "Rua X, 1", "cidade", "Sorocaba"), cookie).statusCode() == 409);
        verificar("status do pedido aparece como pendente",
                get("/api/acesso-farmacia", cookie).body().contains("\"status\":\"pendente\""));

        Object[] antes = fotoDoPar(1, 6);
        String cookieFarmacia = cookieDe(post("/api/entrar", origem, form("email", "farmacia@demo.farmaciadigital.local", "senha", "Demo@2026")));
        verificar("painel da farmácia mostra a própria farmácia (id 1)",
                get("/api/painel", cookieFarmacia).body().contains("\"farmacia\":{\"id\":1"));
        verificar("id de outra farmácia no formulário não muda nada",
                post("/api/painel", origem, form("acao", "salvar", "medicamento", "6", "preco", "12,34", "disponivel", "sim",
                        "farmacia_id", "2"), cookieFarmacia).statusCode() == 200);
        verificar("preço salvo pela farmácia aparece em /api/precos",
                get("/api/precos?medicamento=6", null).body().contains("\"preco\":12.34"));
        verificar("preço do painel vem marcado como informado pela farmácia",
                get("/api/precos?medicamento=6", null).body().contains("\"informadoPelaFarmacia\":true"));
        verificar("preço negativo dá 400",
                post("/api/painel", origem, form("acao", "salvar", "medicamento", "6", "preco", "-1", "disponivel", "sim"),
                        cookieFarmacia).statusCode() == 400);
        restaurarPar(1, 6, antes);

        String emailFarmacia = novoEmail("farmacia");
        Login.criarConta("Farmácia Teste", emailFarmacia, "Senha@12345", "farmacia");
        String cookieF = cookieDe(post("/api/entrar", origem, form("email", emailFarmacia, "senha", "Senha@12345")));
        verificar("conta de farmácia apaga a própria conta (200)",
                post("/api/apagar-conta", origem, form("senha", "Senha@12345"), cookieF).statusCode() == 200);
        verificar("cidadão de teste apaga a própria conta (200)",
                post("/api/apagar-conta", origem, form("senha", "Senha@12345"), cookie).statusCode() == 200);
    }


    private static void testarRedefinicao(String origem) throws Exception {
        String email = novoEmail("senha");
        post("/api/cadastro", origem, form("nome", "Senha Teste", "email", email, "senha", "Senha@12345", "aceite", "sim"));
        String cookie1 = cookieDe(post("/api/entrar", origem, form("email", email, "senha", "Senha@12345")));
        String cookie2 = cookieDe(post("/api/entrar", origem, form("email", email, "senha", "Senha@12345")));

        verificar("trocar senha com a senha atual errada dá 400", post("/api/trocar-senha", origem,
                form("senha_atual", "errada123", "nova", "Outra@12345", "repetir", "Outra@12345"), cookie2).statusCode() == 400);
        verificar("trocar senha com repetição diferente dá 400", post("/api/trocar-senha", origem,
                form("senha_atual", "Senha@12345", "nova", "Outra@12345", "repetir", "Outra@9999"), cookie2).statusCode() == 400);
        verificar("trocar senha dá 200", post("/api/trocar-senha", origem,
                form("senha_atual", "Senha@12345", "nova", "Outra@12345", "repetir", "Outra@12345"), cookie2).statusCode() == 200);
        verificar("sessão antiga da mesma conta cai depois da troca", get("/api/eu", cookie1).statusCode() == 401);
        verificar("a sessão de quem trocou continua valendo", get("/api/eu", cookie2).statusCode() == 200);
        verificar("senha antiga não entra mais",
                post("/api/entrar", origem, form("email", email, "senha", "Senha@12345")).statusCode() == 401);
        verificar("senha nova entra",
                post("/api/entrar", origem, form("email", email, "senha", "Outra@12345")).statusCode() == 200);

        if (!Email.configurado()) {
            String inexistente = post("/api/esqueci-senha", origem, form("email", novoEmail("nao-existe"))).body();
            String existente = post("/api/esqueci-senha", origem, form("email", email)).body();
            verificar("esqueci senha responde a mesma mensagem, com ou sem conta", inexistente.equals(existente)
                    && existente.contains("Se existir uma conta com esse e-mail"));
            for (int i = 0; i < 3; i++) {
                post("/api/esqueci-senha", origem, form("email", email));
            }
            Thread.sleep(1500);
            verificar("limite: o 4º pedido em 30 minutos não gera link (3 linhas no banco)",
                    contarPedidos(email) == 3);

            long contaId = Login.buscarConta(email).id();
            String codigo = RedefinirSenha.criarPedido(contaId);
            verificar("código de link errado dá 400", post("/api/redefinir-senha", origem,
                    form("codigo", "codigo-inventado", "nova", "Nova@12345", "repetir", "Nova@12345")).statusCode() == 400);
            verificar("código certo troca a senha (200)", post("/api/redefinir-senha", origem,
                    form("codigo", codigo, "nova", "Nova@12345", "repetir", "Nova@12345")).statusCode() == 200);
            verificar("o mesmo código não serve de novo (uso único)", post("/api/redefinir-senha", origem,
                    form("codigo", codigo, "nova", "Outra@999999", "repetir", "Outra@999999")).statusCode() == 400);
            verificar("depois de redefinir, a sessão antiga cai", get("/api/eu", cookie2).statusCode() == 401);
            verificar("a senha nova do link entra",
                    post("/api/entrar", origem, form("email", email, "senha", "Nova@12345")).statusCode() == 200);

            String vencido = RedefinirSenha.criarPedido(contaId);
            try (Connection conexao = Banco.conectar();
                 PreparedStatement comando = conexao.prepareStatement(
                         "UPDATE redefinicao_senha SET expira_em = now() - interval '1 minute' WHERE codigo_hash = ?")) {
                comando.setString(1, RedefinirSenha.hashDoCodigo(vencido));
                comando.executeUpdate();
            }
            verificar("código vencido é recusado (400)", post("/api/redefinir-senha", origem,
                    form("codigo", vencido, "nova", "Outra@999999", "repetir", "Outra@999999")).statusCode() == 400);
        }
    }

    // ----- admin -----

    private static void testarAreaAdmin(String origem) throws Exception {
        String emailAdmin = novoEmail("admin3");
        Login.criarConta("Admin Teste B3", emailAdmin, "Senha@12345", "admin");
        String cookieAdmin = cookieDe(post("/api/entrar", origem, form("email", emailAdmin, "senha", "Senha@12345")));

        verificar("área do administrador sem login dá 401", get("/api/admin/pedidos", null).statusCode() == 401);
        String emailC = novoEmail("pedido3");
        post("/api/cadastro", origem, form("nome", "Pedido Teste", "email", emailC, "senha", "Senha@12345", "aceite", "sim"));
        String cookieC = cookieDe(post("/api/entrar", origem, form("email", emailC, "senha", "Senha@12345")));
        verificar("cidadão na área do administrador dá 403", get("/api/admin/resumo", cookieC).statusCode() == 403);
        verificar("resumo do administrador dá 200", get("/api/admin/resumo", cookieAdmin).statusCode() == 200);

        String cnpj = cnpjDeFarmaciaDaBase();
        post("/api/acesso-farmacia", origem, form("cnpj", cnpj, "telefone", "15999991111"), cookieC);
        long pedido = pedidoPendente(emailC);
        verificar("pedido aparece para o administrador como pendente",
                get("/api/admin/pedidos?status=pendente", cookieAdmin).body().contains(emailC));
        verificar("painel antes da aprovação dá 403", get("/api/painel", cookieC).statusCode() == 403);
        verificar("aprovar pedido pendente dá 200", post("/api/admin/pedidos", origem,
                form("id", String.valueOf(pedido), "acao", "aprovar"), cookieAdmin).statusCode() == 200);
        verificar("aprovar faz a conta virar farmácia", "farmacia".equals(tipoDaConta(emailC)));
        verificar("a sessão já aberta antes da aprovação acessa o painel (tipo conferido no banco)",
                get("/api/painel", cookieC).statusCode() == 200);
        String cookieC2 = cookieDe(post("/api/entrar", origem, form("email", emailC, "senha", "Senha@12345")));
        verificar("painel libera depois da aprovação (200)", get("/api/painel", cookieC2).statusCode() == 200);
        verificar("aprovar de novo dá 409", post("/api/admin/pedidos", origem,
                form("id", String.valueOf(pedido), "acao", "aprovar"), cookieAdmin).statusCode() == 409);

        verificar("revogar acesso aprovado dá 200", post("/api/admin/pedidos", origem,
                form("id", String.valueOf(pedido), "acao", "revogar"), cookieAdmin).statusCode() == 200);
        verificar("painel fecha depois de revogar (403)", get("/api/painel", cookieC2).statusCode() == 403);

        String emailR = novoEmail("recusa3");
        post("/api/cadastro", origem, form("nome", "Recusa Teste", "email", emailR, "senha", "Senha@12345", "aceite", "sim"));
        String cookieR = cookieDe(post("/api/entrar", origem, form("email", emailR, "senha", "Senha@12345")));
        post("/api/acesso-farmacia", origem, form("cnpj", cnpj, "telefone", "15999992222"), cookieR);
        long recusado = pedidoPendente(emailR);
        verificar("recusar pedido pendente dá 200", post("/api/admin/pedidos", origem,
                form("id", String.valueOf(recusado), "acao", "recusar"), cookieAdmin).statusCode() == 200);
        verificar("recusar não muda o tipo da conta", "cidadao".equals(tipoDaConta(emailR)));

        String emailN = novoEmail("nova3");
        post("/api/cadastro", origem, form("nome", "Farmácia Nova", "email", emailN, "senha", "Senha@12345", "aceite", "sim"));
        String cookieN = cookieDe(post("/api/entrar", origem, form("email", emailN, "senha", "Senha@12345")));
        post("/api/acesso-farmacia", origem, form("cnpj", "11222333000181", "telefone", "15999993333",
                "nome", "Drogaria Teste B3", "endereco", "Rua Teste B3, 30", "cidade", "Sorocaba"), cookieN);
        long nova = pedidoPendente(emailN);
        verificar("aprovar farmácia nova dá 200", post("/api/admin/pedidos", origem,
                form("id", String.valueOf(nova), "acao", "aprovar"), cookieAdmin).statusCode() == 200);
        verificar("farmácia nova não aparece no mapa (sem coordenada)",
                !get("/api/pontos", null).body().contains("Drogaria Teste B3"));
        verificar("farmácia nova existe no banco sem latitude",
                contarSql("SELECT count(*) FROM farmacia WHERE nome = 'Drogaria Teste B3' AND latitude IS NULL") == 1);
        String cookieNova = cookieDe(post("/api/entrar", origem, form("email", emailN, "senha", "Senha@12345")));
        verificar("painel da farmácia sem posição salva o preço (200)", post("/api/painel", origem,
                form("acao", "salvar", "medicamento", "6", "preco", "9,99", "disponivel", "sim"), cookieNova).statusCode() == 200);
        String precosDeDipirona = get("/api/precos?medicamento=6", null).body();
        verificar("preço de farmácia sem posição aparece em /api/precos (latitude null)",
                precosDeDipirona.contains("\"nome\":\"Drogaria Teste B3\"") && precosDeDipirona.contains("\"latitude\":null"));
        verificar("painel avisa que a farmácia ainda não está no mapa",
                get("/api/painel", cookieNova).body().contains("\"temPosicao\":false"));
        verificar("telefone da farmácia nova gravado formatado (15) 99999-3333",
                contarSql("SELECT count(*) FROM farmacia WHERE nome = 'Drogaria Teste B3' AND telefone = '(15) 99999-3333'") == 1);

        String emailT = novoEmail("limite3");
        post("/api/cadastro", origem, form("nome", "Limite Teste", "email", emailT, "senha", "Senha@12345", "aceite", "sim"));
        String cookieT = cookieDe(post("/api/entrar", origem, form("email", emailT, "senha", "Senha@12345")));
        for (int i = 0; i < 5; i++) {
            post("/api/trocar-senha", origem, form("senha_atual", "errada" + i + "xyz", "nova", "Outra@12345", "repetir", "Outra@12345"), cookieT);
        }
        verificar("6ª tentativa de trocar senha (com a atual certa) dá 429", post("/api/trocar-senha", origem,
                form("senha_atual", "Senha@12345", "nova", "Outra@12345", "repetir", "Outra@12345"), cookieT).statusCode() == 429);
    }

    private static String cnpjDeFarmaciaDaBase() throws Exception {
        return consultaTexto("SELECT cnpj FROM farmacia WHERE cidade IS NOT NULL AND cnpj IS NOT NULL ORDER BY id LIMIT 1");
    }

    private static long pedidoPendente(String email) throws Exception {
        return Long.parseLong(consultaTexto("SELECT a.id FROM acesso_farmacia a JOIN conta c ON c.id = a.conta_id "
                + "WHERE c.email = '" + email + "' AND a.status = 'pendente' ORDER BY a.id DESC LIMIT 1"));
    }

    private static String tipoDaConta(String email) throws Exception {
        return consultaTexto("SELECT tipo FROM conta WHERE email = '" + email + "'");
    }

    private static int contarSql(String sql) throws Exception {
        return Integer.parseInt(consultaTexto(sql));
    }

    private static String consultaTexto(String sql) throws Exception {
        try (Connection conexao = Banco.conectar();
             PreparedStatement comando = conexao.prepareStatement(sql);
             ResultSet linha = comando.executeQuery()) {
            linha.next();
            return linha.getString(1);
        }
    }

    private static int contarPedidos(String email) throws Exception {
        try (Connection conexao = Banco.conectar();
             PreparedStatement comando = conexao.prepareStatement(
                     "SELECT count(*) FROM redefinicao_senha r JOIN conta c ON c.id = r.conta_id WHERE c.email = ?")) {
            comando.setString(1, email);
            try (ResultSet linha = comando.executeQuery()) {
                linha.next();
                return linha.getInt(1);
            }
        }
    }

    private static Object[] fotoDoPar(long farmacia, long medicamento) throws Exception {
        try (Connection conexao = Banco.conectar();
             PreparedStatement preco = conexao.prepareStatement(
                     "SELECT preco, atualizado_em, informado_por_conta_id FROM preco WHERE farmacia_id = ? AND medicamento_id = ?");
             PreparedStatement estoque = conexao.prepareStatement(
                     "SELECT quantidade FROM estoque WHERE farmacia_id = ? AND medicamento_id = ?")) {
            preco.setLong(1, farmacia);
            preco.setLong(2, medicamento);
            estoque.setLong(1, farmacia);
            estoque.setLong(2, medicamento);
            Object[] foto = new Object[4];
            try (ResultSet linha = preco.executeQuery()) {
                if (linha.next()) {
                    foto[0] = linha.getBigDecimal("preco");
                    foto[1] = linha.getTimestamp("atualizado_em");
                    foto[2] = linha.getObject("informado_por_conta_id");
                }
            }
            try (ResultSet linha = estoque.executeQuery()) {
                if (linha.next()) {
                    foto[3] = linha.getInt("quantidade");
                }
            }
            return foto;
        }
    }

    private static void restaurarPar(long farmacia, long medicamento, Object[] foto) throws Exception {
        try (Connection conexao = Banco.conectar();
             PreparedStatement preco = conexao.prepareStatement(
                     "UPDATE preco SET preco = ?, atualizado_em = ?, informado_por_conta_id = ? WHERE farmacia_id = ? AND medicamento_id = ?");
             PreparedStatement estoque = conexao.prepareStatement(
                     "UPDATE estoque SET quantidade = ? WHERE farmacia_id = ? AND medicamento_id = ?")) {
            preco.setObject(1, foto[0]);
            preco.setObject(2, foto[1]);
            preco.setObject(3, foto[2]);
            preco.setLong(4, farmacia);
            preco.setLong(5, medicamento);
            preco.executeUpdate();
            estoque.setObject(1, foto[3]);
            estoque.setLong(2, farmacia);
            estoque.setLong(3, medicamento);
            estoque.executeUpdate();
        }
    }


    private static boolean senhaConfere() throws Exception {
        String sal = Senha.novoSal();
        String hash = Senha.hash("Senha@12345", sal);
        return Senha.confere("Senha@12345", sal, hash) && !Senha.confere("Senha@1234x", sal, hash);
    }

    private static String novoEmail(String tema) {
        String email = "teste-" + tema + "-" + System.nanoTime() + "@exemplo.com";
        emailsCriados.add(email);
        return email;
    }

    private static void apagarContasDeTeste() throws Exception {
        try (Connection conexao = Banco.conectar();
             PreparedStatement comando = conexao.prepareStatement("DELETE FROM conta WHERE email = ?")) {
            for (String email : emailsCriados) {
                comando.setString(1, email);
                comando.executeUpdate();
            }
        }
        try (Connection conexao = Banco.conectar()) {
            String onde = "(SELECT id FROM farmacia WHERE cnpj = '11222333000181' AND latitude IS NULL AND nome = 'Drogaria Teste B3')";
            conexao.prepareStatement("DELETE FROM preco WHERE farmacia_id IN " + onde).executeUpdate();
            conexao.prepareStatement("DELETE FROM estoque WHERE farmacia_id IN " + onde).executeUpdate();
            conexao.prepareStatement("DELETE FROM farmacia WHERE cnpj = '11222333000181' AND latitude IS NULL AND nome = 'Drogaria Teste B3'").executeUpdate();
        }
    }

    private static String form(String... pares) throws Exception {
        StringBuilder saida = new StringBuilder();
        for (int i = 0; i < pares.length; i += 2) {
            if (saida.length() > 0) saida.append('&');
            saida.append(URLEncoder.encode(pares[i], StandardCharsets.UTF_8)).append('=')
                    .append(URLEncoder.encode(pares[i + 1], StandardCharsets.UTF_8));
        }
        return saida.toString();
    }

    private static String cookieDe(HttpResponse<String> resposta) {
        String cabecalho = resposta.headers().firstValue("Set-Cookie").orElse("");
        return cabecalho.contains(";") ? cabecalho.substring(0, cabecalho.indexOf(';')) : cabecalho;
    }

    private static HttpResponse<String> get(String caminho, String cookie) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create(base + caminho));
        if (cookie != null) pedido.header("Cookie", cookie);
        return HttpClient.newHttpClient().send(pedido.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static HttpResponse<String> post(String caminho, String origem, String corpo) throws Exception {
        return post(caminho, origem, corpo, null);
    }

    private static HttpResponse<String> post(String caminho, String origem, String corpo, String cookie) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create(base + caminho))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(corpo, StandardCharsets.UTF_8));
        if (origem != null) pedido.header("Origin", origem);
        if (cookie != null) pedido.header("Cookie", cookie);
        return HttpClient.newHttpClient().send(pedido.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static int cru(String caminho) throws Exception {
        int porta = Integer.parseInt(base.substring(base.lastIndexOf(':') + 1));
        try (Socket socket = new Socket("localhost", porta)) {
            OutputStream saida = socket.getOutputStream();
            saida.write(("GET " + caminho + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            saida.flush();
            String resposta = new String(socket.getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1);
            return Integer.parseInt(resposta.split("\r\n")[0].split(" ")[1]);
        }
    }

    private static void verificar(String descricao, boolean ok) {
        if (!ok) {
            falhas++;
        }
        System.out.println((ok ? "OK      " : "FALHOU ") + descricao);
    }
}
