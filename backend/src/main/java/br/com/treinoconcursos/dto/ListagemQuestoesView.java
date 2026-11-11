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
        boolean indexavel,
        String slugMateriaAtiva,
        String nomeMateriaAtiva,
        String slugAssuntoAtivo,
        String nomeAssuntoAtivo,
        String slugBancaAtiva,
        String nomeBancaAtiva,
        String slugOrgaoAtivo,
        String nomeOrgaoAtivo,
        Integer anoAtivo,
        String nivelAtivo,
        String nomeNivelAtivo,
        String tipoAtivo,
        String nomeTipoAtivo,
        String filtroAtivo,
        String caminhoBase,
        String urlLimparMateria,
        String urlLimparAssunto,
        String urlLimparBanca,
        String urlLimparOrgao,
        String urlLimparAno,
        String urlLimparNivel,
        List<ChipFiltroView> materias,
        List<ChipFiltroView> assuntos,
        List<ChipFiltroView> bancas,
        List<ChipFiltroView> orgaos,
        List<ChipFiltroView> anos,
        List<ChipFiltroView> niveis,
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
