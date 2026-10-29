package br.com.treinoconcursos.service;

import br.com.treinoconcursos.dto.ListagemQuestoesView;
import br.com.treinoconcursos.dto.ListagemQuestoesView.ChipFiltroView;
import br.com.treinoconcursos.dto.ListagemQuestoesView.ItemQuestaoView;
import br.com.treinoconcursos.dto.QuestaoCadernoResponse;
import br.com.treinoconcursos.dto.QuestoesIdsResponse;
import br.com.treinoconcursos.dto.ReferenciaQuestao;
import br.com.treinoconcursos.model.Questao;
import br.com.treinoconcursos.repository.QuestaoRepository;
import br.com.treinoconcursos.service.ClassificacaoCacheService.AssuntoCache;
import br.com.treinoconcursos.service.ClassificacaoCacheService.MateriaCache;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Navegacao e listagens publicas com ordem fixa por {@code id_questao} crescente e
 * paginacao por cursor ({@code depoisDe}) (RF07 RN1-RN5, US02).
 *
 * Tambem calcula a questao anterior e a proxima dentro do filtro indicado em
 * {@code ?de=}, reutilizado pela pagina publica da questao (RF06 RN3, RF08 RN4).
 *
 * As classificacoes (materias e arvore de assuntos) sao lidas em memoria via
 * {@link ClassificacaoCacheService} para reduzir consultas ao banco (RNF03).
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
    private final ClassificacaoCacheService classificacaoCacheService;
    private final MarkdownService markdownService;

    public NavegacaoQuestoesService(QuestaoRepository questaoRepository,
            ClassificacaoCacheService classificacaoCacheService,
            MarkdownService markdownService) {
        this.questaoRepository = questaoRepository;
        this.classificacaoCacheService = classificacaoCacheService;
        this.markdownService = markdownService;
    }

    /** Materias ativas para a pagina inicial e para os filtros de listagem (RF07, RNF03). */
    @Transactional(readOnly = true)
    public List<ChipFiltroView> listarMateriasAtivas(String slugMateriaAtiva) {
        return classificacaoCacheService.listarMateriasAtivas(slugMateriaAtiva);
    }

    /**
     * Monta a listagem publica de questoes para {@code /questoes},
     * {@code /questoes/{materia}} ou {@code /questoes/{materia}/{assunto}} (RF07 RN1-RN5).
     */
    @Transactional(readOnly = true)
    public ListagemQuestoesView listarPagina(String slugMateria, String slugAssunto, Integer depoisDe) {
        int cursor = cursorValido(depoisDe);
        Pageable janela = PageRequest.of(0, TAMANHO_PAGINA_HTML + 1);

        MateriaCache materia = null;
        AssuntoCache assunto = null;
        List<Integer> idsEncontrados;
        String filtroAtivo = null;
        String caminhoBase = "/questoes";
        String tituloPagina = "Questões de Concursos";
        String tituloH1 = "Todas as questões";
        String metaDescricao = "Resolva questões de concursos públicos grátis, com gabarito e comentário.";

        if (slugMateria != null) {
            materia = classificacaoCacheService.buscarMateriaPorSlug(slugMateria)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            filtroAtivo = materia.slug();
            caminhoBase = "/questoes/" + materia.slug();
            tituloPagina = "Questões de " + materia.nome();
            tituloH1 = "Questões de " + materia.nome();
            metaDescricao = "Questões de " + materia.nome()
                    + " para concursos públicos, com correção imediata e comentário.";

            if (slugAssunto != null) {
                assunto = classificacaoCacheService.buscarAssuntoPorSlug(materia.id(), slugAssunto)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
                List<Integer> idsAssunto = classificacaoCacheService.idsDoAssuntoESubassuntos(
                        materia.id(), assunto.slug());
                idsEncontrados = idsAssunto.size() == 1
                        ? questaoRepository.buscarIdsPublicadasPorAssuntoDepoisDe(idsAssunto.get(0), cursor, janela)
                        : questaoRepository.buscarIdsPublicadasPorAssuntosDepoisDe(idsAssunto, cursor, janela);
                filtroAtivo = materia.slug() + "/" + assunto.slug();
                caminhoBase = "/questoes/" + filtroAtivo;
                tituloPagina = "Questões de " + assunto.nome() + " – " + materia.nome();
                tituloH1 = "Questões de " + assunto.nome();
                metaDescricao = "Questões de " + assunto.nome() + " (" + materia.nome()
                        + ") para concursos públicos, com gabarito e comentário.";
            } else {
                idsEncontrados = questaoRepository.buscarIdsPublicadasPorMateriaDepoisDe(materia.id(), cursor, janela);
            }
        } else {
            idsEncontrados = questaoRepository.buscarIdsPublicadasDepoisDe(cursor, janela);
        }

        boolean temMais = idsEncontrados.size() > TAMANHO_PAGINA_HTML;
        List<Integer> idsPagina = temMais ? idsEncontrados.subList(0, TAMANHO_PAGINA_HTML) : idsEncontrados;
        List<Questao> pagina = idsPagina.isEmpty()
                ? List.of()
                : questaoRepository.listarPublicadasPorIds(idsPagina);
        Integer proximoCursor = temMais && !idsPagina.isEmpty() ? idsPagina.get(idsPagina.size() - 1) : null;
        String proximaPaginaUrl = proximoCursor != null ? caminhoBase + "?depoisDe=" + proximoCursor : null;

        String filtroUrl = filtroAtivo;
        List<ItemQuestaoView> itens = pagina.stream()
                .map(q -> paraItemView(q, filtroUrl))
                .toList();

        List<ChipFiltroView> chipsMaterias = listarMateriasAtivas(materia != null ? materia.slug() : null);
        List<ChipFiltroView> chipsAssuntos = materia != null
                ? classificacaoCacheService.listarAssuntosAtivosDaMateria(
                        materia, assunto != null ? assunto.slug() : null)
                : List.of();

        return new ListagemQuestoesView(
                QuestaoPublicaService.cortar(tituloPagina, QuestaoPublicaService.LIMITE_TITULO_PAGINA),
                tituloH1,
                QuestaoPublicaService.cortar(metaDescricao, QuestaoPublicaService.LIMITE_META_DESCRICAO),
                materia != null ? materia.slug() : null,
                materia != null ? materia.nome() : null,
                assunto != null ? assunto.slug() : null,
                assunto != null ? assunto.nome() : null,
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
            MateriaCache materia = classificacaoCacheService.buscarMateriaPorSlug(partes[0]).orElse(null);
            if (materia != null) {
                if (partes.length == 2) {
                    List<Integer> idsAssunto = idsDoAssuntoESubassuntos(materia.id(), partes[1]);
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
                        questaoRepository.anteriorPublicadaPorMateria(materia.id(), idQuestao).orElse(null),
                        questaoRepository.proximaPublicadaPorMateria(materia.id(), idQuestao).orElse(null));
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
        return classificacaoCacheService.idsDoAssuntoESubassuntos(idMateria, slugAssunto);
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
            MateriaCache materia = classificacaoCacheService.buscarMateriaPorSlug(slugMateria).orElse(null);
            if (materia == null) {
                return List.of();
            }
            if (slugAssunto != null) {
                List<Integer> idsAssunto = idsDoAssuntoESubassuntos(materia.id(), slugAssunto);
                if (idsAssunto.isEmpty()) {
                    return List.of();
                }
                return idsAssunto.size() == 1
                        ? questaoRepository.buscarIdsPublicadasPorAssuntoDepoisDe(idsAssunto.get(0), cursor, janela)
                        : questaoRepository.buscarIdsPublicadasPorAssuntosDepoisDe(idsAssunto, cursor, janela);
            }
            return questaoRepository.buscarIdsPublicadasPorMateriaDepoisDe(materia.id(), cursor, janela);
        }
        if (slugAssunto != null) {
            return List.of();
        }
        return questaoRepository.buscarIdsPublicadasDepoisDe(cursor, janela);
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

    public record Vizinhanca(ReferenciaQuestao anterior, ReferenciaQuestao proxima) {
    }
}
