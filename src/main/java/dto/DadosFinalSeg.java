package dto;

import java.math.BigDecimal;

public record DadosFinalSeg (
        String idInfSegr,
        Integer totalTrans,
        BigDecimal vlTotalCbs,
        BigDecimal vlTotalIbs
) {
}
