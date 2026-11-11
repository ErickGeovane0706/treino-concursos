package br.com.treinoconcursos.dto;

import java.time.LocalDateTime;

/**
 * Total e ultima alteracao das questoes publicadas de uma classificacao/filtro
 * (usado no sitemap para filtrar paginas indexaveis e informar lastmod, RF07 RN4 e RF09 RN1).
 */
public record UltimaAtualizacao(Integer id, Long total, LocalDateTime em) {
}
