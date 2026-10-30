import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Properties;

// sem SMTP_USUARIO e SMTP_SENHA não envia nada (modo de desenvolvimento)
class Email {

    static boolean configurado() {
        return ambiente("SMTP_USUARIO") != null && ambiente("SMTP_SENHA") != null;
    }

    static void enviar(String destino, String assunto, String texto) throws MessagingException {
        String host = ambiente("SMTP_HOST") != null ? ambiente("SMTP_HOST") : "smtp.gmail.com";
        String porta = ambiente("SMTP_PORTA") != null ? ambiente("SMTP_PORTA") : "587";
        String usuario = ambiente("SMTP_USUARIO");

        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", porta);
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");

        Session sessao = Session.getInstance(props);
        MimeMessage mensagem = new MimeMessage(sessao);
        mensagem.setFrom(new InternetAddress(usuario));
        mensagem.setRecipient(Message.RecipientType.TO, new InternetAddress(destino));
        mensagem.setSubject(assunto, "UTF-8");
        mensagem.setText(texto, "UTF-8");

        try (Transport transporte = sessao.getTransport("smtp")) {
            transporte.connect(host, usuario, ambiente("SMTP_SENHA"));
            transporte.sendMessage(mensagem, mensagem.getAllRecipients());
        }
    }

    static String ambiente(String nome) {
        String valor = System.getenv(nome);
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
