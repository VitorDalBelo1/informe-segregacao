package security.response;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.cert.CertificateFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Optional;


@ApplicationScoped
public class JwsVerifierProvider {

    @ConfigProperty(name = "sispm.jws.pp.public-key")
    Optional<String> publicKey;

    /** kid esperado na resposta; se ausente, usa o mesmo kid da assinatura da requisicao. */
    @ConfigProperty(name = "sispm.jws.pp.kid")
    Optional<String> verifyKid;

    @ConfigProperty(name = "sispm.jws.kid")
    Optional<String> kid;

    private volatile boolean carregado;
    private volatile JwsSignatureVerifier verifier;

    /** Vazio quando a verificacao nao esta configurada. */
    public Optional<JwsSignatureVerifier> get() {
        if (!carregado) {
            synchronized (this) {
                if (!carregado) {
                    verifier = carregar();
                    carregado = true;
                }
            }
        }
        return Optional.ofNullable(verifier);
    }

    private JwsSignatureVerifier carregar() {
        Optional<String> pem = publicKey.filter(s -> !s.isBlank());
        if (pem.isEmpty()) {
            return null;
        }
        String expected = verifyKid.filter(s -> !s.isBlank())
                .or(() -> kid.filter(s -> !s.isBlank()))
                .orElseThrow(() -> new IllegalStateException(
                        "Configure sispm.jws.pp.kid (ou sispm.jws.kid) para validar o kid da resposta"));
        try {
            return new JwsSignatureVerifier(parsePublicKey(pem.get()), expected);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao carregar a chave publica de verificacao JWS", e);
        }
    }

    static RSAPublicKey parsePublicKey(String pem) throws Exception {
        String texto = pem.replace("\\n", "\n");
        if (texto.contains("BEGIN CERTIFICATE")) {
            var cert = CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(texto.getBytes(StandardCharsets.UTF_8)));
            return (RSAPublicKey) cert.getPublicKey();
        }
        String base64 = texto
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }
}
