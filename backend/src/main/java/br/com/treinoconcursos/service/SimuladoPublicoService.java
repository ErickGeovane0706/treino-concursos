package br.com.treinoconcursos.service;

import br.com.treinoconcursos.dto.QuestaoDoSimuladoResponse;
import br.com.treinoconcursos.dto.SimuladoPaginasView;
import br.com.treinoconcursos.model.Questao;
import br.com.treinoconcursos.model.Simulado;
import br.com.treinoconcursos.model.SimuladoQuestao;
import br.com.treinoconcursos.model.StatusSimulado;
import br.com.treinoconcursos.repository.AlternativaRepository;
import br.com.treinoconcursos.repository.QuestaoRepository;
import br.com.treinoconcursos.repository.SimuladoQuestaoRepository;
import br.com.treinoconcursos.repository.SimuladoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Paginas publicas do simulado (RF13 RN1-RN5, US05). So simulados PUBLICADOS aparecem;
 * dentro deles, so as questoes elegiveis, renumeradas de 1 a N (RF14 RN4).
 */
@Service
public class SimuladoPublicoService {

    static final int LIMITE_META_DESCRICAO = 155;
    static final int LIMITE_TRECHO = 140;

    private final SimuladoRepository simuladoRepository;
    private final SimuladoQuestaoRepository simuladoQuestaoRepository;
    private final QuestaoRepository questaoRepository;
    private final AlternativaRepository alternativaRepository;
    private final MarkdownService markdownService;

    public SimuladoPublicoService(SimuladoRepository simuladoRepository,
            SimuladoQuestaoRepository simuladoQuestaoRepository, QuestaoRepository questaoRepository,
            AlternativaRepository alternativaRepository, MarkdownService markdownService) {
        this.simuladoRepository = simuladoRepository;
        this.simuladoQuestaoRepository = simuladoQuestaoRepository;
        this.questaoRepository = questaoRepository;
        this.alternativaRepository = alternativaRepository;
        this.markdownService = markdownService;
    }

    public static String urlApresentacao(String slug) {
        return "/simulado/" + slug;
    }

    public static String urlQuestao(String slug, int n) {
        return "/simulado/" + slug + "/" + n;
    }

    public static String urlResultado(String slug) {
        return "/simulado/" + slug + "/resultado";
    }

    /** /simulados: publicados com pelo menos uma questao elegivel. */
    @Transactional(readOnly = true)
    public List<SimuladoPaginasView.Cartao> listarPublicados() {
        List<Simulado> simulados = simuladoRepository.listarPublicados();
        if (simulados.isEmpty()) {
            return List.of();
        }
        Map<Integer, Integer> elegiveis = new HashMap<>();
        for (Object[] linha : simuladoQuestaoRepository.contarElegiveis(
                simulados.stream().map(Simulado::getId).toList())) {
            elegiveis.put((Integer) linha[0], ((Number) linha[1]).intValue());
        }
        return simulados.stream()
                .filter(s -> elegiveis.getOrDefault(s.getId(), 0) > 0)
                .map(s -> new SimuladoPaginasView.Cartao(s.getTitulo(), s.getSlug(), s.getDescricao(),
                        elegiveis.get(s.getId()), s.getTempoSugeridoMin(), urlApresentacao(s.getSlug())))
                .toList();
    }

    @Transactional(readOnly = true)
    public SimuladoPaginasView.Apresentacao apresentacao(String slug) {
        Simulado simulado = publicado(slug);
        List<Questao> questoes = elegiveis(simulado);
        Map<String, Integer> porMateria = new LinkedHashMap<>();
        questoes.forEach(q -> porMateria.merge(q.getMateria().getNome(), 1, Integer::sum));
        String descricao = simulado.getDescricao() != null ? simulado.getDescricao()
                : "Simulado com " + questoes.size() + " questões, correção no final e gabarito grátis.";
        return new SimuladoPaginasView.Apresentacao(
                simulado.getTitulo(),
                simulado.getSlug(),
                simulado.getDescricao(),
                QuestaoPublicaService.cortar(descricao, LIMITE_META_DESCRICAO),
                simulado.getTempoSugeridoMin(),
                questoes.size(),
                porMateria.entrySet().stream()
                        .map(e -> new SimuladoPaginasView.MateriaContagem(e.getKey(), e.getValue())).toList(),
                questoes.stream().map(Questao::getId).toList(),
                questoes.isEmpty() ? null : urlQuestao(simulado.getSlug(), 1),
                urlResultado(simulado.getSlug()));
    }

    /** RF13 RN1-RN2: uma pagina por questao, sem gabarito nem comentario. */
    @Transactional(readOnly = true)
    public SimuladoPaginasView.Questao questao(String slug, int n) {
        Simulado simulado = publicado(slug);
        List<Questao> questoes = elegiveis(simulado);
        if (n < 1 || n > questoes.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Questao questao = questaoRepository.findComDetalhesById(questoes.get(n - 1).getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        int total = questoes.size();
        return new SimuladoPaginasView.Questao(
                simulado.getTitulo(),
                slug,
                urlApresentacao(slug),
                n,
                total,
                simulado.getTempoSugeridoMin(),
                questao.getId(),
                questao.getMateria().getNome(),
                QuestaoPublicaService.fonteDe(questao),
                questao.getTipo(),
                markdownService.paraHtml(questao.getEnunciado()),
                questao.getAlternativas().stream()
                        .map(a -> new SimuladoPaginasView.Alternativa(a.getLetra(),
                                markdownService.paraHtml(a.getTexto())))
                        .toList(),
                n > 1 ? urlQuestao(slug, n - 1) : null,
                n < total ? urlQuestao(slug, n + 1) : null,
                urlResultado(slug),
                urlQuestao(slug, 1));
    }

    /** RF13 RN3: gabarito de cada questao, gratuito. O calculo do resultado e feito no navegador. */
    @Transactional(readOnly = true)
    public SimuladoPaginasView.Resultado resultado(String slug) {
        Simulado simulado = publicado(slug);
        List<Questao> questoes = elegiveis(simulado);
        if (questoes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Map<Integer, String> corretas = new HashMap<>();
        for (Object[] linha : alternativaRepository.buscarLetrasCorretas(
                questoes.stream().map(Questao::getId).toList())) {
            corretas.put((Integer) linha[0], (String) linha[1]);
        }
        List<SimuladoPaginasView.ItemGabarito> itens = new java.util.ArrayList<>();
        for (int i = 0; i < questoes.size(); i++) {
            Questao q = questoes.get(i);
            String letra = corretas.getOrDefault(q.getId(), "");
            String trecho = QuestaoPublicaService.cortar(
                    QuestaoPublicaService.textoPlano(markdownService.paraHtml(q.getEnunciado())), LIMITE_TRECHO);
            itens.add(new SimuladoPaginasView.ItemGabarito(i + 1, q.getId(), q.getMateria().getNome(), trecho,
                    letra, QuestaoPublicaService.rotuloCorreta(q.getTipo(), letra),
                    QuestaoPublicaService.urlCanonica(q.getId(), q.getSlug(), null)));
        }
        return new SimuladoPaginasView.Resultado(simulado.getTitulo(), slug, urlApresentacao(slug),
                urlQuestao(slug, 1), simulado.getTempoSugeridoMin(), itens);
    }

    /**
     * @throws ResponseStatusException 404 se inexistente ou RASCUNHO; 410 se ARQUIVADO (RF14 RN3)
     */
    private Simulado publicado(String slug) {
        Simulado simulado = simuladoRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (simulado.getStatus() == StatusSimulado.ARQUIVADO) {
            throw new ResponseStatusException(HttpStatus.GONE);
        }
        if (simulado.getStatus() != StatusSimulado.PUBLICADO) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return simulado;
    }

    /** RF14 RN4: questoes arquivadas, anuladas ou desatualizadas deixam de ser exibidas. */
    private List<Questao> elegiveis(Simulado simulado) {
        return simuladoQuestaoRepository.listarPorSimulado(simulado.getId()).stream()
                .map(SimuladoQuestao::getQuestao)
                .filter(QuestaoDoSimuladoResponse::elegivel)
                .toList();
    }
}
