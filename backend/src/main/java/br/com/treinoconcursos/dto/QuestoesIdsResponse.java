package br.com.treinoconcursos.dto;

import java.util.List;

/**
 * Resposta de {@code GET /api/questoes/ids} (RF07, RF10, doc/api.md secao 8).
 *
 * @param itens         ids publicados do filtro em ordem crescente
 * @param proximoCursor ultimo id retornado quando ha mais itens, ou {@code null} quando acabou
 */
public record QuestoesIdsResponse(List<Integer> itens, Integer proximoCursor) {
}
