package security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSASSASigner;
import org.erdtman.jcs.JsonCanonicalizer;

import java.io.IOException;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Gera a assinatura do header {@code X-JWS-Signature} exigido pela Plataforma Publica
 * de Split Payment: JWS Compact Detached (RFC 7515) com payload nao codificado (RFC 7797).
 *
 * Header gerado: alg=RS256, typ=JWS, kid, b64=false, crit=["b64"], jti (UUID v4) e
 * iat (NumericDate, em segundos).
 *
 * O payload assinado sao exatamente os bytes recebidos (ou, se {@code canonicalizar=true},
 * esses bytes apos canonicalizacao JCS - RFC 8785). Quem envia a requisicao precisa
 * transmitir os MESMOS bytes que foram assinados.
 */
public class JwsDetachedSigner {

    private final RSASSASigner signer;
    private final String kid;
    private final boolean canonicalizar;

    public JwsDetachedSigner(PrivateKey privateKey, String kid, boolean canonicalizar) {
        this.signer = new RSASSASigner(privateKey);
        this.kid = kid;
        this.canonicalizar = canonicalizar;
    }

    /** Retorna o JWS no formato {@code <header>..<assinatura>} (payload omitido). */
    public String sign(byte[] body) throws Exception {
        byte[] payload = canonicalizar ? canonicalizar(body) : body;

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(new JOSEObjectType("JWS"))
                .keyID(kid)
                .base64URLEncodePayload(false)
                .criticalParams(Set.of("b64"))
                .customParam("jti", UUID.randomUUID().toString())
                .customParam("iat", Instant.now().getEpochSecond())
                .build();

        JWSObject jws = new JWSObject(header, new Payload(payload));
        jws.sign(signer);
        return jws.serialize(true); // true = detached content
    }

    /** Canonicalizacao JSON conforme RFC 8785 (JCS), devolvida em UTF-8. */
    public static byte[] canonicalizar(byte[] json) throws IOException {
        return new JsonCanonicalizer(json).getEncodedUTF8();
    }
}
