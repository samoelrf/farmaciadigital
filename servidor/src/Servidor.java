import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.concurrent.Executors;

public class Servidor {

    private static final int PORTA = 8080;

    public static void main(String[] args) throws IOException {
        HttpServer servidor = criar(PORTA, Path.of("..", "site"));
        servidor.start();
        System.out.println("Servidor no ar em http://localhost:" + PORTA + " (Ctrl+C para parar)");
    }

    // sem iniciar; o teste passa porta 0 (o sistema escolhe uma livre)
    public static HttpServer criar(int porta, Path site) throws IOException {
        Path raiz = site.toAbsolutePath().normalize();
        HttpServer servidor = HttpServer.create(new InetSocketAddress(porta), 0);
        // /api primeiro; a barra pega o resto, que é o site
        servidor.createContext("/api", Servidor::api);
        servidor.createContext("/", troca -> arquivo(troca, raiz));
        // 8 threads: um login lento não trava o site para os outros
        servidor.setExecutor(Executors.newFixedThreadPool(8));
        return servidor;
    }

    private static void api(HttpExchange troca) throws IOException {
        String caminho = troca.getRequestURI().getPath();
        if (Login.trata(troca, caminho)) {
            return;
        }
        if (Favoritos.trata(troca, caminho) || PainelFarmacia.trata(troca, caminho) || RedefinirSenha.trata(troca, caminho)
                || Admin.trata(troca, caminho) || ServicosFarmacia.trata(troca, caminho)
                || EstoqueFarmacia.trata(troca, caminho)) {
            return;
        }
        if (!"GET".equals(troca.getRequestMethod())) {
            enviarTexto(troca, 405, "Método não permitido");
            return;
        }
        if (caminho.equals("/api/catalogo") && !Login.exigirAdmin(troca)) {
            return;
        }
        String consulta = troca.getRequestURI().getRawQuery();
        try {
            String json = switch (caminho) {
                case "/api/pontos" -> Rotas.pontos();
                case "/api/medicamentos" -> Rotas.medicamentos(consulta);
                case "/api/precos" -> Rotas.precos(consulta);
                case "/api/catalogo" -> Rotas.catalogo();
                default -> null;
            };
            if (json == null) {
                enviarTexto(troca, 404, "Página não encontrada");
            } else {
                enviar(troca, 200, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IllegalArgumentException erro) {
            enviarTexto(troca, 400, erro.getMessage());
        } catch (SQLException erro) {
            System.err.println("Erro no banco em " + caminho + ": " + erro.getMessage());
            enviarTexto(troca, 500, "Erro no servidor");
        }
    }

    private static void arquivo(HttpExchange troca, Path raiz) throws IOException {
        if (!"GET".equals(troca.getRequestMethod())) {
            enviarTexto(troca, 405, "Método não permitido");
            return;
        }
        String caminho = troca.getRequestURI().getPath().substring(1);
        if (caminho.isEmpty() || caminho.endsWith("/")) {
            caminho += "index.html";
        }
        Path arquivo = dentroDaPasta(raiz, caminho);
        if (arquivo == null || !Files.isRegularFile(arquivo)) {
            enviarTexto(troca, 404, "Página não encontrada");
            return;
        }
        enviar(troca, 200, tipoDe(arquivo), Files.readAllBytes(arquivo));
    }

    // resolve antes de conferir: '..' escondido (%2e%2e) também conta
    private static Path dentroDaPasta(Path raiz, String caminho) {
        try {
            Path arquivo = raiz.resolve(caminho).normalize();
            return arquivo.startsWith(raiz) ? arquivo : null;
        } catch (InvalidPathException erro) {
            return null;
        }
    }

    static void enviarTexto(HttpExchange troca, int status, String texto) throws IOException {
        enviar(troca, status, "text/plain; charset=utf-8", texto.getBytes(StandardCharsets.UTF_8));
    }

    static void enviar(HttpExchange troca, int status, String tipo, byte[] corpo) throws IOException {
        troca.getResponseHeaders().set("Content-Type", tipo);
        troca.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        troca.sendResponseHeaders(status, corpo.length);
        try (OutputStream saida = troca.getResponseBody()) {
            saida.write(corpo);
        }
    }

    private static String tipoDe(Path arquivo) {
        String nome = arquivo.getFileName().toString().toLowerCase();
        if (nome.endsWith(".html")) return "text/html; charset=utf-8";
        if (nome.endsWith(".css")) return "text/css; charset=utf-8";
        if (nome.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (nome.endsWith(".json") || nome.endsWith(".geojson")) return "application/json; charset=utf-8";
        if (nome.endsWith(".png")) return "image/png";
        if (nome.endsWith(".jpg") || nome.endsWith(".jpeg")) return "image/jpeg";
        if (nome.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }
}
