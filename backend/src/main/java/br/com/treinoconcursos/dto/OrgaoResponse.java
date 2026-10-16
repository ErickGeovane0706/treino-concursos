package br.com.treinoconcursos.dto;

import br.com.treinoconcursos.model.EsferaOrgao;
import br.com.treinoconcursos.model.Orgao;

public record OrgaoResponse(Integer id, String nome, String sigla, String slug, EsferaOrgao esfera, String uf) {

    public static OrgaoResponse de(Orgao orgao) {
        return new OrgaoResponse(orgao.getId(), orgao.getNome(), orgao.getSigla(), orgao.getSlug(), orgao.getEsfera(),
                orgao.getUf());
    }
}
