package br.com.treinoconcursos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Resposta anonima enviada pela ilha de treino (RF17 RN1, RN4, RNF08).
 * Contem apenas a letra marcada (A a E), sem nenhum identificador do visitante.
 */
public record RespostaQuestaoRequest(
        @NotBlank
        @Pattern(regexp = "^[A-E]$")
        String letra) {
}
