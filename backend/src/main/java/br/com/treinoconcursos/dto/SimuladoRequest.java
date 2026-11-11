package br.com.treinoconcursos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Dados do simulado no painel (RF14 RN1). {@code idsQuestoes} vem na ordem do simulado.
 * O slug e opcional: vazio na criacao e gerado do titulo; vazio na alteracao mantem o atual.
 */
public record SimuladoRequest(
        @NotBlank @Size(max = 150) String titulo,
        @Size(max = 150) String slug,
        @Size(max = 2000) String descricao,
        @Positive Integer tempoSugeridoMin,
        @NotNull List<@NotNull Integer> idsQuestoes) {
}
