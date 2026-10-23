import java.io.Console;
import java.sql.SQLException;

public class CriarAdmin {

    public static void main(String[] args) throws Exception {
        // dentro de algumas IDEs o console é null, e a senha não pode ser lida
        Console console = System.console();
        if (console == null) {
            System.out.println("Rode este programa pelo terminal (cmd ou PowerShell), não pela IDE.");
            return;
        }

        String nome = console.readLine("Nome do administrador: ").trim();
        String email = Login.normalizarEmail(console.readLine("E-mail: "));
        String senha = new String(console.readPassword("Senha (pelo menos 8 caracteres): "));

        String erro = Login.validarDados(nome, email, senha);
        if (erro != null) {
            System.out.println("Não criei a conta: " + erro);
            return;
        }
        try {
            Login.criarConta(nome, email, senha, "admin");
            System.out.println("Conta de administrador criada para " + email + ".");
        } catch (SQLException erroBanco) {
            if ("23505".equals(erroBanco.getSQLState())) {
                System.out.println("Não criei a conta: já existe uma conta com este e-mail.");
            } else {
                throw erroBanco;
            }
        }
    }
}
