package br.com.treinoconcursos.service;

import br.com.treinoconcursos.dto.ListagemQuestoesView;
import br.com.treinoconcursos.dto.ListagemQuestoesView.ChipFiltroView;
import br.com.treinoconcursos.dto.ListagemQuestoesView.ItemQuestaoView;
import br.com.treinoconcursos.dto.QuestaoCadernoResponse;
import br.com.treinoconcursos.dto.QuestoesIdsResponse;
import br.com.treinoconcursos.model.Assunto;
import br.com.treinoconcursos.model.Materia;
import br.com.treinoconcursos.model.Questao;
import br.com.treinoconcursos.repository.AssuntoRepository;
import br.com.treinoconcursos.repository.MateriaRepository;
import br.com.treinoconcursos.repository.QuestaoRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
import java.util.regex.Pattern;

/**
 * Navegacao e listagens publicas com ordem fixa por {@code id_questao} crescente e
 * paginacao por cursor ({@code depoisDe}) (RF07 RN1-RN5, US02).
 *
 * Tambem calcula a questao anterior e a proxima dentro do filtro indicado em
 * {@code ?de=}, reutilizado pela pagina publica da questao (RF06 RN3, RF08 RN4).
 */
@Service
public class NavegacaoQuestoesService {

    public static final int TAMANHO_PAGINA_HTML = 20;
    public static final int LIMITE_IDS_PADRAO = 500;
    public static final int LIMITE_IDS_MAXIMO = 1000;
    public static final int LIMITE_RESUMOS_CADERNO = 200;
    static final int LIMITE_RESUMO_ENUNCIADO = 180;

    private static final Pattern FILTRO_VALIDO =
            Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*(/[a-z0-9]+(-[a-z0-9]+)*)?$");

    private final QuestaoRepository questaoRepository;
    private final MateriaRepository materiaRepository;
    private final AssuntoRepository assuntoRepository;
    private final MarkdownService markdownService;

    public NavegacaoQuestoesService(QuestaoRepository questaoRepository, MateriaRepository materiaRepository,
            AssuntoRepository assuntoRepository, MarkdownService markdownService) {
        this.questaoRepository = questaoRepository;
        this.materiaRepository = materiaRepository;
        this.assuntoRepository = assuntoRepository;
        this.markdownService = markdownService;
    }

    /** Materias ativas para a pagina inicial e para os filtros de listagem (RF07). */
    @Transactional(readOnly = true)
    public List<ChipFiltroView> listarMateriasAtivas(String slugMateriaAtiva) {
        return materiaRepository.findByAtivaTrueOrderByNomeAsc().stream()
                .map(m -> new ChipFiltroView(
                        m.getNome(),
                        m.getSlug(),
                        "/questoes/" + m.getSlug(),
                        m.getSlug().equals(slugMateriaAtiva)))
                .toList();
    }

    /**
     * Monta a listagem publica de questoes para {@code /questoes},
     * {@code /questoes/{materia}} ou {@code /questoes/{materia}/{assunto}} (RF07 RN1-RN5).
     */
    @Transactional(readOnly = true)
    public ListagemQuestoesView listarPagina(String slugMateria, String slugAssunto, Integer depoisDe) {
        int cursor = cursorValido(depoisDe);
        Pageable janela = PageRequest.of(0, TAMANHO_PAGINA_HTML + 1);

        Materia materia = null;
        Assunto assunto = null;
        List<Questao> encontradas;
        String filtroAtivo = null;
        String caminhoBase = "/questoes";
        String tituloPagina = "Questões de Concursos";
        String tituloH1 = "Todas as questões";
        String metaDescricao = "Resolva questões de concursos públicos grátis, com gabarito e comentário.";

        if (slugMateria != null) {
            materia = materiaRepository.findBySlug(slugMateria)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            filtroAtivo = materia.getSlug();
            caminhoBase = "/questoes/" + materia.getSlug();
            tituloPagina = "Questões de " + materia.getNome();
            tituloH1 = "Questões de " + materia.getNome();
            metaDescricao = "Questões de " + materia.getNome()
                    + " para concursos públicos, com correção imediata e comentário.";

            if (slugAssunto != null) {
                List<Assunto> todosDaMateria = assuntoRepository.findByMateriaIdOrderByNome(materia.getId());
                assunto = todosDaMateria.stream()
                        .filter(a -> a.getSlug().equals(slugAssunto))
                        .findFirst()
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
                List<Integer> idsAssunto = idsComSubassuntos(todosDaMateria, assunto.getId());
                encontradas = idsAssunto.size() == 1
                        ? questaoRepository.listarPublicadasPorAssuntoDepoisDe(idsAssunto.get(0), cursor, janela)
                        : questaoRepository.listarPublicadasPorAssuntosDepoisDe(idsAssunto, cursor, janela);
                filtroAtivo = materia.getSlug() + "/" + assunto.getSlug();
                caminhoBase = "/questoes/" + filtroAtivo;
                tituloPagina = "Questões de " + assunto.getNome() + " – " + materia.getNome();
                tituloH1 = "Questões de " + assunto.getNome();
                metaDescricao = "Questões de " + assunto.getNome() + " (" + materia.getNome()
                        + ") para concursos públicos, com gabarito e comentário.";
            } else {
                encontradas = questaoRepository.listarPublicadasPorMateriaDepoisDe(materia.getId(), cursor, janela);
            }
        } else {
            encontradas = questaoRepository.listarPublicadasDepoisDe(cursor, janela);
        }

        boolean temMais = encontradas.size() > TAMANHO_PAGINA_HTML;
        List<Questao> pagina = temMais ? encontradas.subList(0, TAMANHO_PAGINA_HTML) : encontradas;
        Integer proximoCursor = temMais ? pagina.get(pagina.size() - 1).getId() : null;
        String proximaPaginaUrl = proximoCursor != null ? caminhoBase + "?depoisDe=" + proximoCursor : null;

        String filtroUrl = filtroAtivo;
        List<ItemQuestaoView> itens = pagina.stream()
                .map(q -> paraItemView(q, filtroUrl))
                .toList();

        List<ChipFiltroView> chipsMaterias = listarMateriasAtivas(materia != null ? materia.getSlug() : null);
        List<ChipFiltroView> chipsAssuntos = materia != null
                ? listarAssuntosAtivosDaMateria(materia, assunto != null ? assunto.getSlug() : null)
                : List.of();

        return new ListagemQuestoesView(
                QuestaoPublicaService.cortar(tituloPagina, QuestaoPublicaService.LIMITE_TITULO_PAGINA),
                tituloH1,
                QuestaoPublicaService.cortar(metaDescricao, QuestaoPublicaService.LIMITE_META_DESCRICAO),
                materia != null ? materia.getSlug() : null,
                materia != null ? materia.getNome() : null,
                assunto != null ? assunto.getSlug() : null,
                assunto != null ? assunto.getNome() : null,
                filtroAtivo,
                caminhoBase,
                chipsMaterias,
                chipsAssuntos,
                itens,
                proximoCursor,
                proximaPaginaUrl);
    }

    /**
     * {@code GET /api/questoes/ids}: devolve apenas os ids publicados do filtro em ordem
     * crescente, paginados por cursor (RF07, RF10, doc/api.md secao 8).
     */
    @Transactional(readOnly = true)
    public QuestoesIdsResponse buscarIds(String slugMateria, String slugAssunto, Integer depoisDe, Integer limite) {
        int cursor = cursorValido(depoisDe);
        int tamanho = normalizarLimite(limite);
        Pageable janela = PageRequest.of(0, tamanho + 1);

        List<Integer> encontrados = consultarIdsPorFiltro(limparSlug(slugMateria), limparSlug(slugAssunto), cursor, janela);
        boolean temMais = encontrados.size() > tamanho;
        List<Integer> itens = temMais ? List.copyOf(encontrados.subList(0, tamanho)) : encontrados;
        Integer proximoCursor = temMais ? itens.get(itens.size() - 1) : null;
        return new QuestoesIdsResponse(itens, proximoCursor);
    }

    /**
     * {@code GET /api/questoes/resumos?ids=...}: devolve os dados minimos das questoes
     * publicadas informadas, em ordem crescente de id, para montar o caderno de erros no navegador
     * (RF11 RN1-RN2, US04).
     */
    @Transactional(readOnly = true)
    public List<QuestaoCadernoResponse> buscarResumosPorIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Integer> idsValidos = ids.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .limit(LIMITE_RESUMOS_CADERNO)
                .toList();
        if (idsValidos.isEmpty()) {
            return List.of();
        }
        return questaoRepository.listarPublicadasPorIds(idsValidos).stream()
                .map(this::paraCadernoResponse)
                .toList();
    }

    /**
     * Descobre a questao anterior e a proxima publicadas em ordem crescente de id,
     * respeitando o filtro {@code ?de=} (materia ou materia/assunto com subassuntos).
     */
    @Transactional(readOnly = true)
    public Vizinhanca buscarVizinhanca(Integer idQuestao, String filtroNormalizado) {
        if (filtroNormalizado != null) {
            String[] partes = filtroNormalizado.split("/", 2);
            Materia materia = materiaRepository.findBySlug(partes[0]).orElse(null);
            if (materia != null) {
                if (partes.length == 2) {
                    List<Integer> idsAssunto = idsDoAssuntoESubassuntos(materia.getId(), partes[1]);
                    if (!idsAssunto.isEmpty()) {
                        if (idsAssunto.size() == 1) {
                            Integer idUnico = idsAssunto.get(0);
                            return new Vizinhanca(
                                    questaoRepository.anteriorPublicadaPorAssunto(idUnico, idQuestao).orElse(null),
                                    questaoRepository.proximaPublicadaPorAssunto(idUnico, idQuestao).orElse(null));
                        }
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

    /**
     * Carrega os ids do assunto informado e de todos os seus subassuntos (ate 3 niveis)
     * dentro da mesma materia (RF07 RN1, doc/banco-de-dados.md secao 4).
     */
    @Transactional(readOnly = true)
    public List<Integer> idsDoAssuntoESubassuntos(Integer idMateria, String slugAssunto) {
        List<Assunto> todos = assuntoRepository.findByMateriaIdOrderByNome(idMateria);
        Assunto raiz = todos.stream().filter(a -> a.getSlug().equals(slugAssunto)).findFirst().orElse(null);
        if (raiz == null) {
            return List.of();
        }
        return idsComSubassuntos(todos, raiz.getId());
    }

    public static String normalizarFiltro(String de) {
        if (de == null) {
            return null;
        }
        String limpo = de.trim().replaceAll("^/+|/+$", "");
        return FILTRO_VALIDO.matcher(limpo).matches() ? limpo : null;
    }

    private List<Integer> consultarIdsPorFiltro(String slugMateria, String slugAssunto, int cursor, Pageable janela) {
        if (slugMateria != null) {
            Materia materia = materiaRepository.findBySlug(slugMateria).orElse(null);
            if (materia == null) {
                return List.of();
            }
            if (slugAssunto != null) {
                List<Integer> idsAssunto = idsDoAssuntoESubassuntos(materia.getId(), slugAssunto);
                if (idsAssunto.isEmpty()) {
                    return List.of();
                }
                return idsAssunto.size() == 1
                        ? questaoRepository.buscarIdsPublicadasPorAssuntoDepoisDe(idsAssunto.get(0), cursor, janela)
                        : questaoRepository.buscarIdsPublicadasPorAssuntosDepoisDe(idsAssunto, cursor, janela);
            }
            return questaoRepository.buscarIdsPublicadasPorMateriaDepoisDe(materia.getId(), cursor, janela);
        }
        if (slugAssunto != null) {
            return List.of();
        }
        return questaoRepository.buscarIdsPublicadasDepoisDe(cursor, janela);
    }

    private List<ChipFiltroView> listarAssuntosAtivosDaMateria(Materia materia, String slugAssuntoAtivo) {
        return assuntoRepository.findByMateriaIdOrderByNome(materia.getId()).stream()
                .filter(Assunto::isAtivo)
                .map(a -> new ChipFiltroView(
                        a.getNome(),
                        a.getSlug(),
                        "/questoes/" + materia.getSlug() + "/" + a.getSlug(),
                        a.getSlug().equals(slugAssuntoAtivo)))
                .toList();
    }

    private ItemQuestaoView paraItemView(Questao q, String filtro) {
        String enunciadoHtml = markdownService.paraHtml(q.getEnunciado());
        String resumo = QuestaoPublicaService.cortar(
                QuestaoPublicaService.textoPlano(enunciadoHtml),
                LIMITE_RESUMO_ENUNCIADO);
        return new ItemQuestaoView(
                q.getId(),
                q.getSlug(),
                QuestaoPublicaService.urlCanonica(q.getId(), q.getSlug(), filtro),
                q.getMateria().getNome(),
                q.getAssunto() != null ? q.getAssunto().getNome() : null,
                QuestaoPublicaService.fonteDe(q),
                resumo,
                q.isAnulada(),
                q.isDesatualizada());
    }

    private QuestaoCadernoResponse paraCadernoResponse(Questao q) {
        String enunciadoHtml = markdownService.paraHtml(q.getEnunciado());
        String resumo = QuestaoPublicaService.cortar(
                QuestaoPublicaService.textoPlano(enunciadoHtml),
                LIMITE_RESUMO_ENUNCIADO);
        return new QuestaoCadernoResponse(
                q.getId(),
                q.getSlug(),
                q.getMateria().getSlug(),
                q.getMateria().getNome(),
                q.getAssunto() != null ? q.getAssunto().getNome() : null,
                QuestaoPublicaService.fonteDe(q),
                resumo,
                q.isAnulada(),
                q.isDesatualizada());
    }

    private static List<Integer> idsComSubassuntos(List<Assunto> todosDaMateria, Integer idRaiz) {
        Map<Integer, List<Integer>> filhosPorPai = new HashMap<>();
        for (Assunto a : todosDaMateria) {
            if (a.getAssuntoPai() != null) {
                filhosPorPai.computeIfAbsent(a.getAssuntoPai().getId(), k -> new ArrayList<>()).add(a.getId());
            }
        }
        List<Integer> ids = new ArrayList<>();
        Deque<Integer> fila = new ArrayDeque<>();
        fila.add(idRaiz);
        while (!fila.isEmpty()) {
            Integer atual = fila.removeFirst();
            ids.add(atual);
            fila.addAll(filhosPorPai.getOrDefault(atual, List.of()));
        }
        return ids;
    }

    private static int cursorValido(Integer depoisDe) {
        return depoisDe == null || depoisDe < 0 ? 0 : depoisDe;
    }

    private static int normalizarLimite(Integer limite) {
        if (limite == null || limite <= 0) {
            return LIMITE_IDS_PADRAO;
        }
        return Math.min(limite, LIMITE_IDS_MAXIMO);
    }

    private static String limparSlug(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }

    public record Vizinhanca(Questao anterior, Questao proxima) {
    }
}
