package br.com.treinoconcursos.dto;

import java.util.List;

/**
 * Estatisticas anonimas de respostas de uma questao (RF17 RN1, RN3, RN4).
 * Os percentuais so sao informados quando a questao atinge 30 ou mais respostas (RF17 RN3).
 * Nunca expoe qual alternativa e a correta (doc/api.md secao 4).
 */
public record EstatisticasQuestaoResponse(
        boolean disponivel,
        int totalRespostas,
        Integer percentualAcertos,
        List<EstatisticaAlternativaResponse> alternativas) {

    public record EstatisticaAlternativaResponse(String letra, int percentual) {
    }
}
