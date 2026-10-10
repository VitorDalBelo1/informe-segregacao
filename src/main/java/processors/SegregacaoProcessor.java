package processors;

import dto.DadosInfoSeg;
import dto.InfRequisicao;
import dto.InformeSegregacaoRequest;
import dto.PPResponse;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;
import io.smallrye.common.annotation.Blocking;
import ppclient.SegregacaoClient;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@ApplicationScoped
public class SegregacaoProcessor {
    @ConfigProperty(name="sispm.psp.cnpj.raiz")
    private String raizCnpj;

    @Inject
    @RestClient
    SegregacaoClient client;

    private static final Logger LOG = Logger.getLogger(SegregacaoProcessor.class);

    @Incoming("entrada")
    @Blocking
    public void consumir(List<TransacaoPagamentoEvent> eventos) {
        LOG.info(String.format("Lote recebido do Kafka com %d evento(s)", eventos.size()));

        for (int i = 0, size = eventos.size(); i < size; i++) {
            TransacaoPagamentoEvent evento = eventos.get(i);
            StringBuilder idRepasse = new StringBuilder();
            // Limpa o StringBuilder reaproveitando a mesma instância para todas as iterações (Zero GC Allocation extra)
            idRepasse.setLength(0);
            idRepasse
            .append(raizCnpj)
            .append(evento.ispb())
            .append(evento.arranjo())
            .append(OffsetDateTime.parse(evento.dataHoraLiquidacao(), DateTimeFormatter.ISO_OFFSET_DATE_TIME).plusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd")))
            .append(String.format("%03d", ThreadLocalRandom.current().nextInt(1000)));

            StringBuilder idInfSegr = new StringBuilder();
            idInfSegr.setLength(0);
            idInfSegr
            .append(raizCnpj)
            .append(evento.ispb())
            .append(evento.arranjo())
            .append(OffsetDateTime.parse(evento.dataHoraLiquidacao(), DateTimeFormatter.ISO_OFFSET_DATE_TIME).plusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd")))
            .append("1200")
            .append(String.format("%03d", ThreadLocalRandom.current().nextInt(1000)));


            DadosInfoSeg dadosInfoSeg = new DadosInfoSeg(idRepasse.toString(),evento.arranjo(),idInfSegr.toString());


            String dataHoraAtual = OffsetDateTime.now(ZoneOffset.ofHours(-3))
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"));

            InfRequisicao infRequisicao = new InfRequisicao(raizCnpj,dataHoraAtual);
            InformeSegregacaoRequest req = new InformeSegregacaoRequest();
            req.dadosInfoSeg=dadosInfoSeg;
            req.infRequisicao=infRequisicao;

           PPResponse resp = client.inicializarSegregacao(req);

            Log.infof("Resposta %s",resp.title());


        }

    }

}
