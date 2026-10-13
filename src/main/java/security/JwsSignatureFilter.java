package security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Provider;
import org.erdtman.jcs.JsonCanonicalizer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Filtro global dos REST clients: serializa o corpo, canonicaliza (JCS / RFC 8785),
 * assina (JWS detached RS256) e envia o header {@code X-JWS-Signature}.
 *
 * O corpo e substituido pelo JSON canonicalizado, garantindo que os bytes enviados
 * sejam identicos aos bytes assinados.
 */
@Provider
@Singleton
@Priority(Priorities.AUTHENTICATION)
public class JwsSignatureFilter implements ClientRequestFilter {

    public static final String HEADER = "X-JWS-Signature";

    @Inject
    ObjectMapper mapper;

    @Inject
    JwsSignerProvider signerProvider;

    @Override
    public void filter(ClientRequestContext ctx) throws IOException {
        if (!ctx.hasEntity()) {
            return;
        }
        try {
            Object entity = ctx.getEntity();
            byte[] json = entity instanceof String s
                    ? s.getBytes(StandardCharsets.UTF_8)
                    : mapper.writeValueAsBytes(entity);

            byte[] canonico = new JsonCanonicalizer(json).getEncodedUTF8();
            String assinatura = signerProvider.get().sign(canonico);

            ctx.getHeaders().putSingle(HEADER, assinatura);
            ctx.setEntity(new String(canonico, StandardCharsets.UTF_8),
                    ctx.getEntityAnnotations(), MediaType.APPLICATION_JSON_TYPE);
        } catch (IOException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Falha ao assinar a requisicao (JWS)", e);
        }
    }
}
