package br.com.treinoconcursos.dto;

import br.com.treinoconcursos.model.OrigemQuestao;
import br.com.treinoconcursos.model.TipoQuestao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Dados da questao (RF04). O status nao vem aqui: muda so pelas acoes
 * publicar e arquivar (RF05). O comentario pode ficar vazio no rascunho.
 */
public record QuestaoRequest(
        @NotNull Integer idMateria,
        Integer idAssunto,
        @NotNull TipoQuestao tipo,
        @NotNull OrigemQuestao origem,
        Integer idProva,
        @NotBlank @Size(max = 20_000) String enunciado,
        @Size(max = 50_000) String comentario,
        @NotNull @Size(max = 5) List<@Valid @NotNull AlternativaRequest> alternativas) {
}
