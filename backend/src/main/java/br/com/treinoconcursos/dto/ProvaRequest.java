package br.com.treinoconcursos.dto;

import br.com.treinoconcursos.model.NivelEscolaridade;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** O slug da prova e sempre gerado: banca-orgao-ano-cargo. */
public record ProvaRequest(
        @NotNull Integer idBanca,
        @NotNull Integer idOrgao,
        @NotNull @Min(1990) @Max(2100) Integer ano,
        @NotBlank @Size(max = 150) String cargo,
        @NotNull NivelEscolaridade nivel) {
}
