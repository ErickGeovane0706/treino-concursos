package br.com.treinoconcursos.service;

import br.com.treinoconcursos.dto.FiltroQuestoes;
import br.com.treinoconcursos.dto.ListagemQuestoesView;
import br.com.treinoconcursos.dto.ListagemQuestoesView.ChipFiltroView;
import br.com.treinoconcursos.dto.ListagemQuestoesView.ItemQuestaoView;
import br.com.treinoconcursos.dto.QuestaoCadernoResponse;
import br.com.treinoconcursos.dto.QuestoesIdsResponse;
import br.com.treinoconcursos.dto.ReferenciaQuestao;
import br.com.treinoconcursos.model.NivelEscolaridade;
import br.com.treinoconcursos.model.Questao;
import br.com.treinoconcursos.model.TipoQuestao;
import br.com.treinoconcursos.repository.QuestaoRepository;
import br.com.treinoconcursos.service.ClassificacaoCacheService.AssuntoCache;
import br.com.treinoconcursos.service.ClassificacaoCacheService.BancaCache;
import br.com.treinoconcursos.service.ClassificacaoCacheService.MateriaCache;
import br.com.treinoconcursos.service.ClassificacaoCacheService.OrgaoCache;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Navegacao e listagens publicas com ordem fixa por {@code id_questao} crescente e
 * paginacao por cursor ({@code depoisDe}) (RF07 RN1-RN5, US02).
 *
 * Tambem calcula a questao anterior e a proxima dentro do filtro indicado em
 * {@code ?de=}, reutilizado pela pagina publica da questao (RF06 RN3, RF08 RN4).
 *
 * As classificacoes (materias, assuntos, bancas, orgaos e provas) sao lidas em memoria
 * via {@link ClassificacaoCacheService} para reduzir consultas ao banco (RNF03).
 */
@Service
public class NavegacaoQuestoesService {

    public static final int TAMANHO_PAGINA_HTML = 20;
    public static final int MINIMO_QUESTOES_INDEXAVEL = 10;
    public static final int MAXIMO_FILTROS_INDEXAVEL = 2;
    public static final int LIMITE_IDS_PADRAO = 500;
    public static final int LIMITE_IDS_MAXIMO = 1000;
    public static final int LIMITE_RESUMOS_CADERNO = 200;
    static final int LIMITE_RESUMO_ENUNCIADO = 180;

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
     * Monta a listagem publica para rotas dedicadas ({@code /questoes/{materia}},
     * {@code /questoes/{materia}/{assunto}}) (RF07 RN1-RN5).
     */
    @Transactional(readOnly = true)
    public ListagemQuestoesView listarPagina(String slugMateria, String slugAssunto, Integer depoisDe) {
        FiltroQuestoes filtro = FiltroQuestoes.de(slugMateria, slugAssunto, null, null, null, null, null);
        return listarPagina(filtro, depoisDe, true);
    }

    /**
     * Monta a listagem publica de questoes para qualquer rota dedicada ou combinacao de
     * filtros via query string (RF07 RN1-RN5).
     *
     * @param exigirExistencia {@code true} quando os slugs principais vieram de rota dedicada
     *                         (retorna 404 se o slug nao existir no catalogo)
     */
    @Transactional(readOnly = true)
    public ListagemQuestoesView listarPagina(FiltroQuestoes filtro, Integer depoisDe, boolean exigirExistencia) {
        int cursor = cursorValido(depoisDe);
        Pageable janela = PageRequest.of(0, TAMANHO_PAGINA_HTML + 1);

        FiltroResolvido resolvido = resolverFiltro(filtro, exigirExistencia);
        List<Integer> idsEncontrados = consultarIds(resolvido, cursor, janela);

        boolean temMais = idsEncontrados.size() > TAMANHO_PAGINA_HTML;
        List<Integer> idsPagina = temMais ? idsEncontrados.subList(0, TAMANHO_PAGINA_HTML) : idsEncontrados;
        List<Questao> pagina = idsPagina.isEmpty()
                ? List.of()
                : questaoRepository.listarPublicadasPorIds(idsPagina);
        Integer proximoCursor = temMais && !idsPagina.isEmpty() ? idsPagina.get(idsPagina.size() - 1) : null;

        String filtroAtivo = filtro.chaveFiltro();
        String caminhoBase = filtro.urlListagem(null);
        String proximaPaginaUrl = proximoCursor != null ? filtro.urlListagem(proximoCursor) : null;

        List<ItemQuestaoView> itens = pagina.stream()
                .map(q -> paraItemView(q, filtroAtivo))
                .toList();

        boolean indexavel = calcularIndexavel(filtro, resolvido, itens.size(), idsEncontrados.size(), cursor);
         TextosCabecalho textos = montarTextosCabecalho(filtro, resolvido);

        MateriaCache materia = resolvido.materia();
        AssuntoCache assunto = resolvido.assunto();
        BancaCache banca = resolvido.banca();
        OrgaoCache orgao = resolvido.orgao();

        List<ChipFiltroView> chipsMaterias = classificacaoCacheService.listarMaterias().stream()
                .map(m -> new ChipFiltroView(
                        m.nome(),
                        m.slug(),
                        filtro.comMateria(m.slug()).urlListagem(null),
                        materia != null && m.slug().equals(materia.slug())))
                .toList();

        List<ChipFiltroView> chipsAssuntos = materia != null
                ? classificacaoCacheService.listarAssuntosAtivos(materia.id()).stream()
                        .map(a -> new ChipFiltroView(
                                a.nome(),
                                a.slug(),
                                filtro.comAssunto(a.slug()).urlListagem(null),
                                assunto != null && a.slug().equals(assunto.slug())))
                        .toList()
                : List.of();

        List<ChipFiltroView> chipsBancas = classificacaoCacheService.listarBancas().stream()
                .map(b -> new ChipFiltroView(
                        b.sigla(),
                        b.slug(),
                        filtro.comBanca(b.slug()).urlListagem(null),
                        banca != null && b.slug().equals(banca.slug())))
                .toList();

        List<ChipFiltroView> chipsOrgaos = classificacaoCacheService.listarOrgaos().stream()
                .map(o -> new ChipFiltroView(
                        o.sigla(),
                        o.slug(),
                        filtro.comOrgao(o.slug()).urlListagem(null),
                        orgao != null && o.slug().equals(orgao.slug())))
                .toList();

        List<ChipFiltroView> chipsAnos = classificacaoCacheService.listarAnos().stream()
                .map(a -> new ChipFiltroView(
                        String.valueOf(a),
                        String.valueOf(a),
                        filtro.comAno(a).urlListagem(null),
                        filtro.ano() != null && filtro.ano().equals(a)))
                .toList();

        List<ChipFiltroView> chipsNiveis = Arrays.stream(NivelEscolaridade.values())
                .map(n -> new ChipFiltroView(
                        FiltroQuestoes.rotuloNivel(n),
                        n.name(),
                        filtro.comNivel(n).urlListagem(null),
                        filtro.nivel() == n))
                .toList();

        return new ListagemQuestoesView(
                QuestaoPublicaService.cortar(textos.tituloPagina(), QuestaoPublicaService.LIMITE_TITULO_PAGINA),
                textos.tituloH1(),
                QuestaoPublicaService.cortar(textos.metaDescricao(), QuestaoPublicaService.LIMITE_META_DESCRICAO),
                indexavel,
                materia != null ? materia.slug() : null,
                materia != null ? materia.nome() : null,
                assunto != null ? assunto.slug() : null,
                assunto != null ? assunto.nome() : null,
                banca != null ? banca.slug() : null,
                banca != null ? banca.sigla() + " – " + banca.nome() : null,
                orgao != null ? orgao.slug() : null,
                orgao != null ? orgao.sigla() + " – " + orgao.nome() : null,
                filtro.ano(),
                filtro.nivel() != null ? filtro.nivel().name() : null,
                FiltroQuestoes.rotuloNivel(filtro.nivel()),
                filtro.tipo() != null ? filtro.tipo().name() : null,
                FiltroQuestoes.rotuloTipo(filtro.tipo()),
                filtroAtivo,
                caminhoBase,
                filtro.comMateria(null).urlListagem(null),
                filtro.comAssunto(null).urlListagem(null),
                filtro.comBanca(null).urlListagem(null),
                filtro.comOrgao(null).urlListagem(null),
                filtro.comAno(null).urlListagem(null),
                filtro.comNivel(null).urlListagem(null),
                chipsMaterias,
                chipsAssuntos,
                chipsBancas,
                chipsOrgaos,
                chipsAnos,
                chipsNiveis,
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
        return buscarIds(
                FiltroQuestoes.deParametros(slugMateria, slugAssunto, null, null, null, null, null),
                depoisDe,
                limite);
    }

    @Transactional(readOnly = true)
    public QuestoesIdsResponse buscarIds(FiltroQuestoes filtro, Integer depoisDe, Integer limite) {
        int cursor = cursorValido(depoisDe);
        int tamanho = normalizarLimite(limite);
        Pageable janela = PageRequest.of(0, tamanho + 1);

        FiltroResolvido resolvido = resolverFiltro(filtro, false);
        List<Integer> encontrados = consultarIds(resolvido, cursor, janela);
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
     * respeitando o filtro {@code ?de=}.
     */
    @Transactional(readOnly = true)
    public Vizinhanca buscarVizinhanca(Integer idQuestao, String filtroNormalizado) {
        if (filtroNormalizado != null
                && !"caderno".equals(filtroNormalizado)
                && !filtroNormalizado.startsWith("caderno/")) {
            FiltroQuestoes filtro = FiltroQuestoes.deChaveFiltro(filtroNormalizado);
            if (filtro != null && !filtro.vazio()) {
                FiltroResolvido r = resolverFiltro(filtro, false);
                if (!r.entidadeInexistente()) {
                    return consultarVizinhanca(r, idQuestao);
                }
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

    public String rotuloListagemDeFiltro(FiltroQuestoes filtro) {
        FiltroResolvido r = resolverFiltro(filtro, false);
        return montarTextosCabecalho(filtro, r).tituloH1();
    }

    public static String normalizarFiltro(String de) {
        if (de == null) {
            return null;
        }
        String limpo = de.trim().replaceAll("^/+|/+$", "");
        if ("caderno".equals(limpo)) {
            return "caderno";
        }
        if (limpo.startsWith("caderno/")) {
            String sub = limpo.substring("caderno/".length());
            return FiltroQuestoes.slugValido(sub) ? limpo : null;
        }
        FiltroQuestoes filtro = FiltroQuestoes.deChaveFiltro(limpo);
        return filtro != null ? filtro.chaveFiltro() : null;
    }

    private boolean calcularIndexavel(
            FiltroQuestoes filtro,
            FiltroResolvido resolvido,
            int quantidadeNaPagina,
            int encontradosNaJanela,
            int cursor) {
        if (quantidadeNaPagina == 0 || filtro.invalido() || resolvido.vazioSemResultado()) {
            return false;
        }
        int qtdFiltros = filtro.quantidadeFiltros();
        if (qtdFiltros > MAXIMO_FILTROS_INDEXAVEL) {
            return false;
        }
        if (qtdFiltros == 0) {
            return true;
        }
        if (encontradosNaJanela >= MINIMO_QUESTOES_INDEXAVEL) {
            return true;
        }
        if (cursor == 0) {
            return false;
        }
        List<Integer> primeiros = consultarIds(
                resolvido, 0, PageRequest.of(0, MINIMO_QUESTOES_INDEXAVEL));
        return primeiros.size() >= MINIMO_QUESTOES_INDEXAVEL;
    }

    private FiltroResolvido resolverFiltro(FiltroQuestoes filtro, boolean exigirExistencia) {
        if (filtro == null || filtro.vazio()) {
            return new FiltroResolvido(null, null, null, null, null, false, List.of(), false, List.of(), null, false, false);
        }
        if (filtro.invalido()) {
            return new FiltroResolvido(null, null, null, null, null, false, List.of(), false, List.of(), null, true, true);
        }

        MateriaCache materia = null;
        AssuntoCache assunto = null;
        BancaCache banca = null;
        OrgaoCache orgao = null;
        boolean entidadeInexistente = false;
        boolean vazioSemResultado = false;

        if (filtro.slugMateria() != null) {
            materia = classificacaoCacheService.buscarMateriaPorSlug(filtro.slugMateria()).orElse(null);
            if (materia == null) {
                if (exigirExistencia) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND);
                }
                entidadeInexistente = true;
                vazioSemResultado = true;
            }
        }

        List<Integer> idsAssunto = List.of();
        boolean filtrarAssuntos = false;
        if (filtro.slugAssunto() != null) {
            filtrarAssuntos = true;
            if (materia == null) {
                if (exigirExistencia) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND);
                }
                entidadeInexistente = true;
                vazioSemResultado = true;
            } else {
                assunto = classificacaoCacheService.buscarAssuntoPorSlug(materia.id(), filtro.slugAssunto()).orElse(null);
                if (assunto == null) {
                    if (exigirExistencia) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND);
                    }
                    entidadeInexistente = true;
                    vazioSemResultado = true;
                } else {
                    idsAssunto = classificacaoCacheService.idsDoAssuntoESubassuntos(materia.id(), assunto.slug());
                    if (idsAssunto.isEmpty()) {
                        vazioSemResultado = true;
                    }
                }
            }
        }

        if (filtro.slugBanca() != null) {
            banca = classificacaoCacheService.buscarBancaPorSlug(filtro.slugBanca()).orElse(null);
            if (banca == null) {
                if (exigirExistencia) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND);
                }
                entidadeInexistente = true;
                vazioSemResultado = true;
            }
        }

        if (filtro.slugOrgao() != null) {
            orgao = classificacaoCacheService.buscarOrgaoPorSlug(filtro.slugOrgao()).orElse(null);
            if (orgao == null) {
                if (exigirExistencia) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND);
                }
                entidadeInexistente = true;
                vazioSemResultado = true;
            }
        }

        List<Integer> idsProva = List.of();
        boolean filtrarProvas = filtro.temFiltroDeProva();
        if (filtrarProvas && !vazioSemResultado) {
            idsProva = classificacaoCacheService.idsDeProvas(
                    banca != null ? banca.id() : null,
                    orgao != null ? orgao.id() : null,
                    filtro.ano(),
                    filtro.nivel());
            if (idsProva.isEmpty()) {
                vazioSemResultado = true;
            }
        }

        return new FiltroResolvido(
                materia,
                assunto,
                banca,
                orgao,
                materia != null ? materia.id() : null,
                filtrarAssuntos,
                idsAssunto,
                filtrarProvas,
                idsProva,
                filtro.tipo(),
                vazioSemResultado,
                entidadeInexistente);
    }

    private List<Integer> consultarIds(FiltroResolvido r, int cursor, Pageable janela) {
        if (r.vazioSemResultado()) {
            return List.of();
        }
        if (r.idMateria() == null && !r.filtrarAssuntos() && !r.filtrarProvas() && r.tipo() == null) {
            return questaoRepository.buscarIdsPublicadasDepoisDe(cursor, janela);
        }
        if (r.idMateria() != null && !r.filtrarAssuntos() && !r.filtrarProvas() && r.tipo() == null) {
            return questaoRepository.buscarIdsPublicadasPorMateriaDepoisDe(r.idMateria(), cursor, janela);
        }
        if (r.filtrarAssuntos() && !r.filtrarProvas() && r.tipo() == null) {
            return r.idsAssunto().size() == 1
                    ? questaoRepository.buscarIdsPublicadasPorAssuntoDepoisDe(r.idsAssunto().get(0), cursor, janela)
                    : questaoRepository.buscarIdsPublicadasPorAssuntosDepoisDe(r.idsAssunto(), cursor, janela);
        }
        if (r.idMateria() == null && !r.filtrarAssuntos() && r.filtrarProvas() && r.tipo() == null) {
            return r.idsProva().size() == 1
                    ? questaoRepository.buscarIdsPublicadasPorProvaDepoisDe(r.idsProva().get(0), cursor, janela)
                    : questaoRepository.buscarIdsPublicadasPorProvasDepoisDe(r.idsProva(), cursor, janela);
        }
        return questaoRepository.buscarIdsPublicadasCombinadasDepoisDe(
                r.idMateria(),
                r.filtrarAssuntos(),
                r.idsAssunto().isEmpty() ? List.of(-1) : r.idsAssunto(),
                r.filtrarProvas(),
                r.idsProva().isEmpty() ? List.of(-1) : r.idsProva(),
                r.tipo(),
                cursor,
                janela);
    }

    private Vizinhanca consultarVizinhanca(FiltroResolvido r, Integer idQuestao) {
        if (r.vazioSemResultado()) {
            return new Vizinhanca(null, null);
        }
        if (r.idMateria() != null && !r.filtrarAssuntos() && !r.filtrarProvas() && r.tipo() == null) {
            return new Vizinhanca(
                    questaoRepository.anteriorPublicadaPorMateria(r.idMateria(), idQuestao).orElse(null),
                    questaoRepository.proximaPublicadaPorMateria(r.idMateria(), idQuestao).orElse(null));
        }
        if (r.filtrarAssuntos() && !r.filtrarProvas() && r.tipo() == null) {
            if (r.idsAssunto().size() == 1) {
                Integer idUnico = r.idsAssunto().get(0);
                return new Vizinhanca(
                        questaoRepository.anteriorPublicadaPorAssunto(idUnico, idQuestao).orElse(null),
                        questaoRepository.proximaPublicadaPorAssunto(idUnico, idQuestao).orElse(null));
            }
            return new Vizinhanca(
                    questaoRepository.anteriorPublicadaPorAssuntos(r.idsAssunto(), idQuestao).orElse(null),
                    questaoRepository.proximaPublicadaPorAssuntos(r.idsAssunto(), idQuestao).orElse(null));
        }
        if (r.idMateria() == null && !r.filtrarAssuntos() && r.filtrarProvas() && r.tipo() == null) {
            if (r.idsProva().size() == 1) {
                Integer idProva = r.idsProva().get(0);
                return new Vizinhanca(
                        questaoRepository.anteriorPublicadaPorProva(idProva, idQuestao).orElse(null),
                        questaoRepository.proximaPublicadaPorProva(idProva, idQuestao).orElse(null));
            }
            return new Vizinhanca(
                    questaoRepository.anteriorPublicadaPorProvas(r.idsProva(), idQuestao).orElse(null),
                    questaoRepository.proximaPublicadaPorProvas(r.idsProva(), idQuestao).orElse(null));
        }
        List<Integer> idsAssuntoParam = r.idsAssunto().isEmpty() ? List.of(-1) : r.idsAssunto();
        List<Integer> idsProvaParam = r.idsProva().isEmpty() ? List.of(-1) : r.idsProva();
        return new Vizinhanca(
                questaoRepository.anteriorPublicadaCombinada(
                        r.idMateria(), r.filtrarAssuntos(), idsAssuntoParam,
                        r.filtrarProvas(), idsProvaParam, r.tipo(), idQuestao).orElse(null),
                questaoRepository.proximaPublicadaCombinada(
                        r.idMateria(), r.filtrarAssuntos(), idsAssuntoParam,
                        r.filtrarProvas(), idsProvaParam, r.tipo(), idQuestao).orElse(null));
    }

    private TextosCabecalho montarTextosCabecalho(FiltroQuestoes filtro, FiltroResolvido r) {
        int qtd = filtro.quantidadeFiltros();
        if (qtd == 0) {
            return new TextosCabecalho(
                    "Questões de Concursos",
                    "Todas as questões",
                    "Resolva questões de concursos públicos grátis, com gabarito e comentário.");
        }
        if (qtd == 1) {
            if (r.materia() != null) {
                return new TextosCabecalho(
                        "Questões de " + r.materia().nome(),
                        "Questões de " + r.materia().nome(),
                        "Questões de " + r.materia().nome()
                                + " para concursos públicos, com correção imediata e comentário.");
            }
            if (r.banca() != null) {
                return new TextosCabecalho(
                        "Questões da banca " + r.banca().sigla(),
                        "Questões da banca " + r.banca().sigla(),
                        "Questões de concursos públicos da banca " + r.banca().sigla() + " ("
                                + r.banca().nome() + "), com gabarito e comentário.");
            }
            if (r.orgao() != null) {
                return new TextosCabecalho(
                        "Questões do órgão " + r.orgao().sigla(),
                        "Questões do órgão " + r.orgao().sigla(),
                        "Questões de concursos públicos do órgão " + r.orgao().sigla() + " ("
                                + r.orgao().nome() + "), com gabarito e comentário.");
            }
            if (filtro.ano() != null) {
                return new TextosCabecalho(
                        "Questões de Concursos de " + filtro.ano(),
                        "Questões de " + filtro.ano(),
                        "Questões de provas de concursos públicos aplicadas em " + filtro.ano()
                                + ", com gabarito e comentário.");
            }
            if (filtro.nivel() != null) {
                String rotulo = FiltroQuestoes.rotuloNivel(filtro.nivel());
                return new TextosCabecalho(
                        "Questões de Nível " + rotulo,
                        "Questões de Nível " + rotulo,
                        "Questões de concursos públicos de nível " + rotulo.toLowerCase(Locale.ROOT)
                                + ", com gabarito e comentário.");
            }
            if (filtro.tipo() != null) {
                String rotulo = FiltroQuestoes.rotuloTipo(filtro.tipo());
                return new TextosCabecalho(
                        "Questões de " + rotulo,
                        "Questões de " + rotulo,
                        "Questões de concursos públicos do tipo " + rotulo.toLowerCase(Locale.ROOT)
                                + ", com gabarito e comentário.");
            }
        }
        if (qtd == 2 && r.materia() != null && r.assunto() != null) {
            return new TextosCabecalho(
                    "Questões de " + r.assunto().nome() + " – " + r.materia().nome(),
                    "Questões de " + r.assunto().nome(),
                    "Questões de " + r.assunto().nome() + " (" + r.materia().nome()
                            + ") para concursos públicos, com gabarito e comentário.");
        }

        List<String> partes = new ArrayList<>();
        if (r.materia() != null) {
            partes.add(r.materia().nome());
        } else if (filtro.slugMateria() != null) {
            partes.add(filtro.slugMateria());
        }
        if (r.assunto() != null) {
            partes.add(r.assunto().nome());
        } else if (filtro.slugAssunto() != null) {
            partes.add(filtro.slugAssunto());
        }
        if (r.banca() != null) {
            partes.add(r.banca().sigla());
        } else if (filtro.slugBanca() != null) {
            partes.add(filtro.slugBanca());
        }
        if (r.orgao() != null) {
            partes.add(r.orgao().sigla());
        } else if (filtro.slugOrgao() != null) {
            partes.add(filtro.slugOrgao());
        }
        if (filtro.ano() != null) {
            partes.add(String.valueOf(filtro.ano()));
        }
        if (filtro.nivel() != null) {
            partes.add("Nível " + FiltroQuestoes.rotuloNivel(filtro.nivel()));
        }
        if (filtro.tipo() != null) {
            partes.add(FiltroQuestoes.rotuloTipo(filtro.tipo()));
        }
        String resumo = partes.isEmpty() ? "Questões filtradas" : "Questões: " + String.join(" · ", partes);
        return new TextosCabecalho(
                resumo,
                resumo,
                resumo + " para concursos públicos, com gabarito e comentário.");
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

    public record Vizinhanca(ReferenciaQuestao anterior, ReferenciaQuestao proxima) {
    }

    private record TextosCabecalho(String tituloPagina, String tituloH1, String metaDescricao) {
    }

    private record FiltroResolvido(
            MateriaCache materia,
            AssuntoCache assunto,
            BancaCache banca,
            OrgaoCache orgao,
            Integer idMateria,
            boolean filtrarAssuntos,
            List<Integer> idsAssunto,
            boolean filtrarProvas,
            List<Integer> idsProva,
            TipoQuestao tipo,
            boolean vazioSemResultado,
            boolean entidadeInexistente) {
    }
}
