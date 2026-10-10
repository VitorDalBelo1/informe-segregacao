package ppclient;

import dto.InformeDeSegregacaoPixEstaticoRequest;
import dto.PPResponse;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/api/v1/pix-estatico")
@RegisterRestClient(configKey = "pp-split-payment")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface PixEstaticoClient {
    @POST
    @Path("/segregacao/{idInfSegr}/lotes")
    PPResponse enviarLoteInformeSegregacao(@PathParam("idInfSegr") String idInfSegr, InformeDeSegregacaoPixEstaticoRequest request);
}