package br.com.treinoconcursos.dto;

import br.com.treinoconcursos.model.TipoQuestao;

import java.util.List;

/**
 * Dados ja prontos e sanitizados para as paginas publicas do simulado (RF13, US05).
 * Os templates nunca recebem entidades JPA. So entram as questoes elegiveis (RF14 RN4),
 * numeradas de 1 a {@code total} na ordem do simulado.
 */
public final class SimuladoPaginasView {

    private SimuladoPaginasView() {
    }

    /** Item de /simulados. */
    public record Cartao(String titulo, String slug, String descricao, int totalQuestoes, Integer tempoSugeridoMin,
            String url) {
    }

    /** /simulado/{slug}: apresentacao. {@code idsQuestoes} permite a ilha achar onde o visitante parou. */
    public record Apresentacao(String titulo, String slug, String descricao, String metaDescricao,
            Integer tempoSugeridoMin, int total, List<MateriaContagem> materias, List<Integer> idsQuestoes,
            String primeiraUrl, String resultadoUrl) {
    }

    public record MateriaContagem(String nome, int quantidade) {
    }

    /** /simulado/{slug}/{n}: questao n, sem gabarito nem comentario (RF13 RN2). */
    public record Questao(String tituloSimulado, String slugSimulado, String apresentacaoUrl, int n, int total,
            Integer tempoSugeridoMin, Integer idQuestao, String nomeMateria, String fonte, TipoQuestao tipo,
            String enunciadoHtml, List<Alternativa> alternativas, String anteriorUrl, String proximaUrl,
            String resultadoUrl, String primeiraUrl) {

        /** Largura da barra de andamento, em %. */
        public int percentualAndamento() {
            return total == 0 ? 0 : Math.round((n - 1) * 100f / total);
        }
    }

    public record Alternativa(String letra, String textoHtml) {
    }

    /** /simulado/{slug}/resultado: gabarito gratuito de cada questao (RF13 RN3). */
    public record Resultado(String titulo, String slug, String apresentacaoUrl, String primeiraUrl,
            String comentariosUrl, Integer tempoSugeridoMin, List<ItemGabarito> itens) {
    }

    /** /simulado/{slug}/comentarios: comentario de cada questao (RF15 RN1). */
    public record Comentarios(String titulo, String slug, String apresentacaoUrl, String resultadoUrl,
            List<ItemComentario> itens) {
    }

    public record ItemComentario(int n, Integer idQuestao, String nomeMateria, String enunciadoHtml,
            List<AlternativaCorrigida> alternativas, String rotuloCorreta, String comentarioHtml) {
    }

    public record AlternativaCorrigida(String letra, String textoHtml, boolean correta) {
    }

    public record ItemGabarito(int n, Integer idQuestao, String nomeMateria, String trecho, String letraCorreta,
            String rotuloCorreta, String questaoUrl) {
    }
}
