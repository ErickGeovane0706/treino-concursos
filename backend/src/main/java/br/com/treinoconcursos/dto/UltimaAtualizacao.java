package br.com.treinoconcursos.dto;

import java.time.LocalDateTime;

/** Ultima alteracao das questoes publicadas de uma materia ou assunto (lastmod do sitemap, RF09 RN1). */
public record UltimaAtualizacao(Integer id, LocalDateTime em) {
}
