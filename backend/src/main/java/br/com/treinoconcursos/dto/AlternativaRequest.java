package br.com.treinoconcursos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Alternativa enviada pelo painel. No Certo/Errado o texto e ignorado: o
 * sistema grava "Certo" e "Errado".
 */
public record AlternativaRequest(
        @NotBlank @Pattern(regexp = "[A-E]") String letra,
        @Size(max = 10_000) String texto,
        boolean correta) {
}
