import dto.InformeSegregacaoRequest;
import dto.PPResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/api/v1/segregacao")
@RegisterRestClient(configKey = "pp-split-payment")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface SegregacaoClient {
    @POST
    @ClientHeaderParam(name = "X-JWS-Signature", value = "${pp.jws-signature}")
    PPResponse inicializarSegregacao(InformeSegregacaoRequest request);
}