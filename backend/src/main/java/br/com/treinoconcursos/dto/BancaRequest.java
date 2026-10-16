package br.com.treinoconcursos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param slug opcional: vazio gera da sigla na criacao e mantem o atual na alteracao */
public record BancaRequest(
        @NotBlank @Size(max = 100) String nome,
        @NotBlank @Size(max = 30) String sigla,
        @Size(max = 120) String slug) {
}
