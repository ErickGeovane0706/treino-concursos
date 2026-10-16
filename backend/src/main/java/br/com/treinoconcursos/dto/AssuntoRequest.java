package br.com.treinoconcursos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param idAssuntoPai opcional; deve ser da mesma materia (max. 3 niveis)
 * @param slug         opcional: vazio gera do nome na criacao e mantem o atual na alteracao
 */
public record AssuntoRequest(
        @NotBlank @Size(max = 150) String nome,
        @Size(max = 170) String slug,
        Integer idAssuntoPai,
        @NotNull Boolean ativo) {
}
