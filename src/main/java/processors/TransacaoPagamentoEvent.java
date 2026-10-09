package processors;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Evento do contrato de transação de pagamento.
 *
 * Campos desconhecidos no JSON são ignorados para garantir
 * resiliência contra mudanças de schema no tópico.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TransacaoPagamentoEvent(
        Integer tipoEvento,
        String arranjo,
        String identificadorTransacao,
        BigDecimal valorTransacao,
        BigDecimal valorCbs,
        BigDecimal valorIbs,
        String documentoFiscal,
        Integer agenciaContaDebitada,
        Integer produtoContaDebitada,
        String numeroContaDebitada,
        Integer agenciaContaCreditada,
        Integer produtoContaCreditada,
        String numeroContaCreditada,
        Integer compe,
        Integer ispb,
        String cnpjRecebedor,
        String nomeRecebedor,
        String cpfCnpjPagador,
        String nomePagador,
        String dataHoraLiquidacao,
        String cnpjRaizBancoPagador,
        String dataHoraPagamento,
        String nsuOrigem) {
}
