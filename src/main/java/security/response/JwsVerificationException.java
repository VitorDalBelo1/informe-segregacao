package security.response;

/** Resposta da Plataforma com assinatura JWS ausente, invalida ou com kid inesperado. */
public class JwsVerificationException extends RuntimeException {

    public JwsVerificationException(String message) {
        super(message);
    }

    public JwsVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
