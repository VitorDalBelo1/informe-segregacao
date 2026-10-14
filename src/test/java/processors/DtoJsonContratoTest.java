package processors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dto.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Garante o contrato JSON enviado para (e recebido de) a Plataforma Publica:
 * nomes dos campos, aninhamento e formato das datas.
 */
class DtoJsonContratoTest {

    // Mesma configuracao relevante do ObjectMapper do Quarkus: datas em ISO-8601, nao timestamp numerico
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static InfRequisicao inf() {
        return new InfRequisicao("11222333", "2026-10-05T10:15:32-03:00");
    }

    @Test
    @DisplayName("InformeSegregacaoRequest (passo 1) serializa com os nomes esperados")
    void passo1Serializa() {
        InformeSegregacaoRequest req = new InformeSegregacaoRequest();
        req.infRequisicao = inf();
        req.dadosInfoSeg = new DadosInfoSeg("REPASSE1", "PXE", "SEG1");

        JsonNode json = mapper.valueToTree(req);

        assertEquals("11222333", json.at("/infRequisicao/cnpjRaizPspRecDir").asText());
        assertEquals("2026-10-05T10:15:32-03:00", json.at("/infRequisicao/dtHrMsg").asText());
        assertEquals("REPASSE1", json.at("/dadosInfoSeg/idRepasse").asText());
        assertEquals("PXE", json.at("/dadosInfoSeg/arrj").asText());
        assertEquals("SEG1", json.at("/dadosInfoSeg/idInfSegr").asText());
    }

    @Test
    @DisplayName("InformeDeSegregacaoPixEstaticoRequest (passo 2) serializa lote e transacoes")
    void passo2Serializa() {
        Transacao t = new Transacao();
        t.setIndex(1);
        t.setE2eId("E123");
        t.setVlPago(new BigDecimal("100.00"));
        t.setVlCbsSegr(new BigDecimal("1.00"));
        t.setVlIbsSegr(new BigDecimal("0.50"));
        t.setDocFiscal("NF-1");
        t.setCnpjRaizPspRecInd("12345678");
        t.setCnpjRaizPspPag("00360305");
        t.setCnpjRec("12345678000190");
        t.setCnpjCpfPagEfet("12345678901");

        DadosLoteSeg lote = new DadosLoteSeg();
        lote.idLote = "LOTE1";

        InformeDeSegregacaoPixEstaticoRequest req = new InformeDeSegregacaoPixEstaticoRequest();
        req.infRequisicao = inf();
        req.dadosLoteSeg = lote;
        req.transacoes = List.of(t);

        JsonNode json = mapper.valueToTree(req);

        assertEquals("LOTE1", json.at("/dadosLoteSeg/idLote").asText());
        JsonNode tj = json.at("/transacoes/0");
        assertEquals(1, tj.get("index").asInt());
        assertEquals("E123", tj.get("e2eId").asText());
        assertEquals(0, new BigDecimal("100.00").compareTo(tj.get("vlPago").decimalValue()));
        assertEquals(0, new BigDecimal("1.00").compareTo(tj.get("vlCbsSegr").decimalValue()));
        assertEquals(0, new BigDecimal("0.50").compareTo(tj.get("vlIbsSegr").decimalValue()));
        assertEquals("NF-1", tj.get("docFiscal").asText());
        assertEquals("12345678", tj.get("cnpjRaizPspRecInd").asText());
        assertEquals("00360305", tj.get("cnpjRaizPspPag").asText());
        assertEquals("12345678000190", tj.get("cnpjRec").asText());
        assertEquals("12345678901", tj.get("cnpjCpfPagEfet").asText());
    }

    @Test
    @DisplayName("Datas da Transacao saem em ISO-8601 (string), nao como timestamp numerico")
    void datasDaTransacaoEmIso8601() {
        Transacao t = new Transacao();
        t.setDtHrPgto(OffsetDateTime.parse("2026-10-05T10:15:30Z"));
        t.setDtHrLiq(OffsetDateTime.parse("2026-10-05T10:15:32Z"));
        t.setDtHrRepasse(OffsetDateTime.parse("2026-10-06T10:15:32Z"));

        JsonNode json = mapper.valueToTree(t);

        assertEquals("2026-10-05T10:15:30Z", json.get("dtHrPgto").asText());
        assertEquals("2026-10-05T10:15:32Z", json.get("dtHrLiq").asText());
        assertEquals("2026-10-06T10:15:32Z", json.get("dtHrRepasse").asText());
        assertTrue(json.get("dtHrPgto").isTextual());
    }

    @Test
    @DisplayName("InfoSegFinalRequest (passo 3) serializa totais")
    void passo3Serializa() {
        InfoSegFinalRequest req = new InfoSegFinalRequest();
        req.infRequisicao = inf();
        req.dadosFinalSeg = new DadosFinalSeg("SEG1", 1, new BigDecimal("15.01"), new BigDecimal("7.50"));

        JsonNode json = mapper.valueToTree(req);

        assertEquals("SEG1", json.at("/dadosFinalSeg/idInfSegr").asText());
        assertEquals(1, json.at("/dadosFinalSeg/totalTrans").asInt());
        assertEquals(0, new BigDecimal("15.01").compareTo(json.at("/dadosFinalSeg/vlTotalCbs").decimalValue()));
        assertEquals(0, new BigDecimal("7.50").compareTo(json.at("/dadosFinalSeg/vlTotalIbs").decimalValue()));
    }

    @Test
    @DisplayName("PPResponse desserializa a resposta da Plataforma e ignora campos desconhecidos")
    void ppResponseDesserializa() throws Exception {
        String json = """
                {
                  "title": "Created",
                  "status": 201,
                  "detail": "Lote aceito",
                  "numValidos": 1,
                  "numErros": 1,
                  "errors": [
                    { "index": 0, "field": "vlPago", "message": "valor invalido", "campoNovo": true }
                  ],
                  "resourceId": "abc-123",
                  "campoQueNaoExiste": "x"
                }
                """;

        PPResponse r = mapper.readValue(json, PPResponse.class);

        assertEquals("Created", r.title());
        assertEquals(201, r.status());
        assertEquals("Lote aceito", r.detail());
        assertEquals(1, r.numValidos());
        assertEquals(1, r.numErros());
        assertEquals("abc-123", r.resourceId());
        assertEquals(1, r.errors().size());
        assertEquals(0, r.errors().get(0).index());
        assertEquals("vlPago", r.errors().get(0).field());
        assertEquals("valor invalido", r.errors().get(0).message());
    }

    @Test
    @DisplayName("PPResponse aceita resposta minima (so status)")
    void ppResponseMinima() throws Exception {
        PPResponse r = mapper.readValue("{\"status\":200}", PPResponse.class);

        assertEquals(200, r.status());
        assertNull(r.errors());
        assertNull(r.resourceId());
    }
}
