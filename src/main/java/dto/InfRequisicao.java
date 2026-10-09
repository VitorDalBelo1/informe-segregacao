package dto;

import java.time.OffsetDateTime;

public record InfRequisicao(
        String cnpjRaizPspRecDir,
        OffsetDateTime dtHrMsg) {
}