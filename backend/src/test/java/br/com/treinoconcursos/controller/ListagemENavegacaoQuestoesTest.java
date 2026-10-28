package br.com.treinoconcursos.controller;

import br.com.treinoconcursos.IntegracaoTest;
import br.com.treinoconcursos.model.Alternativa;
import br.com.treinoconcursos.model.Assunto;
import br.com.treinoconcursos.model.Materia;
import br.com.treinoconcursos.model.OrigemQuestao;
import br.com.treinoconcursos.model.Perfil;
import br.com.treinoconcursos.model.Questao;
import br.com.treinoconcursos.model.StatusQuestao;
import br.com.treinoconcursos.model.TipoQuestao;
import br.com.treinoconcursos.model.Usuario;
import br.com.treinoconcursos.repository.AssuntoRepository;
import br.com.treinoconcursos.repository.MateriaRepository;
import br.com.treinoconcursos.repository.QuestaoRepository;
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
    private QuestaoRepository questaoRepository;

    private Usuario autor;
    private Materia portugues;
    private Materia direito;
    private Assunto sintaxe;
    private Assunto concordancia;
    private Assunto verboHaver;

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

    private Questao novaQuestao(Materia materia, Assunto assunto, String enunciado, StatusQuestao status) {
        Questao q = new Questao();
        q.setMateria(materia);
        q.setAssunto(assunto);
        q.setAutor(autor);
        q.setTipo(TipoQuestao.MULTIPLA_ESCOLHA);
        q.setOrigem(OrigemQuestao.AUTORAL);
        q.setEnunciado(enunciado);
        q.setSlug("questao-" + System.nanoTime());
        q.setStatus(status);
        if (status != StatusQuestao.RASCUNHO) {
            q.setComentario("Comentário da questão.");
            q.setPublicadoEm(LocalDateTime.now(relogio));
        }
        q.adicionarAlternativa(new Alternativa("A", "Alternativa A", true));
        q.adicionarAlternativa(new Alternativa("B", "Alternativa B", false));
        return questaoRepository.save(q);
    }
}
