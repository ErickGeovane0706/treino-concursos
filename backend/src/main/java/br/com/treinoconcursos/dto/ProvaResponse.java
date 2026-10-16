package br.com.treinoconcursos.dto;

import br.com.treinoconcursos.model.NivelEscolaridade;
import br.com.treinoconcursos.model.Prova;

public record ProvaResponse(Integer id, Integer idBanca, String siglaBanca, Integer idOrgao, String siglaOrgao,
        Integer ano, String cargo, NivelEscolaridade nivel, String slug) {

    public static ProvaResponse de(Prova prova) {
        return new ProvaResponse(prova.getId(), prova.getBanca().getId(), prova.getBanca().getSigla(),
                prova.getOrgao().getId(), prova.getOrgao().getSigla(), prova.getAno(), prova.getCargo(),
                prova.getNivel(), prova.getSlug());
    }
}
