package processors;

import com.fasterxml.jackson.core.io.BigDecimalParser;
import dto.*;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;
import io.smallrye.common.annotation.Blocking;
import ppclient.PixEstaticoClient;
import ppclient.SegregacaoClient;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@ApplicationScoped
public class SegregacaoProcessor {
    @ConfigProperty(name="sispm.psp.cnpj.raiz")
    private String raizCnpj;

    @Inject
    @RestClient
    SegregacaoClient client;

    @Inject
    @RestClient
    PixEstaticoClient pxeClient;

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


            StringBuilder idLote = new StringBuilder(idInfSegr);
            idLote.append(String.format("%06d", ThreadLocalRandom.current().nextInt(1000000)));

            DadosInfoSeg dadosInfoSeg = new DadosInfoSeg(idRepasse.toString(),evento.arranjo(),idInfSegr.toString());


            String dataHoraAtual = OffsetDateTime.now(ZoneOffset.ofHours(-3))
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"));

            InfRequisicao infRequisicao = new InfRequisicao(raizCnpj,dataHoraAtual);
            InformeSegregacaoRequest req = new InformeSegregacaoRequest();
            req.dadosInfoSeg=dadosInfoSeg;
            req.infRequisicao=infRequisicao;

           PPResponse resp = client.inicializarSegregacao(req);

            InformeDeSegregacaoPixEstaticoRequest pxeReq = new InformeDeSegregacaoPixEstaticoRequest();
            pxeReq.infRequisicao= infRequisicao;
            DadosLoteSeg dadosLoteSeg = new DadosLoteSeg();
            dadosLoteSeg.idLote = idLote.toString();
            pxeReq.dadosLoteSeg= dadosLoteSeg;

            Transacao transacao = new Transacao();
            transacao.setIndex(i + 1);
            transacao.setE2eId(evento.identificadorTransacao());
            transacao.setVlPago(evento.valorTransacao());
            transacao.setVlCbsSegr(evento.valorCbs());
            transacao.setVlIbsSegr(evento.valorIbs());
            transacao.setDocFiscal(evento.documentoFiscal());
            transacao.setCnpjRaizPspRecInd(evento.cnpjRecebedor() != null && evento.cnpjRecebedor().length() >= 8 ? evento.cnpjRecebedor().substring(0, 8) : null); // Exemplo extraindo a raiz caso venha completo, ou ajuste conforme seu dado
            transacao.setCnpjRaizPspPag(evento.cnpjRaizBancoPagador());
            transacao.setCnpjRec(evento.cnpjRecebedor());
            transacao.setCnpjCpfPagEfet(evento.cpfCnpjPagador());
            if (evento.dataHoraPagamento() != null) {
                transacao.setDtHrPgto(OffsetDateTime.parse(evento.dataHoraPagamento(), DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            }
            if (evento.dataHoraLiquidacao() != null) {
                OffsetDateTime dtLiq = OffsetDateTime.parse(evento.dataHoraLiquidacao(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                transacao.setDtHrLiq(dtLiq);
                // Definindo dtHrRepasse com base na liquidação + 1 dia (regra padrão comum, ajuste se necessário)
                transacao.setDtHrRepasse(dtLiq.plusDays(1));
            }

            List<Transacao> transacoes = new ArrayList<Transacao>();

            transacoes.add(transacao);
            pxeReq.transacoes = transacoes;

            PPResponse pxeResp = pxeClient.enviarLoteInformeSegregacao(idInfSegr.toString(),pxeReq);

            DadosFinalSeg dadosFinalSeg = new DadosFinalSeg(idInfSegr.toString(),transacoes.size(), evento.valorCbs(),evento.valorIbs());
            InfoSegFinalRequest segFinalRequest = new InfoSegFinalRequest();
            segFinalRequest.infRequisicao=infRequisicao;
            segFinalRequest.dadosFinalSeg=dadosFinalSeg;

            PPResponse segFinalResp = client.finalizarSegregacao(segFinalRequest);

            Log.infof("Resposta passo 1 %d",resp.status());
            Log.infof("Resposta passo 2 %d",pxeResp.status());
            Log.infof("Resposta passo 3 %d",segFinalResp.status());


        }

    }

}
