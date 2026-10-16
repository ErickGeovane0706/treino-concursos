package br.com.treinoconcursos.dto;

import br.com.treinoconcursos.model.Materia;

public record MateriaResponse(Integer id, String nome, String slug, boolean ativa) {

    public static MateriaResponse de(Materia materia) {
        return new MateriaResponse(materia.getId(), materia.getNome(), materia.getSlug(), materia.isAtiva());
    }
}
