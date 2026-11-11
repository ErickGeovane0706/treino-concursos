package br.com.treinoconcursos.dto;

import br.com.treinoconcursos.model.StatusSimulado;

import java.time.LocalDateTime;

/** Linha da lista de simulados do painel. */
public record SimuladoResumoResponse(Integer id, String titulo, String slug, StatusSimulado status,
        boolean precisaRevisao, long totalQuestoes, LocalDateTime publicadoEm) {
}
