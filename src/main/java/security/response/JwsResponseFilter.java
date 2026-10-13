package security.response;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientResponseContext;
import jakarta.ws.rs.client.ClientResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import security.JwsSignatureFilter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;

/**
 * Valida o header {@code X-JWS-Signature} das respostas (assinatura e kid).
 * Se a validacao falhar, a chamada ao client lanca {@link JwsVerificationException}.
 *
 * Sem sispm.jws.verify.public-key configurada, nao faz nada.
 */
@Provider
@Singleton
@Priority(Priorities.AUTHENTICATION)
public class JwsResponseFilter implements ClientResponseFilter {

    private static final Logger LOG = Logger.getLogger(JwsResponseFilter.class);

    @Inject
    JwsVerifierProvider verifierProvider;

    /** true = rejeita respostas 2xx que venham sem o header X-JWS-Signature. */
    @ConfigProperty(name = "sispm.jws.pp.required", defaultValue = "false")
    boolean required;

    @Override
    public void filter(ClientRequestContext request, ClientResponseContext response) throws IOException {
        Optional<JwsSignatureVerifier> verifier = verifierProvider.get();
        if (verifier.isEmpty()) {
            return;
        }

        String token = response.getHeaderString(JwsSignatureFilter.HEADER);
        boolean sucesso = response.getStatus() >= 200 && response.getStatus() < 300;
        if (token == null || token.isBlank()) {
            if (required && sucesso) {
                throw new JwsVerificationException("Resposta " + response.getStatus() + " sem X-JWS-Signature");
            }
            return;
        }

        // Le o corpo para validar e devolve um stream novo para o client continuar normalmente.
        byte[] body = response.getEntityStream() == null
                ? new byte[0]
                : response.getEntityStream().readAllBytes();
        response.setEntityStream(new ByteArrayInputStream(body));

        try {
            verifier.get().verify(token, body);
        } catch (JwsVerificationException e) {
            LOG.warnf("Falha na validacao JWS da resposta %s %s (HTTP %d): %s",
                    request.getMethod(), request.getUri(), response.getStatus(), e.getMessage());
            throw e;
        }
        LOG.debugf("Assinatura JWS da resposta validada (%s %s)", request.getMethod(), request.getUri());
    }
}
