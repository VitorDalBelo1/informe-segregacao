package processors;

import dto.InfRequisicao;
import dto.InfoSegFinalRequest;
import dto.InformeDeSegregacaoPixEstaticoRequest;
import dto.InformeSegregacaoRequest;
import dto.PPResponse;
import dto.Transacao;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import ppclient.PixEstaticoClient;
import ppclient.SegregacaoClient;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitarios do SegregacaoProcessor (sem Quarkus, sem Kafka, sem rede).
 * Os dois clientes REST sao mocks; o campo @ConfigProperty e preenchido por reflexao.
 */
class SegregacaoProcessorTest {

    private static final String RAIZ = "11222333";

    private SegregacaoClient segregacaoClient;
    private PixEstaticoClient pixClient;
    private SegregacaoProcessor processor;

    @BeforeEach
    void setUp() throws Exception {
        segregacaoClient = mock(SegregacaoClient.class);
        pixClient = mock(PixEstaticoClient.class);

        processor = new SegregacaoProcessor();
        processor.client = segregacaoClient;   // campos package-private (mesmo pacote do teste)
        processor.pxeClient = pixClient;
        setField(processor, "raizCnpj", RAIZ);  // campo private com @ConfigProperty

        when(segregacaoClient.inicializarSegregacao(any())).thenReturn(resposta(201));
        when(pixClient.enviarLoteInformeSegregacao(anyString(), any())).thenReturn(resposta(201));
        when(segregacaoClient.finalizarSegregacao(any())).thenReturn(resposta(200));
    }

    // ------------------------------------------------------------------
    // Fluxo geral
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Chama a Plataforma Publica em 3 passos, na ordem: inicializar -> lote -> finalizar")
    void chamaOsTresPassosNaOrdemCorreta() {
        processor.consumir(List.of(eventoPadrao()));

        InOrder ordem = inOrder(segregacaoClient, pixClient);
        ordem.verify(segregacaoClient).inicializarSegregacao(any());
        ordem.verify(pixClient).enviarLoteInformeSegregacao(anyString(), any());
        ordem.verify(segregacaoClient).finalizarSegregacao(any());
        ordem.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName("Lista vazia nao dispara nenhuma chamada")
    void listaVaziaNaoChamaNada() {
        processor.consumir(List.of());

        verifyNoInteractions(segregacaoClient, pixClient);
    }

    @Test
    @DisplayName("Cada evento do lote gera seu proprio ciclo de 3 chamadas")
    void cadaEventoGeraUmCicloCompleto() {
        processor.consumir(List.of(
                evento("E1", "12345678000190", "2026-10-05T10:15:32Z", "2026-10-05T10:15:30Z"),
                evento("E2", "12345678000190", "2026-10-05T10:15:32Z", "2026-10-05T10:15:30Z"),
                evento("E3", "12345678000190", "2026-10-05T10:15:32Z", "2026-10-05T10:15:30Z")));

        verify(segregacaoClient, times(3)).inicializarSegregacao(any());
        verify(pixClient, times(3)).enviarLoteInformeSegregacao(anyString(), any());
        verify(segregacaoClient, times(3)).finalizarSegregacao(any());
    }

    @Test
    @DisplayName("index da transacao segue a posicao do evento no lote recebido (i + 1)")
    void indiceDaTransacaoSegueAPosicaoNoLote() {
        processor.consumir(List.of(
                evento("E1", "12345678000190", "2026-10-05T10:15:32Z", "2026-10-05T10:15:30Z"),
                evento("E2", "12345678000190", "2026-10-05T10:15:32Z", "2026-10-05T10:15:30Z"),
                evento("E3", "12345678000190", "2026-10-05T10:15:32Z", "2026-10-05T10:15:30Z")));

        ArgumentCaptor<InformeDeSegregacaoPixEstaticoRequest> captor =
                ArgumentCaptor.forClass(InformeDeSegregacaoPixEstaticoRequest.class);
        verify(pixClient, times(3)).enviarLoteInformeSegregacao(anyString(), captor.capture());

        List<InformeDeSegregacaoPixEstaticoRequest> lotes = captor.getAllValues();
        assertEquals(List.of(1, 2, 3), lotes.stream().map(l -> l.transacoes.get(0).getIndex()).toList());
        assertEquals(List.of("E1", "E2", "E3"), lotes.stream().map(l -> l.transacoes.get(0).getE2eId()).toList());
    }

    // ------------------------------------------------------------------
    // Identificadores
    // ------------------------------------------------------------------

    @Test
    @DisplayName("idInfSegr = raiz + ispb + arranjo + (liquidacao + 1 dia, yyyyMMdd) + '1200' + 3 digitos")
    void formatoDoIdInfSegr() {
        Chamadas c = processarUmEvento(eventoPadrao());

        assertTrue(c.passo1().dadosInfoSeg.idInfSegr().matches("^11222333360305PXE202610061200\\d{3}$"),
                "idInfSegr fora do formato: " + c.passo1().dadosInfoSeg.idInfSegr());
    }

    @Test
    @DisplayName("idRepasse = raiz + ispb + arranjo + (liquidacao + 1 dia, yyyyMMdd) + 3 digitos")
    void formatoDoIdRepasse() {
        Chamadas c = processarUmEvento(eventoPadrao());

        assertTrue(c.passo1().dadosInfoSeg.idRepasse().matches("^11222333360305PXE20261006\\d{3}$"),
                "idRepasse fora do formato: " + c.passo1().dadosInfoSeg.idRepasse());
    }

    @Test
    @DisplayName("arrj do passo 1 e o arranjo do evento")
    void arranjoEhRepassadoParaOPasso1() {
        Chamadas c = processarUmEvento(eventoPadrao());

        assertEquals("PXE", c.passo1().dadosInfoSeg.arrj());
    }

    @Test
    @DisplayName("O mesmo idInfSegr e usado nos 3 passos e o idLote e idInfSegr + 6 digitos")
    void idsSaoConsistentesEntreOsPassos() {
        Chamadas c = processarUmEvento(eventoPadrao());

        String idInfSegr = c.passo1().dadosInfoSeg.idInfSegr();
        assertEquals(idInfSegr, c.idInfSegrNoPath(), "path do passo 2 difere do idInfSegr do passo 1");
        assertEquals(idInfSegr, c.passo3().dadosFinalSeg.idInfSegr(), "idInfSegr do passo 3 difere do passo 1");

        String idLote = c.passo2().dadosLoteSeg.idLote;
        assertTrue(Pattern.matches(Pattern.quote(idInfSegr) + "\\d{6}", idLote),
                "idLote deveria ser idInfSegr + 6 digitos, mas foi: " + idLote);
    }

    @Test
    @DisplayName("A data do id usa o offset original da liquidacao (nao converte para UTC)")
    void dataDoIdMantemOOffsetOriginal() {
        // 23:30 em -03:00 + 1 dia = 06/10; se convertesse para UTC (02:30 de 06/10) + 1 dia daria 07/10
        Chamadas c = processarUmEvento(
                evento("E1", "12345678000190", "2026-10-05T23:30:00-03:00", "2026-10-05T23:29:00-03:00"));

        assertTrue(c.passo1().dadosInfoSeg.idInfSegr().contains("PXE20261006"));
    }

    @Test
    @DisplayName("Liquidacao no ultimo dia do ano gera data do id no ano seguinte")
    void dataDoIdViraOAno() {
        Chamadas c = processarUmEvento(
                evento("E1", "12345678000190", "2026-12-31T12:00:00Z", "2026-12-31T11:59:00Z"));

        assertTrue(c.passo1().dadosInfoSeg.idInfSegr().contains("PXE20270101"));
    }

    // ------------------------------------------------------------------
    // infRequisicao
    // ------------------------------------------------------------------

    @Test
    @DisplayName("infRequisicao usa a raiz do CNPJ configurada e e o mesmo objeto nos 3 passos")
    void infRequisicaoCompartilhadaEntreOsPassos() {
        Chamadas c = processarUmEvento(eventoPadrao());

        InfRequisicao inf = c.passo1().infRequisicao;
        assertEquals(RAIZ, inf.cnpjRaizPspRecDir());
        assertSame(inf, c.passo2().infRequisicao);
        assertSame(inf, c.passo3().infRequisicao);
    }

    @Test
    @DisplayName("dtHrMsg e a hora atual em -03:00, formato yyyy-MM-dd'T'HH:mm:ssXXX")
    void dtHrMsgEhAgoraEmMenos3() {
        OffsetDateTime antes = OffsetDateTime.now(ZoneOffset.ofHours(-3)).truncatedTo(ChronoUnit.SECONDS);
        Chamadas c = processarUmEvento(eventoPadrao());
        OffsetDateTime depois = OffsetDateTime.now(ZoneOffset.ofHours(-3));

        String dtHrMsg = c.passo1().infRequisicao.dtHrMsg();
        assertTrue(dtHrMsg.matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}-03:00$"), "formato inesperado: " + dtHrMsg);

        OffsetDateTime parsed = OffsetDateTime.parse(dtHrMsg);
        assertFalse(parsed.isBefore(antes), "dtHrMsg anterior ao inicio do teste");
        assertFalse(parsed.isAfter(depois), "dtHrMsg posterior ao fim do teste");
    }

    // ------------------------------------------------------------------
    // Mapeamento evento -> Transacao
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Mapeia todos os campos do evento para a Transacao do lote")
    void mapeiaEventoParaTransacao() {
        Chamadas c = processarUmEvento(eventoPadrao());

        List<Transacao> transacoes = c.passo2().transacoes;
        assertEquals(1, transacoes.size());

        Transacao t = transacoes.get(0);
        assertEquals(1, t.getIndex());
        assertEquals("E18236120261005101530000000000012345", t.getE2eId());
        assertEquals(new BigDecimal("1500.75"), t.getVlPago());
        assertEquals(new BigDecimal("15.01"), t.getVlCbsSegr());
        assertEquals(new BigDecimal("7.50"), t.getVlIbsSegr());
        assertEquals("NF-20261005-987654", t.getDocFiscal());
        assertEquals("12345678", t.getCnpjRaizPspRecInd());
        assertEquals("00360305", t.getCnpjRaizPspPag());
        assertEquals("12345678000190", t.getCnpjRec());
        assertEquals("12345678901", t.getCnpjCpfPagEfet());
        assertEquals(OffsetDateTime.parse("2026-10-05T10:15:30Z"), t.getDtHrPgto());
        assertEquals(OffsetDateTime.parse("2026-10-05T10:15:32Z"), t.getDtHrLiq());
        assertEquals(OffsetDateTime.parse("2026-10-06T10:15:32Z"), t.getDtHrRepasse());
    }

    @Test
    @DisplayName("cnpjRaizPspRecInd = 8 primeiros digitos do cnpjRecebedor")
    void raizDoCnpjRecebedorTemOitoDigitos() {
        assertEquals("12345678", raizRecebedorPara("12345678000190"));
        assertEquals("12345678", raizRecebedorPara("12345678"));   // limite: exatamente 8
    }

    @Test
    @DisplayName("cnpjRaizPspRecInd fica nulo quando cnpjRecebedor e nulo ou tem menos de 8 caracteres")
    void raizDoCnpjRecebedorNulaQuandoInvalido() {
        assertNull(raizRecebedorPara(null));
        assertNull(raizRecebedorPara("1234567"));
        assertNull(raizRecebedorPara(""));
    }

    @Test
    @DisplayName("Sem dataHoraPagamento, dtHrPgto fica nulo e o fluxo continua")
    void semDataHoraPagamentoSegueOFluxo() {
        Chamadas c = processarUmEvento(evento("E1", "12345678000190", "2026-10-05T10:15:32Z", null));

        Transacao t = c.passo2().transacoes.get(0);
        assertNull(t.getDtHrPgto());
        assertNotNull(t.getDtHrLiq());
        verify(segregacaoClient).finalizarSegregacao(any());
    }

    // ------------------------------------------------------------------
    // Passo 3 (finalizacao)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Finalizacao informa 1 transacao e repete os valores de CBS e IBS do evento")
    void finalizacaoTrazTotais() {
        Chamadas c = processarUmEvento(eventoPadrao());

        assertEquals(1, c.passo3().dadosFinalSeg.totalTrans());
        assertEquals(new BigDecimal("15.01"), c.passo3().dadosFinalSeg.vlTotalCbs());
        assertEquals(new BigDecimal("7.50"), c.passo3().dadosFinalSeg.vlTotalIbs());
    }

    // ------------------------------------------------------------------
    // Entradas invalidas (comportamento atual)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("dataHoraLiquidacao nula: falha com NPE antes de qualquer chamada a Plataforma")
    void dataHoraLiquidacaoNulaFalhaAntesDeChamarAPlataforma() {
        // O id e montado com OffsetDateTime.parse(dataHoraLiquidacao) incondicionalmente,
        // entao o 'if (dataHoraLiquidacao != null)' mais abaixo nunca chega a ser util.
        TransacaoPagamentoEvent semLiquidacao = evento("E1", "12345678000190", null, "2026-10-05T10:15:30Z");

        assertThrows(NullPointerException.class, () -> processor.consumir(List.of(semLiquidacao)));
        verifyNoInteractions(segregacaoClient, pixClient);
    }

    @Test
    @DisplayName("dataHoraLiquidacao em formato invalido: DateTimeParseException antes de chamar a Plataforma")
    void dataHoraLiquidacaoInvalidaFalhaAntesDeChamarAPlataforma() {
        TransacaoPagamentoEvent invalido = evento("E1", "12345678000190", "05/10/2026 10:15", "2026-10-05T10:15:30Z");

        assertThrows(DateTimeParseException.class, () -> processor.consumir(List.of(invalido)));
        verifyNoInteractions(segregacaoClient, pixClient);
    }

    // ------------------------------------------------------------------
    // Falhas na Plataforma Publica
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Falha no passo 1 propaga a excecao e nao executa os passos 2 e 3")
    void falhaNoPasso1InterrompeOFluxo() {
        doThrow(new WebApplicationException("indisponivel", 503))
                .when(segregacaoClient).inicializarSegregacao(any());

        assertThrows(WebApplicationException.class, () -> processor.consumir(List.of(eventoPadrao())));

        verify(pixClient, never()).enviarLoteInformeSegregacao(anyString(), any());
        verify(segregacaoClient, never()).finalizarSegregacao(any());
    }

    @Test
    @DisplayName("Falha no passo 2 propaga a excecao e nao finaliza a segregacao")
    void falhaNoPasso2NaoFinaliza() {
        doThrow(new WebApplicationException("erro de validacao", 422))
                .when(pixClient).enviarLoteInformeSegregacao(anyString(), any());

        assertThrows(WebApplicationException.class, () -> processor.consumir(List.of(eventoPadrao())));

        verify(segregacaoClient).inicializarSegregacao(any());
        verify(segregacaoClient, never()).finalizarSegregacao(any());
    }

    @Test
    @DisplayName("Falha no passo 3 propaga a excecao")
    void falhaNoPasso3Propaga() {
        doThrow(new WebApplicationException("timeout", 504))
                .when(segregacaoClient).finalizarSegregacao(any());

        assertThrows(WebApplicationException.class, () -> processor.consumir(List.of(eventoPadrao())));
    }

    @Test
    @DisplayName("Falha no 2o evento interrompe o lote: o 3o evento nao e processado")
    void falhaNoSegundoEventoInterrompeOLote() {
        doReturn(resposta(201))
                .doThrow(new WebApplicationException("erro", 500))
                .when(pixClient).enviarLoteInformeSegregacao(anyString(), any());

        List<TransacaoPagamentoEvent> lote = List.of(
                evento("E1", "12345678000190", "2026-10-05T10:15:32Z", "2026-10-05T10:15:30Z"),
                evento("E2", "12345678000190", "2026-10-05T10:15:32Z", "2026-10-05T10:15:30Z"),
                evento("E3", "12345678000190", "2026-10-05T10:15:32Z", "2026-10-05T10:15:30Z"));

        assertThrows(WebApplicationException.class, () -> processor.consumir(lote));

        verify(segregacaoClient, times(2)).inicializarSegregacao(any());   // E1 e E2; E3 nunca iniciou
        verify(segregacaoClient, times(1)).finalizarSegregacao(any());     // so E1 foi finalizado
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private record Chamadas(InformeSegregacaoRequest passo1,
                            String idInfSegrNoPath,
                            InformeDeSegregacaoPixEstaticoRequest passo2,
                            InfoSegFinalRequest passo3) {
    }

    /** Processa um unico evento e devolve os argumentos recebidos pelos 3 endpoints. */
    private Chamadas processarUmEvento(TransacaoPagamentoEvent evento) {
        processor.consumir(List.of(evento));

        ArgumentCaptor<InformeSegregacaoRequest> c1 = ArgumentCaptor.forClass(InformeSegregacaoRequest.class);
        ArgumentCaptor<String> cId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<InformeDeSegregacaoPixEstaticoRequest> c2 =
                ArgumentCaptor.forClass(InformeDeSegregacaoPixEstaticoRequest.class);
        ArgumentCaptor<InfoSegFinalRequest> c3 = ArgumentCaptor.forClass(InfoSegFinalRequest.class);

        verify(segregacaoClient).inicializarSegregacao(c1.capture());
        verify(pixClient).enviarLoteInformeSegregacao(cId.capture(), c2.capture());
        verify(segregacaoClient).finalizarSegregacao(c3.capture());

        return new Chamadas(c1.getValue(), cId.getValue(), c2.getValue(), c3.getValue());
    }

    private String raizRecebedorPara(String cnpjRecebedor) {
        Chamadas c = processarUmEvento(
                evento("E1", cnpjRecebedor, "2026-10-05T10:15:32Z", "2026-10-05T10:15:30Z"));
        String raiz = c.passo2().transacoes.get(0).getCnpjRaizPspRecInd();
        // limpa as interacoes para o helper poder ser chamado de novo no mesmo teste
        clearInvocations(segregacaoClient, pixClient);
        return raiz;
    }

    private static PPResponse resposta(int status) {
        return new PPResponse("ok", status, null, null, null, null, null);
    }

    private static TransacaoPagamentoEvent eventoPadrao() {
        return evento("E18236120261005101530000000000012345", "12345678000190",
                "2026-10-05T10:15:32Z", "2026-10-05T10:15:30Z");
    }

    private static TransacaoPagamentoEvent evento(String e2eId, String cnpjRecebedor,
                                                  String dataHoraLiquidacao, String dataHoraPagamento) {
        return new TransacaoPagamentoEvent(
                1,                                  // tipoEvento
                "PXE",                              // arranjo
                e2eId,                              // identificadorTransacao
                new BigDecimal("1500.75"),          // valorTransacao
                new BigDecimal("15.01"),            // valorCbs
                new BigDecimal("7.50"),             // valorIbs
                "NF-20261005-987654",               // documentoFiscal
                1234, 1, "1234567890123",           // conta debitada
                5678, 1, "9876543210987",           // conta creditada
                104,                                // compe
                360305,                             // ispb
                cnpjRecebedor,
                "LOJA EXEMPLO LTDA",                // nomeRecebedor
                "12345678901",                      // cpfCnpjPagador
                "CLIENTE TESTE",                    // nomePagador
                dataHoraLiquidacao,
                "00360305",                         // cnpjRaizBancoPagador
                dataHoraPagamento,
                "001202610051234567890");           // nsuOrigem
    }

    private static void setField(Object alvo, String nome, Object valor) throws Exception {
        Field f = alvo.getClass().getDeclaredField(nome);
        f.setAccessible(true);
        f.set(alvo, valor);
    }
}