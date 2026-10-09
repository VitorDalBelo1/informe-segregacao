package dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Resposta de sucesso da Plataforma Publica (PPResponseOk). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PPResponse(
        String title,
        Integer status,
        String detail,
        Integer numValidos,
        Integer numErros,
        List<Erro> errors,
        String resourceId) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Erro(Integer index, String field, String message) {
    }
}
