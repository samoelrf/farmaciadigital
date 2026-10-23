import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Json {

    public static String de(Object valor) {
        if (valor == null) {
            return "null";
        }
        if (valor instanceof String texto) {
            return texto(texto);
        }
        // toPlainString: sem notação científica (39.50 continua 39.50)
        if (valor instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        if (valor instanceof Number || valor instanceof Boolean) {
            return valor.toString();
        }
        if (valor instanceof Map<?, ?> mapa) {
            List<String> partes = new ArrayList<>();
            for (Map.Entry<?, ?> par : mapa.entrySet()) {
                partes.add(texto(par.getKey().toString()) + ":" + de(par.getValue()));
            }
            return "{" + String.join(",", partes) + "}";
        }
        if (valor instanceof List<?> lista) {
            List<String> partes = new ArrayList<>();
            for (Object item : lista) {
                partes.add(de(item));
            }
            return "[" + String.join(",", partes) + "]";
        }
        throw new IllegalArgumentException("Tipo que o Json não sabe escrever: " + valor.getClass());
    }

    public static String texto(String original) {
        StringBuilder saida = new StringBuilder("\"");
        for (char c : original.toCharArray()) {
            switch (c) {
                case '"' -> saida.append("\\\"");
                case '\\' -> saida.append("\\\\");
                case '\n' -> saida.append("\\n");
                case '\r' -> saida.append("\\r");
                case '\t' -> saida.append("\\t");
                default -> {
                    if (c < 0x20) {
                        saida.append(String.format("\\u%04x", (int) c));
                    } else {
                        saida.append(c);
                    }
                }
            }
        }
        return saida.append('"').toString();
    }
}
