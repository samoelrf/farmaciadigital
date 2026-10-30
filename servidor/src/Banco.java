import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

// a senha vem de DB_PASSWORD, nunca fica no código
public class Banco {

    public static Connection conectar() throws SQLException {
        String senha = System.getenv("DB_PASSWORD");
        if (senha == null) {
            senha = "";
        }
        return DriverManager.getConnection("jdbc:postgresql://localhost:5432/farmaciadigital", "postgres", senha);
    }
}
