package br.com.treinoconcursos.dto;

/**
 * Dados minimos de uma questao publicada para o caderno de erros no navegador (RF11 RN1-RN2, US04).
 * Nunca expoe o gabarito (doc/api.md secao 4).
 */
public record QuestaoCadernoResponse(
        Integer id,
        String slug,
        String slugMateria,
        String nomeMateria,
        String nomeAssunto,
        String fonte,
        String resumoEnunciado,
        boolean anulada,
        boolean desatualizada) {
}
