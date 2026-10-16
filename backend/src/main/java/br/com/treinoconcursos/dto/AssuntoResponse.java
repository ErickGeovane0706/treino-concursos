package br.com.treinoconcursos.dto;

/** @param nivel 1 = assunto raiz da materia; no maximo 3 (RF02 RN2) */
public record AssuntoResponse(Integer id, Integer idMateria, Integer idAssuntoPai, String nome, String slug,
        boolean ativo, int nivel) {
}
