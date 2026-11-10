package br.com.treinoconcursos.controller;

import br.com.treinoconcursos.IntegracaoTest;
import br.com.treinoconcursos.model.Alternativa;
import br.com.treinoconcursos.model.Assunto;
import br.com.treinoconcursos.model.Banca;
import br.com.treinoconcursos.model.EsferaOrgao;
import br.com.treinoconcursos.model.Materia;
import br.com.treinoconcursos.model.NivelEscolaridade;
import br.com.treinoconcursos.model.Orgao;
import br.com.treinoconcursos.model.OrigemQuestao;
import br.com.treinoconcursos.model.Perfil;
import br.com.treinoconcursos.model.Prova;
import br.com.treinoconcursos.model.Questao;
import br.com.treinoconcursos.model.StatusQuestao;
import br.com.treinoconcursos.model.TipoQuestao;
import br.com.treinoconcursos.model.Usuario;
import br.com.treinoconcursos.repository.AssuntoRepository;
import br.com.treinoconcursos.repository.BancaRepository;
import br.com.treinoconcursos.repository.MateriaRepository;
import br.com.treinoconcursos.repository.OrgaoRepository;
import br.com.treinoconcursos.repository.ProvaRepository;
import br.com.treinoconcursos.repository.QuestaoRepository;
import br.com.treinoconcursos.service.ClassificacaoCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Listagens e navegacao com ordem fixa por id crescente e paginacao por cursor (RF07 RN1-RN5, US02).
 */
class ListagemENavegacaoQuestoesTest extends IntegracaoTest {

    @Autowired
    private MateriaRepository materiaRepository;

    @Autowired
    private AssuntoRepository assuntoRepository;

    @Autowired
    private BancaRepository bancaRepository;

    @Autowired
    private OrgaoRepository orgaoRepository;

    @Autowired
    private ProvaRepository provaRepository;

    @Autowired
    private QuestaoRepository questaoRepository;

    @Autowired
    private ClassificacaoCacheService classificacaoCacheService;

    private Usuario autor;
    private Materia portugues;
    private Materia direito;
    private Assunto sintaxe;
    private Assunto concordancia;
    private Assunto verboHaver;
    private Banca fgv;
    private Banca cebraspe;
    private Orgao tceGo;
    private Orgao prf;
    private Prova provaFgvTce2025Superior;
    private Prova provaCebraspePrf2024Medio;

    @BeforeEach
    void prepararDados() {
        autor = criarUsuario("editor@teste.com.br", Perfil.EDITOR);

        portugues = new Materia();
        portugues.setNome("Português");
        portugues.setSlug("portugues");
        portugues = materiaRepository.save(portugues);

        direito = new Materia();
        direito.setNome("Direito Constitucional");
        direito.setSlug("direito-constitucional");
        direito = materiaRepository.save(direito);

        sintaxe = new Assunto();
        sintaxe.setMateria(portugues);
        sintaxe.setNome("Sintaxe");
        sintaxe.setSlug("sintaxe");
        sintaxe = assuntoRepository.save(sintaxe);

        concordancia = new Assunto();
        concordancia.setMateria(portugues);
        concordancia.setAssuntoPai(sintaxe);
        concordancia.setNome("Concordância verbal");
        concordancia.setSlug("concordancia-verbal");
        concordancia = assuntoRepository.save(concordancia);

        verboHaver = new Assunto();
        verboHaver.setMateria(portugues);
        verboHaver.setAssuntoPai(concordancia);
        verboHaver.setNome("Verbo haver");
        verboHaver.setSlug("verbo-haver");
        verboHaver = assuntoRepository.save(verboHaver);

        fgv = new Banca();
        fgv.setNome("Fundação Getulio Vargas");
        fgv.setSigla("FGV");
        fgv.setSlug("fgv");
        fgv = bancaRepository.save(fgv);

        cebraspe = new Banca();
        cebraspe.setNome("Centro Brasileiro de Pesquisa em Avaliação");
        cebraspe.setSigla("CEBRASPE");
        cebraspe.setSlug("cebraspe");
        cebraspe = bancaRepository.save(cebraspe);

        tceGo = new Orgao();
        tceGo.setNome("Tribunal de Contas do Estado de Goiás");
        tceGo.setSigla("TCE-GO");
        tceGo.setSlug("tce-go");
        tceGo.setEsfera(EsferaOrgao.ESTADUAL);
        tceGo.setUf("GO");
        tceGo = orgaoRepository.save(tceGo);

        prf = new Orgao();
        prf.setNome("Polícia Rodoviária Federal");
        prf.setSigla("PRF");
        prf.setSlug("prf");
        prf.setEsfera(EsferaOrgao.FEDERAL);
        prf = orgaoRepository.save(prf);

        provaFgvTce2025Superior = new Prova();
        provaFgvTce2025Superior.setBanca(fgv);
        provaFgvTce2025Superior.setOrgao(tceGo);
        provaFgvTce2025Superior.setAno(2025);
        provaFgvTce2025Superior.setCargo("Auditor de Controle Externo");
        provaFgvTce2025Superior.setNivel(NivelEscolaridade.SUPERIOR);
        provaFgvTce2025Superior.setSlug("fgv-tce-go-2025-auditor");
        provaFgvTce2025Superior = provaRepository.save(provaFgvTce2025Superior);

        provaCebraspePrf2024Medio = new Prova();
        provaCebraspePrf2024Medio.setBanca(cebraspe);
        provaCebraspePrf2024Medio.setOrgao(prf);
        provaCebraspePrf2024Medio.setAno(2024);
        provaCebraspePrf2024Medio.setCargo("Agente Administrativo");
        provaCebraspePrf2024Medio.setNivel(NivelEscolaridade.MEDIO);
        provaCebraspePrf2024Medio.setSlug("cebraspe-prf-2024-agente");
        provaCebraspePrf2024Medio = provaRepository.save(provaCebraspePrf2024Medio);

        classificacaoCacheService.invalidar();
    }

    @Test
    void paginaInicialExibeLinksParaMateriasAtivas() throws Exception {
        Materia inativa = new Materia();
        inativa.setNome("Matéria Inativa");
        inativa.setSlug("materia-inativa");
        inativa.setAtiva(false);
        materiaRepository.save(inativa);

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/questoes/portugues\"")))
                .andExpect(content().string(containsString("href=\"/questoes/direito-constitucional\"")))
                .andExpect(content().string(not(containsString("Matéria Inativa"))));
    }

    @Test
    void listagensOrdenamPorIdCrescenteIncluemSubassuntosEIgnoramRascunhoOuArquivada() throws Exception {
        Questao q1 = novaQuestao(portugues, sintaxe, "Questão 1 nível 1 sintaxe", StatusQuestao.PUBLICADA);
        Questao qRascunho = novaQuestao(portugues, sintaxe, "Questão rascunho oculta", StatusQuestao.RASCUNHO);
        Questao q2 = novaQuestao(direito, null, "Questão 2 direito constitucional", StatusQuestao.PUBLICADA);
        Questao qArquivada = novaQuestao(portugues, concordancia, "Questão arquivada oculta", StatusQuestao.ARQUIVADA);
        Questao q3 = novaQuestao(portugues, verboHaver, "Questão 3 nível 3 verbo haver", StatusQuestao.PUBLICADA);

        // /questoes: q1, q2, q3 em ordem crescente de id
        String htmlTodas = mockMvc.perform(get("/questoes"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(qRascunho.getEnunciado()))))
                .andExpect(content().string(not(containsString(qArquivada.getEnunciado()))))
                .andReturn().getResponse().getContentAsString();

        assertThat(htmlTodas.indexOf("Questão 1 nível 1 sintaxe"))
                .isLessThan(htmlTodas.indexOf("Questão 2 direito constitucional"));
        assertThat(htmlTodas.indexOf("Questão 2 direito constitucional"))
                .isLessThan(htmlTodas.indexOf("Questão 3 nível 3 verbo haver"));

        // /questoes/portugues: apenas q1 e q3, com ?de=portugues nos links
        mockMvc.perform(get("/questoes/portugues"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "/questao/" + q1.getId() + "/" + q1.getSlug() + "?de=portugues")))
                .andExpect(content().string(containsString(
                        "/questao/" + q3.getId() + "/" + q3.getSlug() + "?de=portugues")))
                .andExpect(content().string(not(containsString("Questão 2 direito constitucional"))));

        // /questoes/portugues/sintaxe: inclui subassuntos de nivel 2 e 3 (verbo-haver)
        mockMvc.perform(get("/questoes/portugues/sintaxe"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "/questao/" + q1.getId() + "/" + q1.getSlug() + "?de=portugues/sintaxe")))
                .andExpect(content().string(containsString(
                        "/questao/" + q3.getId() + "/" + q3.getSlug() + "?de=portugues/sintaxe")));
    }

    @Test
    void cadastrarNovaQuestaoNaoMudaAPosicaoDasAntigasNaPaginacaoPorCursor() throws Exception {
        for (int i = 1; i <= 21; i++) {
            novaQuestao(portugues, sintaxe, "Enunciado numerado " + i, StatusQuestao.PUBLICADA);
        }

        MvcResult primeiraPagina = mockMvc.perform(get("/questoes/portugues"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Enunciado numerado 1")))
                .andExpect(content().string(containsString("Enunciado numerado 20")))
                .andExpect(content().string(not(containsString("Enunciado numerado 21"))))
                .andExpect(content().string(containsString("href=\"/questoes/portugues?depoisDe=20\"")))
                .andReturn();

        assertThat(primeiraPagina.getResponse().getContentAsString()).contains("Mais questões →");

        // Cadastra uma nova questao enquanto o visitante esta na pagina 1 (RF07 RN2-RN3)
        Questao nova = novaQuestao(portugues, sintaxe, "Enunciado recém cadastrado 22", StatusQuestao.PUBLICADA);

        // Ao seguir o cursor ?depoisDe=20, a questao 21 continua exatamente na primeira posicao da pagina seguinte
        String segundaPagina = mockMvc.perform(get("/questoes/portugues").param("depoisDe", "20"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Enunciado numerado 20"))))
                .andExpect(content().string(containsString("Enunciado numerado 21")))
                .andExpect(content().string(containsString(nova.getEnunciado())))
                .andExpect(content().string(not(containsString("data-proximo-cursor"))))
                .andReturn().getResponse().getContentAsString();

        assertThat(segundaPagina.indexOf("Enunciado numerado 21"))
                .isLessThan(segundaPagina.indexOf(nova.getEnunciado()));
    }

    @Test
    void exibeMensagemSemAnuncioQuandoFiltroNaoTemQuestoesERetorna404ParaSlugInexistente() throws Exception {
        // RF07 RN5 e RF12 RN4: lista vazia sugere remover filtros e nao exibe anuncio
        mockMvc.perform(get("/questoes/direito-constitucional"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nenhuma questão publicada foi encontrada")))
                .andExpect(content().string(containsString("Experimente remover filtros")))
                .andExpect(content().string(not(containsString("Publicidade"))))
                .andExpect(content().string(not(containsString("adsbygoogle"))));

        mockMvc.perform(get("/questoes/materia-que-nao-existe"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/questoes/portugues/assunto-que-nao-existe"))
                .andExpect(status().isNotFound());
    }

    @Test
    void apiQuestoesIdsRetornaIdsEmOrdemCrescenteComCursorESubassuntos() throws Exception {
        Questao q1 = novaQuestao(portugues, sintaxe, "Q1", StatusQuestao.PUBLICADA);
        Questao q2 = novaQuestao(direito, null, "Q2", StatusQuestao.PUBLICADA);
        Questao q3 = novaQuestao(portugues, concordancia, "Q3", StatusQuestao.PUBLICADA);
        Questao q4 = novaQuestao(portugues, verboHaver, "Q4", StatusQuestao.PUBLICADA);
        novaQuestao(portugues, sintaxe, "Rascunho", StatusQuestao.RASCUNHO);

        // Paginacao por cursor com limite=2 em /api/questoes/ids?materia=portugues&assunto=sintaxe
        mockMvc.perform(get("/api/questoes/ids")
                        .param("materia", "portugues")
                        .param("assunto", "sintaxe")
                        .param("limite", "2")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(2)))
                .andExpect(jsonPath("$.itens[0]", is(q1.getId())))
                .andExpect(jsonPath("$.itens[1]", is(q3.getId())))
                .andExpect(jsonPath("$.proximoCursor", is(q3.getId())));

        // Continuacao pelo cursor depoisDe=q3.id
        mockMvc.perform(get("/api/questoes/ids")
                        .param("materia", "portugues")
                        .param("assunto", "sintaxe")
                        .param("depoisDe", String.valueOf(q3.getId()))
                        .param("limite", "2")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", is(List.of(q4.getId()))))
                .andExpect(jsonPath("$.proximoCursor", nullValue()));

        // Sem filtro: todas as publicadas em ordem crescente
        mockMvc.perform(get("/api/questoes/ids").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", is(List.of(q1.getId(), q2.getId(), q3.getId(), q4.getId()))))
                .andExpect(jsonPath("$.proximoCursor", nullValue()));
    }

    @Test
    void apiQuestoesResumosRetornaDadosMinimosDasPublicadasOrdenadosPorIdSemGabarito() throws Exception {
        Questao q1 = novaQuestao(portugues, sintaxe, "Enunciado **um** de sintaxe", StatusQuestao.PUBLICADA);
        Questao qRascunho = novaQuestao(portugues, sintaxe, "Rascunho oculto", StatusQuestao.RASCUNHO);
        Questao q2 = novaQuestao(direito, null, "Enunciado dois de direito constitucional", StatusQuestao.PUBLICADA);
        Questao qArquivada = novaQuestao(portugues, concordancia, "Arquivada oculta", StatusQuestao.ARQUIVADA);

        // Passando ids fora de ordem + rascunho + arquivada -> devolve so as publicadas em ordem crescente de id
        String idsParam = q2.getId() + "," + qRascunho.getId() + "," + q1.getId() + "," + qArquivada.getId();
        mockMvc.perform(get("/api/questoes/resumos")
                        .param("ids", idsParam)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is(q1.getId())))
                .andExpect(jsonPath("$[0].slug", is(q1.getSlug())))
                .andExpect(jsonPath("$[0].slugMateria", is("portugues")))
                .andExpect(jsonPath("$[0].nomeMateria", is("Português")))
                .andExpect(jsonPath("$[0].nomeAssunto", is("Sintaxe")))
                .andExpect(jsonPath("$[0].resumoEnunciado", is("Enunciado um de sintaxe")))
                .andExpect(jsonPath("$[1].id", is(q2.getId())))
                .andExpect(jsonPath("$[1].slugMateria", is("direito-constitucional")))
                .andExpect(content().string(not(containsString("correta"))))
                .andExpect(content().string(not(containsString("comentario"))));

        mockMvc.perform(get("/api/questoes/resumos").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void rotasDedicadasDeBancaOrgaoEAnoFiltramOrdenamPorIdEExibemChipsNaInterface() throws Exception {
        Questao q1Fgv = novaQuestaoComProva(
                portugues, sintaxe, provaFgvTce2025Superior, "Questão 1 FGV TCE 2025", TipoQuestao.MULTIPLA_ESCOLHA);
        Questao q2Cebraspe = novaQuestaoComProva(
                direito, null, provaCebraspePrf2024Medio, "Questão 2 CEBRASPE PRF 2024", TipoQuestao.CERTO_ERRADO);
        Questao q3Fgv = novaQuestaoComProva(
                direito, null, provaFgvTce2025Superior, "Questão 3 FGV TCE 2025", TipoQuestao.MULTIPLA_ESCOLHA);

        // /questoes/banca/fgv: apenas q1 e q3 em ordem crescente, com chips de banca, orgao, ano e nivel
        String htmlBanca = mockMvc.perform(get("/questoes/banca/fgv"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Questões da banca FGV")))
                .andExpect(content().string(containsString("aria-label=\"Filtrar por banca\"")))
                .andExpect(content().string(containsString("aria-label=\"Filtrar por órgão\"")))
                .andExpect(content().string(containsString("aria-label=\"Filtrar por ano\"")))
                .andExpect(content().string(containsString("aria-label=\"Filtrar por nível\"")))
                .andExpect(content().string(containsString(
                        "/questao/" + q1Fgv.getId() + "/" + q1Fgv.getSlug() + "?de=banca/fgv")))
                .andExpect(content().string(containsString(
                        "/questao/" + q3Fgv.getId() + "/" + q3Fgv.getSlug() + "?de=banca/fgv")))
                .andExpect(content().string(not(containsString(q2Cebraspe.getEnunciado()))))
                .andReturn().getResponse().getContentAsString();

        assertThat(htmlBanca.indexOf("Questão 1 FGV TCE 2025"))
                .isLessThan(htmlBanca.indexOf("Questão 3 FGV TCE 2025"));

        // /questoes/orgao/prf: apenas q2
        mockMvc.perform(get("/questoes/orgao/prf"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Questões do órgão PRF")))
                .andExpect(content().string(containsString(
                        "/questao/" + q2Cebraspe.getId() + "/" + q2Cebraspe.getSlug() + "?de=orgao/prf")))
                .andExpect(content().string(not(containsString(q1Fgv.getEnunciado()))));

        // /questoes/ano/2025: apenas q1 e q3
        mockMvc.perform(get("/questoes/ano/2025"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Questões de 2025")))
                .andExpect(content().string(containsString(
                        "/questao/" + q1Fgv.getId() + "/" + q1Fgv.getSlug() + "?de=ano/2025")))
                .andExpect(content().string(not(containsString(q2Cebraspe.getEnunciado()))));

        // 404 para banca, orgao ou ano inexistente/invalido
        mockMvc.perform(get("/questoes/banca/banca-inexistente")).andExpect(status().isNotFound());
        mockMvc.perform(get("/questoes/orgao/orgao-inexistente")).andExpect(status().isNotFound());
        mockMvc.perform(get("/questoes/ano/1800")).andExpect(status().isNotFound());
        mockMvc.perform(get("/questoes/ano/nao-numerico")).andExpect(status().isNotFound());
    }

    @Test
    void combinaFiltrosViaQueryStringNaListagemNaApiDeIdsENaNavegacaoDaQuestao() throws Exception {
        Questao q1 = novaQuestaoComProva(
                portugues, sintaxe, provaFgvTce2025Superior, "Q1 Português FGV 2025 Superior", TipoQuestao.MULTIPLA_ESCOLHA);
        Questao q2 = novaQuestaoComProva(
                direito, null, provaFgvTce2025Superior, "Q2 Direito FGV 2025 Superior", TipoQuestao.MULTIPLA_ESCOLHA);
        Questao q3 = novaQuestaoComProva(
                portugues, concordancia, provaCebraspePrf2024Medio, "Q3 Português CEBRASPE 2024 Médio", TipoQuestao.CERTO_ERRADO);
        Questao q4 = novaQuestaoComProva(
                portugues, verboHaver, provaFgvTce2025Superior, "Q4 Português Subassunto FGV 2025 Superior", TipoQuestao.MULTIPLA_ESCOLHA);

        // Combinacao materia=portugues + banca=fgv -> apenas q1 e q4
        mockMvc.perform(get("/questoes")
                        .param("materia", "portugues")
                        .param("banca", "fgv"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(q1.getEnunciado())))
                .andExpect(content().string(containsString(q4.getEnunciado())))
                .andExpect(content().string(not(containsString(q2.getEnunciado()))))
                .andExpect(content().string(not(containsString(q3.getEnunciado()))))
                .andExpect(content().string(containsString("?de=materia=portugues,banca=fgv")));

        // Filtro por nivel=MEDIO e tipo=CERTO_ERRADO -> apenas q3
        mockMvc.perform(get("/questoes")
                        .param("nivel", "MEDIO")
                        .param("tipo", "CERTO_ERRADO"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(q3.getEnunciado())))
                .andExpect(content().string(not(containsString(q1.getEnunciado()))));

        // API /api/questoes/ids com filtros de banca, orgao, ano, nivel e tipo
        mockMvc.perform(get("/api/questoes/ids")
                        .param("materia", "portugues")
                        .param("banca", "fgv")
                        .param("ano", "2025")
                        .param("nivel", "SUPERIOR")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", is(List.of(q1.getId(), q4.getId()))));

        mockMvc.perform(get("/api/questoes/ids")
                        .param("orgao", "prf")
                        .param("tipo", "CERTO_ERRADO")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", is(List.of(q3.getId()))));

        // Navegacao na pagina da questao mantendo ?de=banca/fgv e ?de=materia=portugues,banca=fgv
        mockMvc.perform(get("/questao/" + q1.getId() + "/" + q1.getSlug())
                        .param("de", "materia=portugues,banca=fgv"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "href=\"/questao/" + q4.getId() + "/" + q4.getSlug() + "?de=materia=portugues,banca=fgv\"")))
                .andExpect(content().string(containsString("href=\"/questoes?materia=portugues&amp;banca=fgv\"")));
    }

    @Test
    void aplicaNoindexAbaixoDe10QuestoesOuCom3OuMaisFiltrosEIncluiNoSitemapApenasConteudoSuficiente() throws Exception {
        // Com apenas 5 questoes publicadas para FGV / TCE-GO / 2025 / Portugues (< 10): todas recebem noindex
        for (int i = 1; i <= 5; i++) {
            novaQuestaoComProva(
                    portugues,
                    sintaxe,
                    provaFgvTce2025Superior,
                    "Questão FGV lote inicial " + i,
                    TipoQuestao.MULTIPLA_ESCOLHA);
        }

        mockMvc.perform(get("/questoes/banca/fgv"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<meta name=\"robots\" content=\"noindex\">")));

        mockMvc.perform(get("/questoes/orgao/tce-go"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<meta name=\"robots\" content=\"noindex\">")));

        mockMvc.perform(get("/questoes/ano/2025"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<meta name=\"robots\" content=\"noindex\">")));

        // Sitemap de paginas nao inclui filtros com menos de 10 questoes
        mockMvc.perform(get("/sitemaps/paginas.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<loc>http://localhost:8080/questoes</loc>")))
                .andExpect(content().string(not(containsString("/questoes/banca/fgv"))))
                .andExpect(content().string(not(containsString("/questoes/orgao/tce-go"))))
                .andExpect(content().string(not(containsString("/questoes/ano/2025"))))
                .andExpect(content().string(not(containsString("/questoes/portugues"))));

        // Cadastra mais 5 questoes (total = 10 questoes publicadas >= MINIMO_QUESTOES_INDEXAVEL)
        for (int i = 6; i <= 10; i++) {
            novaQuestaoComProva(
                    portugues,
                    concordancia,
                    provaFgvTce2025Superior,
                    "Questão FGV lote complementar " + i,
                    TipoQuestao.MULTIPLA_ESCOLHA);
        }

        // Com 10 questoes: rotas dedicadas (1 filtro) e combinacoes de ate 2 filtros ficam indexaveis (sem noindex)
        mockMvc.perform(get("/questoes/banca/fgv"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("noindex"))));

        mockMvc.perform(get("/questoes/orgao/tce-go"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("noindex"))));

        mockMvc.perform(get("/questoes/ano/2025"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("noindex"))));

        mockMvc.perform(get("/questoes/portugues"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("noindex"))));

        mockMvc.perform(get("/questoes/portugues/sintaxe"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("noindex"))));

        mockMvc.perform(get("/questoes")
                        .param("banca", "fgv")
                        .param("ano", "2025"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("noindex"))));

        // RF07 RN4: a partir de 3 filtros combinados, SEMPRE recebe noindex mesmo tendo 10+ questoes
        mockMvc.perform(get("/questoes")
                        .param("materia", "portugues")
                        .param("banca", "fgv")
                        .param("ano", "2025"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Questão FGV lote inicial 1")))
                .andExpect(content().string(containsString("<meta name=\"robots\" content=\"noindex\">")));

        mockMvc.perform(get("/questoes")
                        .param("banca", "fgv")
                        .param("orgao", "tce-go")
                        .param("ano", "2025")
                        .param("nivel", "SUPERIOR"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<meta name=\"robots\" content=\"noindex\">")));

        // Sitemap de paginas agora inclui materia, assunto (com subassuntos = 10), banca, orgao e ano com 10+ questoes,
        // mas continua omitindo cebraspe/prf/2024 (0 questoes) e concordancia-verbal isolada (5 questoes < 10)
        mockMvc.perform(get("/sitemaps/paginas.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<loc>http://localhost:8080/questoes/portugues</loc>")))
                .andExpect(content().string(containsString("<loc>http://localhost:8080/questoes/portugues/sintaxe</loc>")))
                .andExpect(content().string(containsString("<loc>http://localhost:8080/questoes/banca/fgv</loc>")))
                .andExpect(content().string(containsString("<loc>http://localhost:8080/questoes/orgao/tce-go</loc>")))
                .andExpect(content().string(containsString("<loc>http://localhost:8080/questoes/ano/2025</loc>")))
                .andExpect(content().string(not(containsString("/questoes/portugues/concordancia-verbal"))))
                .andExpect(content().string(not(containsString("/questoes/banca/cebraspe"))))
                .andExpect(content().string(not(containsString("/questoes/orgao/prf"))));
    }

    private Questao novaQuestao(Materia materia, Assunto assunto, String enunciado, StatusQuestao status) {
        return salvarQuestao(materia, assunto, null, enunciado, TipoQuestao.MULTIPLA_ESCOLHA, status);
    }

    private Questao novaQuestaoComProva(
            Materia materia, Assunto assunto, Prova prova, String enunciado, TipoQuestao tipo) {
        return salvarQuestao(materia, assunto, prova, enunciado, tipo, StatusQuestao.PUBLICADA);
    }

    private Questao salvarQuestao(
            Materia materia, Assunto assunto, Prova prova, String enunciado, TipoQuestao tipo, StatusQuestao status) {
        Questao q = new Questao();
        q.setMateria(materia);
        q.setAssunto(assunto);
        q.setProva(prova);
        q.setAutor(autor);
        q.setTipo(tipo);
        q.setOrigem(prova != null ? OrigemQuestao.PROVA : OrigemQuestao.AUTORAL);
        q.setEnunciado(enunciado);
        q.setSlug("questao-" + System.nanoTime());
        q.setStatus(status);
        if (status != StatusQuestao.RASCUNHO) {
            q.setComentario("Comentário da questão.");
            q.setPublicadoEm(LocalDateTime.now(relogio));
        }
        if (tipo == TipoQuestao.CERTO_ERRADO) {
            q.adicionarAlternativa(new Alternativa("C", "Certo", true));
            q.adicionarAlternativa(new Alternativa("E", "Errado", false));
        } else {
            q.adicionarAlternativa(new Alternativa("A", "Alternativa A", true));
            q.adicionarAlternativa(new Alternativa("B", "Alternativa B", false));
        }
        return questaoRepository.save(q);
    }
}
