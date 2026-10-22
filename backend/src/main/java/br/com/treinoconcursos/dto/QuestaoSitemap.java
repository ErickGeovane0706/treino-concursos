package br.com.treinoconcursos.dto;

import java.time.LocalDateTime;

/** Uma URL de questao no sitemap (RF09 RN1): so o necessario, sem carregar a entidade. */
public record QuestaoSitemap(Integer id, String slug, LocalDateTime atualizadoEm) {
}
