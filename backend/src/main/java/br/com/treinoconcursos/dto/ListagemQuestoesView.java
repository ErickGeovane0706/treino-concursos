package br.com.treinoconcursos.dto;

import java.util.List;

/**
 * Dados ja prontos e sanitizados para as paginas de listagem por cursor (RF07, US02).
 * O template nunca recebe entidades JPA (RNF05).
 */
public record ListagemQuestoesView(
        String tituloPagina,
        String tituloH1,
        String metaDescricao,
        String slugMateriaAtiva,
        String nomeMateriaAtiva,
        String slugAssuntoAtivo,
        String nomeAssuntoAtivo,
        String filtroAtivo,
        String caminhoBase,
        List<ChipFiltroView> materias,
        List<ChipFiltroView> assuntos,
        List<ItemQuestaoView> questoes,
        Integer proximoCursor,
        String proximaPaginaUrl) {

    public record ChipFiltroView(String nome, String slug, String url, boolean ativo) {
    }

    public record ItemQuestaoView(
            Integer id,
            String slug,
            String url,
            String nomeMateria,
            String nomeAssunto,
            String fonte,
            String resumoEnunciado,
            boolean anulada,
            boolean desatualizada) {
    }
}
