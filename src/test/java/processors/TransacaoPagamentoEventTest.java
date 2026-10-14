package processors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** Contrato JSON do evento + serializer/deserializer usados no Kafka. */
class TransacaoPagamentoEventTest {

    private static final String JSON_COMPLETO = """
            {
              "tipoEvento": 1,
              "arranjo": "PXE",
              "identificadorTransacao": "E18236120261005101530000000000012345",
              "valorTransacao": 1500.75,
              "valorCbs": 15.01,
              "valorIbs": 7.5,
              "documentoFiscal": "NF-20261005-987654",
              "agenciaContaDebitada": 1234,
              "produtoContaDebitada": 1,
              "numeroContaDebitada": "1234567890123",
              "agenciaContaCreditada": 5678,
              "produtoContaCreditada": 1,
              "numeroContaCreditada": "9876543210987",
              "compe": 104,
              "ispb": 360305,
              "cnpjRecebedor": "12345678000190",
              "nomeRecebedor": "LOJA EXEMPLO LTDA",
              "cpfCnpjPagador": "12345678901",
              "nomePagador": "CLIENTE TESTE",
              "dataHoraLiquidacao": "2026-10-05T10:15:32Z",
              "cnpjRaizBancoPagador": "00360305",
              "dataHoraPagamento": "2026-10-05T10:15:30Z",
              "nsuOrigem": "001202610051234567890"
            }
            """;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("Desserializa o JSON completo do contrato")
    void desserializaJsonCompleto() throws Exception {
        TransacaoPagamentoEvent e = mapper.readValue(JSON_COMPLETO, TransacaoPagamentoEvent.class);

        assertEquals(1, e.tipoEvento());
        assertEquals("PXE", e.arranjo());
        assertEquals("E18236120261005101530000000000012345", e.identificadorTransacao());
        assertEquals(0, new BigDecimal("1500.75").compareTo(e.valorTransacao()));
        assertEquals(0, new BigDecimal("15.01").compareTo(e.valorCbs()));
        assertEquals(0, new BigDecimal("7.5").compareTo(e.valorIbs()));
        assertEquals("NF-20261005-987654", e.documentoFiscal());
        assertEquals(1234, e.agenciaContaDebitada());
        assertEquals("1234567890123", e.numeroContaDebitada());
        assertEquals(5678, e.agenciaContaCreditada());
        assertEquals("9876543210987", e.numeroContaCreditada());
        assertEquals(104, e.compe());
        assertEquals(360305, e.ispb());
        assertEquals("12345678000190", e.cnpjRecebedor());
        assertEquals("LOJA EXEMPLO LTDA", e.nomeRecebedor());
        assertEquals("12345678901", e.cpfCnpjPagador());
        assertEquals("CLIENTE TESTE", e.nomePagador());
        assertEquals("2026-10-05T10:15:32Z", e.dataHoraLiquidacao());
        assertEquals("00360305", e.cnpjRaizBancoPagador());
        assertEquals("2026-10-05T10:15:30Z", e.dataHoraPagamento());
        assertEquals("001202610051234567890", e.nsuOrigem());
    }

    @Test
    @DisplayName("Campos desconhecidos no JSON sao ignorados (resiliencia a mudanca de schema)")
    void ignoraCamposDesconhecidos() throws Exception {
        String json = "{\"arranjo\":\"PXE\",\"campoNovoDoTopico\":\"x\",\"outro\":{\"a\":1}}";

        TransacaoPagamentoEvent e = mapper.readValue(json, TransacaoPagamentoEvent.class);

        assertEquals("PXE", e.arranjo());
    }

    @Test
    @DisplayName("Campos ausentes viram null")
    void camposAusentesViramNull() throws Exception {
        TransacaoPagamentoEvent e = mapper.readValue("{\"arranjo\":\"PXE\"}", TransacaoPagamentoEvent.class);

        assertEquals("PXE", e.arranjo());
        assertNull(e.valorTransacao());
        assertNull(e.ispb());
        assertNull(e.dataHoraLiquidacao());
        assertNull(e.cnpjRecebedor());
    }

    @Test
    @DisplayName("Serializer + Deserializer do Kafka fazem round-trip sem perder dados")
    void roundTripKafka() throws Exception {
        TransacaoPagamentoEvent original = mapper.readValue(JSON_COMPLETO, TransacaoPagamentoEvent.class);

        byte[] bytes = new SegregacaoSerializer().serialize("topico", original);
        TransacaoPagamentoEvent lido = new SegregacaoDeserializer().deserialize("topico", bytes);

        assertNotNull(lido);
        assertEquals(original.identificadorTransacao(), lido.identificadorTransacao());
        assertEquals(original.arranjo(), lido.arranjo());
        assertEquals(original.ispb(), lido.ispb());
        assertEquals(0, original.valorTransacao().compareTo(lido.valorTransacao()));
        assertEquals(0, original.valorCbs().compareTo(lido.valorCbs()));
        assertEquals(0, original.valorIbs().compareTo(lido.valorIbs()));
        assertEquals(original.dataHoraLiquidacao(), lido.dataHoraLiquidacao());
    }

    @Test
    @DisplayName("Deserializer devolve null para payload nulo")
    void deserializerComPayloadNulo() {
        assertNull(new SegregacaoDeserializer().deserialize("topico", null));
    }

    @Test
    @DisplayName("Deserializer lanca excecao para JSON quebrado (a mensagem segue para a DLQ)")
    void deserializerComJsonInvalido() {
        byte[] lixo = "isto nao e json".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        assertThrows(RuntimeException.class, () -> new SegregacaoDeserializer().deserialize("topico", lixo));
    }
}
