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
import br.com.treinoconcursos.repository.AssuntoRepository;
import br.com.treinoconcursos.repository.MateriaRepository;
import br.com.treinoconcursos.repository.QuestaoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Monta os dados da pagina publica da questao (RF06, RF08, US01).
 */
@Service
public class QuestaoPublicaService {

    /** RF08 RN2: o <title> inteiro (com " | Treino de Concursos" do layout) tem ate 60 caracteres. */
    static final int LIMITE_TITULO_TOTAL = 60;
    static final int LIMITE_TITULO_PAGINA = LIMITE_TITULO_TOTAL - (" | " + SiteConfig.NOME).length();
    static final int LIMITE_META_DESCRICAO = 155;

    private static final Pattern FILTRO_VALIDO =
            Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*(/[a-z0-9]+(-[a-z0-9]+)*)?$");

    private final QuestaoRepository questaoRepository;
    private final MateriaRepository materiaRepository;
    private final AssuntoRepository assuntoRepository;
    private final MarkdownService markdownService;

    public QuestaoPublicaService(QuestaoRepository questaoRepository, MateriaRepository materiaRepository,
            AssuntoRepository assuntoRepository, MarkdownService markdownService) {
        this.questaoRepository = questaoRepository;
        this.materiaRepository = materiaRepository;
        this.assuntoRepository = assuntoRepository;
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
        Vizinhanca vizinhanca = buscarVizinhanca(questao.getId(), filtro);

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
        String listagemUrl = assunto != null
                ? "/questoes/" + materia.getSlug() + "/" + assunto.getSlug()
                : "/questoes/" + materia.getSlug();
        String rotuloListagem = assunto != null
                ? "Questões de " + assunto.getNome()
                : "Questões de " + materia.getNome();

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
        if (de == null) {
            return null;
        }
        String limpo = de.trim().replaceAll("^/+|/+$", "");
        return FILTRO_VALIDO.matcher(limpo).matches() ? limpo : null;
    }

    public static String urlCanonica(Integer id, String slug, String filtro) {
        String base = "/questao/" + id + "/" + slug;
        return filtro == null ? base : base + "?de=" + filtro;
    }

    private Vizinhanca buscarVizinhanca(Integer idQuestao, String filtro) {
        if (filtro != null) {
            String[] partes = filtro.split("/", 2);
            Materia materia = materiaRepository.findBySlug(partes[0]).orElse(null);
            if (materia != null) {
                if (partes.length == 2) {
                    List<Integer> idsAssunto = idsDoAssuntoESubassuntos(materia.getId(), partes[1]);
                    if (!idsAssunto.isEmpty()) {
                        return new Vizinhanca(
                                questaoRepository.anteriorPublicadaPorAssuntos(idsAssunto, idQuestao).orElse(null),
                                questaoRepository.proximaPublicadaPorAssuntos(idsAssunto, idQuestao).orElse(null));
                    }
                }
                return new Vizinhanca(
                        questaoRepository.anteriorPublicadaPorMateria(materia.getId(), idQuestao).orElse(null),
                        questaoRepository.proximaPublicadaPorMateria(materia.getId(), idQuestao).orElse(null));
            }
        }
        return new Vizinhanca(
                questaoRepository.anteriorPublicada(idQuestao).orElse(null),
                questaoRepository.proximaPublicada(idQuestao).orElse(null));
    }

    private List<Integer> idsDoAssuntoESubassuntos(Integer idMateria, String slugAssunto) {
        List<Assunto> todos = assuntoRepository.findByMateriaIdOrderByNome(idMateria);
        Assunto raiz = todos.stream().filter(a -> a.getSlug().equals(slugAssunto)).findFirst().orElse(null);
        if (raiz == null) {
            return List.of();
        }
        Map<Integer, List<Integer>> filhosPorPai = new HashMap<>();
        for (Assunto a : todos) {
            if (a.getAssuntoPai() != null) {
                filhosPorPai.computeIfAbsent(a.getAssuntoPai().getId(), k -> new ArrayList<>()).add(a.getId());
            }
        }
        List<Integer> ids = new ArrayList<>();
        Deque<Integer> fila = new ArrayDeque<>();
        fila.add(raiz.getId());
        while (!fila.isEmpty()) {
            Integer atual = fila.removeFirst();
            ids.add(atual);
            fila.addAll(filhosPorPai.getOrDefault(atual, List.of()));
        }
        return ids;
    }

    private static String urlDeQuestao(Questao questao, String filtro) {
        return questao == null ? null : urlCanonica(questao.getId(), questao.getSlug(), filtro);
    }

    /** RF04 RN3 e RF08 RN3: banca, orgao, ano e cargo, ou "Questao autoral". */
    private static String fonteDe(Questao questao) {
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

    private record Vizinhanca(Questao anterior, Questao proxima) {
    }
}
