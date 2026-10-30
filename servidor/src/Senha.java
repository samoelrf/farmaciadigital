import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

// PBKDF2 do próprio JDK; no banco vai só o hash e o sal
public class Senha {

    // 120 mil iterações: deixa o ataque por força bruta bem mais lento
    private static final int ITERACOES = 120_000;
    private static final int TAMANHO_BITS = 256;
    public static final int MINIMO = 8;
    public static final int MAXIMO = 100;

    public static String novoSal() {
        byte[] sal = new byte[16];
        new SecureRandom().nextBytes(sal);
        return Base64.getEncoder().encodeToString(sal);
    }

    public static String hash(String senha, String saltBase64) throws GeneralSecurityException {
        byte[] sal = Base64.getDecoder().decode(saltBase64);
        KeySpec especificacao = new PBEKeySpec(senha.toCharArray(), sal, ITERACOES, TAMANHO_BITS);
        byte[] resultado = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(especificacao).getEncoded();
        return Base64.getEncoder().encodeToString(resultado);
    }

    // compara todos os bytes para não vazar nada pelo tempo de resposta
    public static boolean confere(String senha, String saltBase64, String hashBase64) throws GeneralSecurityException {
        byte[] esperado = Base64.getDecoder().decode(hashBase64);
        byte[] calculado = Base64.getDecoder().decode(hash(senha, saltBase64));
        return MessageDigest.isEqual(esperado, calculado);
    }

    public static String validar(String senha) {
        if (senha == null || senha.length() < MINIMO) {
            return "A senha precisa ter pelo menos 8 caracteres.";
        }
        if (senha.length() > MAXIMO) {
            return "A senha pode ter no máximo 100 caracteres.";
        }
        return null;
    }
}
