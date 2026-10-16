package br.com.treinoconcursos.dto;

import br.com.treinoconcursos.model.EsferaOrgao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param uf   obrigatoria fora da esfera FEDERAL (RF03 RN1); ignorada na FEDERAL
 * @param slug opcional: vazio gera da sigla na criacao e mantem o atual na alteracao
 */
public record OrgaoRequest(
        @NotBlank @Size(max = 150) String nome,
        @NotBlank @Size(max = 30) String sigla,
        @Size(max = 120) String slug,
        @NotNull EsferaOrgao esfera,
        @Pattern(regexp = "[A-Za-z]{2}") String uf) {
}
