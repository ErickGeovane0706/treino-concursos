package br.com.treinoconcursos.controller;

import br.com.treinoconcursos.IntegracaoTest;
import br.com.treinoconcursos.model.Perfil;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Paginas publicas do modo simulado (RF13 RN1-RN5, RF14 RN4, US05). */
class SimuladoPublicoTest extends IntegracaoTest {

    private static final String NOINDEX = "<meta name=\"robots\" content=\"noindex\">";

    private MockHttpSession editor;
    private int portugues;
    private int matematica;

    @BeforeEach
    void preparar() throws Exception {
        criarUsuario("editor@exemplo.com", Perfil.EDITOR);
        editor = login("editor@exemplo.com");
        portugues = idDe(enviar(post("/api/admin/materias"), "{\"nome\": \"Português\", \"ativa\": true}"));
        matematica = idDe(enviar(post("/api/admin/materias"), "{\"nome\": \"Matemática\", \"ativa\": true}"));
    }

    @Test
    void listaSoPublicadosEIndexa() throws Exception {
        mockMvc.perform(get("/simulados"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString(NOINDEX),
                        containsString("Ainda não há simulados publicados"), not(containsString("Publicidade")))));

        publicado("Simulado de Português nº 1", 30);
        enviar(post("/api/admin/simulados"), simulado("Rascunho escondido", null, publicadas(portugues, 2)));

        mockMvc.perform(get("/simulados"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("public")))
                .andExpect(content().string(allOf(
                        not(containsString(NOINDEX)),
                        containsString("href=\"/simulado/simulado-de-portugues-n-1\""),
                        containsString("5 questões"),
                        containsString("30 min sugeridos"),
                        not(containsString("Rascunho escondido")))));
    }

    @Test
    void apresentacaoIndexavelComMateriasEIds() throws Exception {
        List<Integer> ids = new ArrayList<>(publicadas(portugues, 3));
        ids.addAll(publicadas(matematica, 2));
        String slug = publicar(enviarSimulado("Misto", 45, ids));

        mockMvc.perform(get("/simulado/" + slug))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        not(containsString(NOINDEX)),
                        containsString("<h1>Misto</h1>"),
                        containsString("Português (3)"),
                        containsString("Matemática (2)"),
                        containsString("Tempo sugerido: 45 min"),
                        containsString("data-ids=\"" + ids.stream().map(String::valueOf)
                                .collect(Collectors.joining(",")) + "\""),
                        containsString("href=\"/simulado/" + slug + "/1\""))));
    }

    /** RF13 RN1-RN2: uma pagina por questao, noindex, sem gabarito nem comentario no HTML. */
    @Test
    void questaoNaoTrazCorrecaoETemNoindex() throws Exception {
        List<Integer> ids = publicadas(portugues, 5);
        String slug = publicar(enviarSimulado("Simulado", 20, ids));

        String html = mockMvc.perform(get("/simulado/" + slug + "/1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains(NOINDEX)
                .contains("Questão 1 de 5")
                .contains("data-ilha=\"simulado-questao\"")
                .contains("data-id=\"" + ids.get(0) + "\"")
                .contains("data-tempo=\"20\"")
                .contains("href=\"/simulado/" + slug + "/2\"")
                .doesNotContain("data-correta")
                .doesNotContain("Gabarito")
                .doesNotContain("Comentário da questão")
                .doesNotContain("Questão anterior");

        mockMvc.perform(get("/simulado/" + slug + "/5"))
                .andExpect(content().string(allOf(
                        containsString("Finalizar simulado"),
                        containsString("href=\"/simulado/" + slug + "/resultado\""),
                        containsString("href=\"/simulado/" + slug + "/4\""),
                        not(containsString("Próxima questão")))));
    }

    @Test
    void numeroForaDoIntervaloOuSimuladoInexistenteDevolve404() throws Exception {
        String slug = publicado("Simulado", null);
        mockMvc.perform(get("/simulado/" + slug + "/0")).andExpect(status().isNotFound());
        mockMvc.perform(get("/simulado/" + slug + "/6")).andExpect(status().isNotFound());
        mockMvc.perform(get("/simulado/nao-existe")).andExpect(status().isNotFound());
    }

    /** RF14 RN3: rascunho nao aparece; arquivado responde 410. */
    @Test
    void rascunhoDevolve404EArquivado410() throws Exception {
        int rascunho = enviarSimulado("Rascunho", null, publicadas(portugues, 5));
        mockMvc.perform(get("/simulado/rascunho")).andExpect(status().isNotFound());

        enviar(patch("/api/admin/simulados/" + rascunho + "/publicar"), null).andExpect(status().isOk());
        enviar(patch("/api/admin/simulados/" + rascunho + "/arquivar"), null).andExpect(status().isOk());
        mockMvc.perform(get("/simulado/rascunho")).andExpect(status().isGone());
        mockMvc.perform(get("/simulado/rascunho/1")).andExpect(status().isGone());
    }

    /** RF14 RN4: a questao anulada deixa de aparecer e as outras sao renumeradas. */
    @Test
    void questaoQueDeixouDeSerElegivelNaoAparece() throws Exception {
        List<Integer> ids = publicadas(portugues, 5);
        String slug = publicar(enviarSimulado("Simulado", null, ids));
        enviar(patch("/api/admin/questoes/" + ids.get(0) + "/anular"), null).andExpect(status().isOk());

        mockMvc.perform(get("/simulado/" + slug + "/1"))
                .andExpect(content().string(allOf(
                        containsString("Questão 1 de 4"), containsString("data-id=\"" + ids.get(1) + "\""))));
        mockMvc.perform(get("/simulado/" + slug + "/5")).andExpect(status().isNotFound());
        mockMvc.perform(get("/simulado/" + slug + "/resultado"))
                .andExpect(content().string(not(containsString("data-id=\"" + ids.get(0) + "\""))));
    }

    /** RF13 RN3: resultado com noindex e gabarito gratuito de cada questao, para a ilha calcular. */
    @Test
    void resultadoTemGabaritoDeCadaQuestao() throws Exception {
        List<Integer> ids = new ArrayList<>(publicadas(portugues, 4));
        ids.addAll(publicadas(matematica, 1));
        String slug = publicar(enviarSimulado("Simulado", null, ids));

        String html = mockMvc.perform(get("/simulado/" + slug + "/resultado"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains(NOINDEX)
                .contains("data-ilha=\"simulado-resultado\"")
                .contains("data-id=\"" + ids.get(4) + "\"")
                .contains("data-materia=\"Matemática\"")
                .contains("data-correta=\"B\"")
                .contains("Gabarito: <b>B</b>")
                .doesNotContain("Comentário da questão");
        assertThat(html.split("data-correta=").length - 1).isEqualTo(5);
    }

    @Test
    void sitemapEMenuTemOsSimulados() throws Exception {
        String slug = publicado("Simulado de Português", null);
        mockMvc.perform(get("/sitemaps/paginas.xml"))
                .andExpect(content().string(allOf(
                        containsString("<loc>http://localhost:8080/simulados</loc>"),
                        containsString("<loc>http://localhost:8080/simulado/" + slug + "</loc>"),
                        not(containsString("/simulado/" + slug + "/1")))));
        mockMvc.perform(get("/sobre")).andExpect(content().string(containsString("href=\"/simulados\"")));
    }

    // ---------------- apoio ----------------

    private String publicado(String titulo, Integer tempo) throws Exception {
        return publicar(enviarSimulado(titulo, tempo, publicadas(portugues, 5)));
    }

    private String publicar(int idSimulado) throws Exception {
        String json = enviar(patch("/api/admin/simulados/" + idSimulado + "/publicar"), null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.slug");
    }

    private int enviarSimulado(String titulo, Integer tempo, List<Integer> ids) throws Exception {
        return idDe(enviar(post("/api/admin/simulados"), simulado(titulo, tempo, ids)));
    }

    private List<Integer> publicadas(int materia, int quantidade) throws Exception {
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < quantidade; i++) {
            String json = """
                    {"idMateria": %d, "idAssunto": null, "tipo": "MULTIPLA_ESCOLHA", "origem": "AUTORAL",
                     "idProva": null, "enunciado": "Enunciado %d sobre **crase**",
                     "comentario": "Comentário da questão",
                     "alternativas": [{"letra": "A", "texto": "um", "correta": false},
                                      {"letra": "B", "texto": "dois", "correta": true},
                                      {"letra": "C", "texto": "três", "correta": false},
                                      {"letra": "D", "texto": "quatro", "correta": false}]}"""
                    .formatted(materia, i);
            int id = idDe(enviar(post("/api/admin/questoes"), json));
            enviar(patch("/api/admin/questoes/" + id + "/publicar"), null).andExpect(status().isOk());
            ids.add(id);
        }
        return ids;
    }

    private static String simulado(String titulo, Integer tempo, List<Integer> ids) {
        return """
                {"titulo": "%s", "slug": null, "descricao": null, "tempoSugeridoMin": %s, "idsQuestoes": [%s]}"""
                .formatted(titulo, tempo, ids.stream().map(String::valueOf).collect(Collectors.joining(",")));
    }

    private ResultActions enviar(MockHttpServletRequestBuilder req, String json) throws Exception {
        req = req.session(editor).with(csrf());
        if (json != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(req);
    }

    private static int idDe(ResultActions resultado) throws Exception {
        return JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), "$.id");
    }
}
