package security.response;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import security.JwsDetachedSigner;

import java.io.IOException;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.List;

/**
 * Valida o header {@code X-JWS-Signature} de uma resposta: JWS Compact Detached,
 * RS256, b64=false, com o payload sendo o corpo da resposta (canonicalizado em JCS).
 *
 * Confere: algoritmo (somente RS256), b64=false, kid igual ao esperado e a assinatura.
 */
public class JwsSignatureVerifier {

    private final RSASSAVerifier verifier;
    private final String expectedKid;

    public JwsSignatureVerifier(RSAPublicKey publicKey, String expectedKid) {
        this.verifier = new RSASSAVerifier(publicKey);
        this.expectedKid = expectedKid;
    }

    public void verify(String token, byte[] body) {
        if (token == null || token.isBlank()) {
            throw new JwsVerificationException("Resposta sem o header X-JWS-Signature");
        }

        // O corpo pode ter chegado sem estar em forma canonica; a assinatura cobre o JCS.
        // Tenta o JCS primeiro e, se falhar, os bytes crus do corpo.
        List<byte[]> candidatos = new ArrayList<>();
        try {
            candidatos.add(JwsDetachedSigner.canonicalizar(body));
        } catch (IOException | RuntimeException e) {
            // corpo nao e JSON valido: segue so com os bytes crus
        }
        candidatos.add(body);

        try {
            validarHeader(JWSObject.parse(token, new Payload(candidatos.get(0))).getHeader());
            for (byte[] payload : candidatos) {
                if (JWSObject.parse(token, new Payload(payload)).verify(verifier)) {
                    return;
                }
            }
        } catch (java.text.ParseException e) {
            throw new JwsVerificationException("X-JWS-Signature mal formado (partes separadas por '.': "
                    + token.split("\\.", -1).length + ", esperado 3; valor: " + abreviar(token) + ")", e);
        } catch (com.nimbusds.jose.JOSEException e) {
            throw new JwsVerificationException("Falha ao validar a assinatura JWS", e);
        }
        throw new JwsVerificationException("Assinatura JWS invalida");
    }

    private void validarHeader(JWSHeader header) {
        if (!JWSAlgorithm.RS256.equals(header.getAlgorithm())) {
            throw new JwsVerificationException("Algoritmo JWS nao aceito: " + header.getAlgorithm());
        }
        if (header.isBase64URLEncodePayload()) {
            throw new JwsVerificationException("Header JWS deve ter b64=false");
        }
        if (!expectedKid.equals(header.getKeyID())) {
            throw new JwsVerificationException(
                    "kid inesperado na resposta: '" + header.getKeyID() + "' (esperado '" + expectedKid + "')");
        }
    }

    private static String abreviar(String token) {
        return token.length() <= 120 ? token : token.substring(0, 120) + "...";
    }
}
