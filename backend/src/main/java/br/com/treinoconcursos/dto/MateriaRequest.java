package br.com.treinoconcursos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param slug opcional: vazio gera do nome na criacao e mantem o atual na alteracao
 */
public record MateriaRequest(
        @NotBlank @Size(max = 100) String nome,
        @Size(max = 120) String slug,
        @NotNull Boolean ativa) {
}
