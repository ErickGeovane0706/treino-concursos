package br.com.treinoconcursos.dto;

import br.com.treinoconcursos.model.Alternativa;

public record AlternativaResponse(Integer id, String letra, String texto, boolean correta, int totalMarcacoes) {

    public static AlternativaResponse de(Alternativa alternativa) {
        return new AlternativaResponse(alternativa.getId(), alternativa.getLetra(), alternativa.getTexto(),
                alternativa.isCorreta(), alternativa.getTotalMarcacoes());
    }
}
