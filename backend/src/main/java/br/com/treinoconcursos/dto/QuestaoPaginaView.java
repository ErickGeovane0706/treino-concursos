package br.com.treinoconcursos.dto;

import br.com.treinoconcursos.model.TipoQuestao;

import java.util.List;

/**
 * Dados ja prontos e sanitizados para a pagina publica da questao (RF06, RF08).
 * O template nunca recebe a entidade JPA (RNF05).
 */
public record QuestaoPaginaView(
        Integer id,
        String slug,
        String tituloPagina,
        String metaDescricao,
        String nomeMateria,
        String slugMateria,
        String nomeAssunto,
        String slugAssunto,
        String fonte,
        TipoQuestao tipo,
        boolean anulada,
        boolean desatualizada,
        String enunciadoHtml,
        String comentarioHtml,
        String letraCorreta,
        String rotuloCorreta,
        List<AlternativaView> alternativas,
        String anteriorUrl,
        String proximaUrl,
        String listagemUrl,
        String rotuloListagem,
        String filtroAtivo) {

    public record AlternativaView(String letra, String textoHtml, boolean correta) {
    }
}
