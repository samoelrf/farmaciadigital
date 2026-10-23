import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Rotas {

    private static final String SQL_PONTOS = """
            SELECT id, nome, endereco,
                   CAST(latitude AS DOUBLE PRECISION) AS latitude,
                   CAST(longitude AS DOUBLE PRECISION) AS longitude,
                   'farmacia' AS tipo, cidade, tipo AS tipo_farmacia, precisao_posicao,
                   (cidade IS NULL) AS exemplo,
                   COALESCE(horario_funcionamento,
                       CASE WHEN hora_abertura IS NOT NULL AND hora_fechamento IS NOT NULL
                            THEN 'Mo-Su ' || to_char(hora_abertura, 'HH24:MI') || '-' || to_char(hora_fechamento, 'HH24:MI')
                       END) AS horario_funcionamento,
                   telefone,
                   COALESCE(fonte_horario,
                       CASE WHEN hora_abertura IS NOT NULL THEN 'Dados fictícios (exemplo)' END) AS fonte_horario
            FROM farmacia
            WHERE latitude IS NOT NULL   -- farmácia aprovada sem posição não aparece no mapa
            UNION ALL
            SELECT id, nome, endereco,
                   CAST(latitude AS DOUBLE PRECISION) AS latitude,
                   CAST(longitude AS DOUBLE PRECISION) AS longitude,
                   'ubs' AS tipo, cidade, NULL AS tipo_farmacia, precisao_posicao, NULL AS exemplo,
                   horario_funcionamento, telefone, fonte_horario
            FROM ubs
            """;

    private static final String SQL_MEDICAMENTOS = """
            SELECT id, nome FROM medicamento
            WHERE nome ILIKE ?
            ORDER BY nome
            LIMIT 10
            """;

    private static final String SQL_PRECOS = """
            SELECT f.nome, f.endereco, p.preco, p.url_produto, p.atualizado_em,
                   f.latitude, f.longitude, (f.cidade IS NULL) AS exemplo,
                   p.informado_por_conta_id
            FROM preco p
            JOIN farmacia f ON f.id = p.farmacia_id
            JOIN estoque e ON e.farmacia_id = p.farmacia_id AND e.medicamento_id = p.medicamento_id
            WHERE p.medicamento_id = ?
              AND e.quantidade > 0
            ORDER BY p.preco, f.nome
            """;

    private static final String SQL_CATALOGO = """
            SELECT f.id AS farmacia_id, f.nome AS farmacia, f.endereco, f.cidade,
                   (f.cidade IS NULL) AS exemplo,
                   m.id AS medicamento_id, m.nome AS medicamento,
                   e.quantidade, p.preco,
                   GREATEST(e.atualizado_em, p.atualizado_em) AS atualizado_em,
                   p.informado_por_conta_id
            FROM (SELECT farmacia_id, medicamento_id FROM estoque
                  UNION
                  SELECT farmacia_id, medicamento_id FROM preco) par
            JOIN farmacia f ON f.id = par.farmacia_id
            JOIN medicamento m ON m.id = par.medicamento_id
            LEFT JOIN estoque e ON e.farmacia_id = par.farmacia_id AND e.medicamento_id = par.medicamento_id
            LEFT JOIN preco p ON p.farmacia_id = par.farmacia_id AND p.medicamento_id = par.medicamento_id
            ORDER BY f.nome, m.nome
            """;

    public static String pontos() throws SQLException {
        List<Map<String, Object>> lista = new ArrayList<>();
        try (Connection conexao = Banco.conectar();
             PreparedStatement comando = conexao.prepareStatement(SQL_PONTOS);
             ResultSet linhas = comando.executeQuery()) {
            while (linhas.next()) {
                Map<String, Object> ponto = new LinkedHashMap<>();
                ponto.put("id", linhas.getLong("id"));
                ponto.put("nome", linhas.getString("nome"));
                ponto.put("endereco", linhas.getString("endereco"));
                ponto.put("latitude", linhas.getDouble("latitude"));
                ponto.put("longitude", linhas.getDouble("longitude"));
                ponto.put("tipo", linhas.getString("tipo"));
                ponto.put("cidade", linhas.getString("cidade"));
                ponto.put("tipoFarmacia", linhas.getString("tipo_farmacia"));
                ponto.put("precisaoPosicao", linhas.getString("precisao_posicao"));
                // getObject: UBS não tem a flag, e getBoolean trocaria null por false
                ponto.put("exemplo", linhas.getObject("exemplo", Boolean.class));
                ponto.put("horarioFuncionamento", linhas.getString("horario_funcionamento"));
                ponto.put("telefone", linhas.getString("telefone"));
                ponto.put("fonteHorario", linhas.getString("fonte_horario"));
                lista.add(ponto);
            }
        }
        return Json.de(lista);
    }

    public static String medicamentos(String consulta) throws SQLException {
        String nome = parametro(consulta, "nome");
        if (nome == null || nome.trim().isEmpty()) {
            return "[]";
        }
        nome = nome.trim();
        if (nome.length() > 100) {
            throw new IllegalArgumentException("O nome pode ter no máximo 100 caracteres.");
        }
        // % e _ são curingas do LIKE: escapa para buscar o texto literal
        String padrao = "%" + nome.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";

        List<Map<String, Object>> lista = new ArrayList<>();
        try (Connection conexao = Banco.conectar();
             PreparedStatement comando = conexao.prepareStatement(SQL_MEDICAMENTOS)) {
            comando.setString(1, padrao);
            try (ResultSet linhas = comando.executeQuery()) {
                while (linhas.next()) {
                    Map<String, Object> remedio = new LinkedHashMap<>();
                    remedio.put("id", linhas.getLong("id"));
                    remedio.put("nome", linhas.getString("nome"));
                    lista.add(remedio);
                }
            }
        }
        return Json.de(lista);
    }

    public static String precos(String consulta) throws SQLException {
        String texto = parametro(consulta, "medicamento");
        if (texto == null) {
            throw new IllegalArgumentException("Informe o parâmetro medicamento (o número do remédio).");
        }
        long medicamento;
        try {
            medicamento = Long.parseLong(texto.trim());
        } catch (NumberFormatException erro) {
            throw new IllegalArgumentException("O parâmetro medicamento deve ser um número.");
        }
        if (medicamento <= 0) {
            throw new IllegalArgumentException("O parâmetro medicamento deve ser maior que zero.");
        }

        List<Map<String, Object>> lista = new ArrayList<>();
        try (Connection conexao = Banco.conectar();
             PreparedStatement comando = conexao.prepareStatement(SQL_PRECOS)) {
            comando.setLong(1, medicamento);
            try (ResultSet linhas = comando.executeQuery()) {
                while (linhas.next()) {
                    Map<String, Object> preco = new LinkedHashMap<>();
                    preco.put("nome", linhas.getString("nome"));
                    preco.put("endereco", linhas.getString("endereco"));
                    preco.put("preco", linhas.getBigDecimal("preco"));
                    preco.put("urlProduto", linhas.getString("url_produto"));
                    preco.put("atualizadoEm", DateTimeFormatter.ISO_LOCAL_DATE_TIME
                            .format(linhas.getTimestamp("atualizado_em").toLocalDateTime()));
                    java.math.BigDecimal lat = linhas.getBigDecimal("latitude");
                    java.math.BigDecimal lng = linhas.getBigDecimal("longitude");
                    preco.put("latitude", lat == null ? null : lat.doubleValue());
                    preco.put("longitude", lng == null ? null : lng.doubleValue());
                    preco.put("exemplo", linhas.getBoolean("exemplo"));
                    preco.put("informadoPelaFarmacia", linhas.getObject("informado_por_conta_id") != null);
                    lista.add(preco);
                }
            }
        }
        return Json.de(lista);
    }

    public static String catalogo() throws SQLException {
        List<Map<String, Object>> lista = new ArrayList<>();
        try (Connection conexao = Banco.conectar();
             PreparedStatement comando = conexao.prepareStatement(SQL_CATALOGO);
             ResultSet linhas = comando.executeQuery()) {
            while (linhas.next()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("farmaciaId", linhas.getLong("farmacia_id"));
                item.put("farmacia", linhas.getString("farmacia"));
                item.put("endereco", linhas.getString("endereco"));
                item.put("cidade", linhas.getString("cidade"));
                item.put("exemplo", linhas.getBoolean("exemplo"));
                item.put("medicamentoId", linhas.getLong("medicamento_id"));
                item.put("medicamento", linhas.getString("medicamento"));
                item.put("quantidade", linhas.getObject("quantidade", Integer.class));
                item.put("preco", linhas.getBigDecimal("preco"));
                java.sql.Timestamp atualizado = linhas.getTimestamp("atualizado_em");
                item.put("atualizadoEm", atualizado == null ? null
                        : DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(atualizado.toLocalDateTime()));
                item.put("informadoPelaFarmacia", linhas.getObject("informado_por_conta_id") != null);
                lista.add(item);
            }
        }
        return Json.de(lista);
    }

    static String parametro(String consulta, String nome) {
        if (consulta == null) {
            return null;
        }
        for (String par : consulta.split("&")) {
            if (par.startsWith(nome + "=")) {
                try {
                    return URLDecoder.decode(par.substring(nome.length() + 1), StandardCharsets.UTF_8);
                } catch (IllegalArgumentException erro) {
                    throw new IllegalArgumentException("Parâmetro mal formado.");
                }
            }
        }
        return null;
    }
}
