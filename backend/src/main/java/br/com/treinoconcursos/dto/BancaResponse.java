package br.com.treinoconcursos.dto;

import br.com.treinoconcursos.model.Banca;

public record BancaResponse(Integer id, String nome, String sigla, String slug) {

    public static BancaResponse de(Banca banca) {
        return new BancaResponse(banca.getId(), banca.getNome(), banca.getSigla(), banca.getSlug());
    }
}
