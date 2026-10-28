package br.com.treinoconcursos.service;

import br.com.treinoconcursos.config.SiteConfig;
import br.com.treinoconcursos.dto.QuestaoPaginaView;
import br.com.treinoconcursos.dto.QuestaoPaginaView.AlternativaView;
import br.com.treinoconcursos.model.Alternativa;
import br.com.treinoconcursos.model.Assunto;
import br.com.treinoconcursos.model.Materia;
import br.com.treinoconcursos.model.OrigemQuestao;
import br.com.treinoconcursos.model.Prova;
import br.com.treinoconcursos.model.Questao;
import br.com.treinoconcursos.model.StatusQuestao;
import br.com.treinoconcursos.model.TipoQuestao;
import br.com.treinoconcursos.repository.QuestaoRepository;
import br.com.treinoconcursos.service.NavegacaoQuestoesService.Vizinhanca;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Monta os dados da pagina publica da questao (RF06, RF08, US01).
 */
@Service
public class QuestaoPublicaService {

    /** RF08 RN2: o <title> inteiro (com " | Treino de Concursos" do layout) tem ate 60 caracteres. */
    static final int LIMITE_TITULO_TOTAL = 60;
    static final int LIMITE_TITULO_PAGINA = LIMITE_TITULO_TOTAL - (" | " + SiteConfig.NOME).length();
    static final int LIMITE_META_DESCRICAO = 155;

    private final QuestaoRepository questaoRepository;
    private final NavegacaoQuestoesService navegacaoService;
    private final MarkdownService markdownService;

    public QuestaoPublicaService(QuestaoRepository questaoRepository, NavegacaoQuestoesService navegacaoService,
            MarkdownService markdownService) {
        this.questaoRepository = questaoRepository;
        this.navegacaoService = navegacaoService;
        this.markdownService = markdownService;
    }

    /**
     * @throws ResponseStatusException 404 se inexistente ou RASCUNHO; 410 se ARQUIVADA (RF05 RN3)
     */
    @Transactional(readOnly = true)
    public QuestaoPaginaView buscarParaPagina(Integer id, String de) {
        Questao questao = questaoRepository.findComDetalhesById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (questao.getStatus() == StatusQuestao.RASCUNHO) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (questao.getStatus() == StatusQuestao.ARQUIVADA) {
            throw new ResponseStatusException(HttpStatus.GONE);
        }

        String filtro = normalizarFiltro(de);
        Vizinhanca vizinhanca = navegacaoService.buscarVizinhanca(questao.getId(), filtro);

        String enunciadoHtml = markdownService.paraHtml(questao.getEnunciado());
        String comentarioHtml = markdownService.paraHtml(questao.getComentario());
        String textoEnunciado = textoPlano(enunciadoHtml);
        String tituloPagina = cortar(textoEnunciado, LIMITE_TITULO_PAGINA);
        String metaDescricao = cortar(questao.getMateria().getNome() + ": " + textoEnunciado, LIMITE_META_DESCRICAO);

        List<AlternativaView> alternativas = questao.getAlternativas().stream()
                .map(a -> new AlternativaView(a.getLetra(), markdownService.paraHtml(a.getTexto()), a.isCorreta()))
                .toList();
        String letraCorreta = questao.getAlternativas().stream()
                .filter(Alternativa::isCorreta)
                .map(Alternativa::getLetra)
                .findFirst()
                .orElse("");
        String rotuloCorreta = rotuloCorreta(questao.getTipo(), letraCorreta);

        Materia materia = questao.getMateria();
        Assunto assunto = questao.getAssunto();
        boolean origemCaderno = "caderno".equals(filtro) || (filtro != null && filtro.startsWith("caderno/"));
        String listagemUrl;
        String rotuloListagem;
        if (origemCaderno) {
            listagemUrl = "/caderno-de-erros";
            rotuloListagem = "Caderno de erros";
        } else {
            listagemUrl = assunto != null
                    ? "/questoes/" + materia.getSlug() + "/" + assunto.getSlug()
                    : "/questoes/" + materia.getSlug();
            rotuloListagem = assunto != null
                    ? "Questões de " + assunto.getNome()
                    : "Questões de " + materia.getNome();
        }

        return new QuestaoPaginaView(
                questao.getId(),
                questao.getSlug(),
                tituloPagina,
                metaDescricao,
                materia.getNome(),
                materia.getSlug(),
                assunto != null ? assunto.getNome() : null,
                assunto != null ? assunto.getSlug() : null,
                fonteDe(questao),
                questao.getTipo(),
                questao.isAnulada(),
                questao.isDesatualizada(),
                enunciadoHtml,
                comentarioHtml,
                letraCorreta,
                rotuloCorreta,
                alternativas,
                urlDeQuestao(vizinhanca.anterior(), filtro),
                urlDeQuestao(vizinhanca.proxima(), filtro),
                listagemUrl,
                rotuloListagem,
                filtro);
    }

    public static String normalizarFiltro(String de) {
        return NavegacaoQuestoesService.normalizarFiltro(de);
    }

    public static String urlCanonica(Integer id, String slug, String filtro) {
        String base = "/questao/" + id + "/" + slug;
        return filtro == null ? base : base + "?de=" + filtro;
    }

    private static String urlDeQuestao(Questao questao, String filtro) {
        return questao == null ? null : urlCanonica(questao.getId(), questao.getSlug(), filtro);
    }

    /** RF04 RN3 e RF08 RN3: banca, orgao, ano e cargo, ou "Questao autoral". */
    static String fonteDe(Questao questao) {
        if (questao.getOrigem() == OrigemQuestao.PROVA && questao.getProva() != null) {
            Prova p = questao.getProva();
            return p.getBanca().getSigla() + " · " + p.getOrgao().getSigla() + " · " + p.getAno() + " · " + p.getCargo();
        }
        return "Questão autoral";
    }

    private static String rotuloCorreta(TipoQuestao tipo, String letra) {
        if (tipo == TipoQuestao.CERTO_ERRADO) {
            return "C".equals(letra) ? "C (Certo)" : "E (Errado)";
        }
        return letra;
    }

    static String textoPlano(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return html.replaceAll("<[^>]+>", " ")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();
    }

    static String cortar(String texto, int limite) {
        if (texto == null || texto.length() <= limite) {
            return texto == null ? "" : texto;
        }
        String recorte = texto.substring(0, limite - 1).stripTrailing();
        int espaco = recorte.lastIndexOf(' ');
        if (espaco >= limite / 2) {
            recorte = recorte.substring(0, espaco).stripTrailing();
        }
        return recorte + "…";
    }
}
