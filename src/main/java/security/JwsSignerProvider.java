package security;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.ByteArrayOutputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Optional;

/**
 * Recebe o VALOR da chave privada RSA (sispm.jws.private-key) e entrega o {@link JwsDetachedSigner}.
 *
 * Formatos aceitos:
 *  - PEM PKCS#8:  -----BEGIN PRIVATE KEY-----
 *  - PEM PKCS#1:  -----BEGIN RSA PRIVATE KEY-----
 *  - apenas o Base64 (sem cabecalho/rodape), tratado como PKCS#8
 * Chaves protegidas por senha (ENCRYPTED PRIVATE KEY) nao sao suportadas.
 *
 * A carga e preguicosa: so ocorre na primeira assinatura, entao o profile 'local'
 * (mock) sobe sem chave configurada.
 */
@ApplicationScoped
public class JwsSignerProvider {

    /** Valor da chave privada RSA (PEM completo ou apenas o Base64 do PKCS#8). */
    @ConfigProperty(name = "sispm.jws.private-key")
    Optional<String> privateKey;

    /** Valor do 'kid' no header; deve coincidir com a chave publica cadastrada na Plataforma. */
    @ConfigProperty(name = "sispm.jws.kid")
    Optional<String> kid;

    private volatile JwsDetachedSigner signer;

    public JwsDetachedSigner get() {
        JwsDetachedSigner s = signer;
        if (s == null) {
            synchronized (this) {
                if (signer == null) {
                    signer = carregar();
                }
                s = signer;
            }
        }
        return s;
    }

    private JwsDetachedSigner carregar() {
        String kidValue = keyValue(kid)
                .orElseThrow(() -> new IllegalStateException("Propriedade obrigatoria nao configurada: sispm.jws.kid"));
        try {
            String pem = keyValue(privateKey).orElseThrow(() -> new IllegalStateException(
                    "Propriedade obrigatoria nao configurada: sispm.jws.private-key")).replace("\\n", "\n");
            // O filtro ja entrega o corpo canonicalizado (JCS), entao nao precisa canonicalizar de novo.
            return new JwsDetachedSigner(parsePrivateKey(pem), kidValue, false);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao carregar a chave privada de assinatura JWS", e);
        }
    }

    static PrivateKey parsePrivateKey(String pem) throws Exception {
        if (pem.contains("ENCRYPTED PRIVATE KEY")) {
            throw new IllegalStateException("Chave privada protegida por senha nao e suportada; use uma chave sem senha");
        }
        boolean pkcs1 = pem.contains("BEGIN RSA PRIVATE KEY");
        String base64 = pem
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        if (pkcs1) {
            der = pkcs1ToPkcs8(der);
        }
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    /** Embrulha uma chave RSA PKCS#1 no envelope PKCS#8 (que e o que o JDK le). */
    private static byte[] pkcs1ToPkcs8(byte[] pkcs1) {
        byte[] version = {0x02, 0x01, 0x00};
        byte[] algId = {0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7,
                0x0d, 0x01, 0x01, 0x01, 0x05, 0x00};
        byte[] octet = der(0x04, pkcs1);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(version);
        body.writeBytes(algId);
        body.writeBytes(octet);
        return der(0x30, body.toByteArray());
    }

    private static byte[] der(int tag, byte[] content) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        int len = content.length;
        if (len < 128) {
            out.write(len);
        } else if (len < 256) {
            out.write(0x81);
            out.write(len);
        } else if (len < 65536) {
            out.write(0x82);
            out.write(len >> 8);
            out.write(len & 0xff);
        } else {
            out.write(0x83);
            out.write(len >> 16);
            out.write((len >> 8) & 0xff);
            out.write(len & 0xff);
        }
        out.writeBytes(content);
        return out.toByteArray();
    }

    private static Optional<String> keyValue(Optional<String> v) {
        return v.filter(s -> !s.isBlank());
    }
}
