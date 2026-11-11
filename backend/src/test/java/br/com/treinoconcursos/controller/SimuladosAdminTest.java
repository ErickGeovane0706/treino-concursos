package br.com.treinoconcursos.controller;

import br.com.treinoconcursos.IntegracaoTest;
import br.com.treinoconcursos.model.Perfil;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Montagem de simulados no painel (RF14 RN1-RN4, US11). */
class SimuladosAdminTest extends IntegracaoTest {

    private static final String ALTERNATIVAS = """
            [{"letra": "A", "texto": "um", "correta": false},
             {"letra": "B", "texto": "dois", "correta": true},
             {"letra": "C", "texto": "três", "correta": false},
             {"letra": "D", "texto": "quatro", "correta": false},
             {"letra": "E", "texto": "cinco", "correta": false}]""";

    private MockHttpSession editor;
    private int materia;

    @BeforeEach
    void preparar() throws Exception {
        criarUsuario("editor@exemplo.com", Perfil.EDITOR);
        editor = login("editor@exemplo.com");
        materia = idDe(enviar(post("/api/admin/materias"), "{\"nome\": \"Português\", \"ativa\": true}"));
    }

    @Test
    void semLoginNaoAcessa() throws Exception {
        mockMvc.perform(get("/api/admin/simulados")).andExpect(status().isUnauthorized());
    }

    @Test
    void criaRascunhoComSlugDoTituloEQuestoesNaOrdem() throws Exception {
        List<Integer> q = publicadas(3);
        List<Integer> ordem = List.of(q.get(2), q.get(0), q.get(1));

        enviar(post("/api/admin/simulados"), simulado("Simulado de Português – FGV nº 1", null, 60, ordem))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/admin/simulados/")))
                .andExpect(jsonPath("$.status").value("RASCUNHO"))
                .andExpect(jsonPath("$.slug").value("simulado-de-portugues-fgv-n-1"))
                .andExpect(jsonPath("$.tempoSugeridoMin").value(60))
                .andExpect(jsonPath("$.nomeAutor").value("Usuário EDITOR"))
                .andExpect(jsonPath("$.precisaRevisao").value(false))
                .andExpect(jsonPath("$.questoes.length()").value(3))
                .andExpect(jsonPath("$.questoes[0].id").value(q.get(2)))
                .andExpect(jsonPath("$.questoes[0].ordem").value(1))
                .andExpect(jsonPath("$.questoes[2].id").value(q.get(1)))
                .andExpect(jsonPath("$.questoes[2].elegivel").value(true));
    }

    /** RF14 RN1: titulo obrigatorio. */
    @Test
    void semTituloDevolve400() throws Exception {
        enviar(post("/api/admin/simulados"), simulado(" ", null, null, List.of()))
                .andExpect(status().isBadRequest());
    }

    /** RF14 RN1: slug unico. */
    @Test
    void slugRepetidoDevolve409() throws Exception {
        enviar(post("/api/admin/simulados"), simulado("Simulado A", "meu-simulado", null, List.of()))
                .andExpect(status().isCreated());
        enviar(post("/api/admin/simulados"), simulado("Simulado B", "meu-simulado", null, List.of()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("meu-simulado")));
    }

    /** RF14 RN1: tempo sugerido, se informado, e positivo. */
    @Test
    void tempoSugeridoZeroDevolve400() throws Exception {
        enviar(post("/api/admin/simulados"), simulado("Simulado", null, 0, List.of()))
                .andExpect(status().isBadRequest());
    }

    /** RF14 RN2 */
    @Test
    void questaoRepetidaDevolve409() throws Exception {
        List<Integer> q = publicadas(2);
        enviar(post("/api/admin/simulados"), simulado("Simulado", null, null, List.of(q.get(0), q.get(1), q.get(0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("A questão #" + q.get(0) + " aparece mais de uma vez no simulado."));
    }

    /** RF14 RN2: o banco tambem impede, pela chave primaria. */
    @Test
    void bancoImpedeQuestaoRepetida() throws Exception {
        int q = publicadas(1).get(0);
        int id = idDe(enviar(post("/api/admin/simulados"), simulado("Simulado", null, null, List.of(q))));
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO simulado_questoes (id_simulado, id_questao, ordem) VALUES (?, ?, 2)", id, q))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** RF14 RN1: so questoes publicadas, nao anuladas e nao desatualizadas. */
    @Test
    void questaoNaoElegivelDevolve409ComOMotivo() throws Exception {
        int rascunho = criarQuestao();
        int anulada = publicadas(1).get(0);
        enviar(patch("/api/admin/questoes/" + anulada + "/anular"), null).andExpect(status().isOk());
        int desatualizada = publicadas(1).get(0);
        enviar(patch("/api/admin/questoes/" + desatualizada + "/marcar-desatualizada"), null)
                .andExpect(status().isOk());
        int arquivada = publicadas(1).get(0);
        enviar(patch("/api/admin/questoes/" + arquivada + "/arquivar"), null).andExpect(status().isOk());

        verificarInelegivel(rascunho, "ainda não foi publicada");
        verificarInelegivel(anulada, "foi anulada pela banca");
        verificarInelegivel(desatualizada, "está desatualizada");
        verificarInelegivel(arquivada, "está arquivada");
        enviar(post("/api/admin/simulados"), simulado("Simulado", null, null, List.of(9999)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A questão #9999 não existe."));
    }

    /** RF14 RN1: no maximo 100 questoes. */
    @Test
    void maisDeCemQuestoesDevolve409() throws Exception {
        List<Integer> ids = new ArrayList<>(publicadas(100));
        ids.add(publicadas(1).get(0));
        enviar(post("/api/admin/simulados"), simulado("Simulado", null, null, ids))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("O simulado pode ter no máximo 100 questões."));
    }

    /** RF14 RN1 e RN3: publicar exige 5 questoes; o rascunho pode ter menos enquanto e montado. */
    @Test
    void publicarExigeCincoQuestoes() throws Exception {
        List<Integer> q = publicadas(5);
        int id = idDe(enviar(post("/api/admin/simulados"), simulado("Simulado", null, null, q.subList(0, 4))));

        enviar(patch("/api/admin/simulados/" + id + "/publicar"), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("pelo menos 5 questões")));

        enviar(put("/api/admin/simulados/" + id), simulado("Simulado", null, null, q)).andExpect(status().isOk());
        enviar(patch("/api/admin/simulados/" + id + "/publicar"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLICADO"))
                .andExpect(jsonPath("$.publicadoEm").isNotEmpty());
        enviar(patch("/api/admin/simulados/" + id + "/publicar"), null).andExpect(status().isConflict());
    }

    /** RF14 RN1: um simulado publicado nao pode ficar com menos de 5 questoes. */
    @Test
    void publicadoNaoPodeFicarComMenosDeCinco() throws Exception {
        List<Integer> q = publicadas(5);
        int id = publicado(q);
        enviar(put("/api/admin/simulados/" + id), simulado("Simulado", null, null, q.subList(0, 4)))
                .andExpect(status().isConflict());
    }

    /** RF14 RN3: arquivar, republicar, e o slug fica fixo depois da primeira publicacao. */
    @Test
    void cicloDeVidaEExclusao() throws Exception {
        int id = publicado(publicadas(5));

        enviar(delete("/api/admin/simulados/" + id), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("Arquive-o")));
        enviar(put("/api/admin/simulados/" + id), simulado("Simulado", "outro-endereco", null, idsDe(id)))
                .andExpect(status().isConflict());

        enviar(patch("/api/admin/simulados/" + id + "/arquivar"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARQUIVADO"));
        enviar(patch("/api/admin/simulados/" + id + "/arquivar"), null).andExpect(status().isConflict());
        enviar(patch("/api/admin/simulados/" + id + "/publicar"), null)
                .andExpect(jsonPath("$.status").value("PUBLICADO"));

        int rascunho = idDe(enviar(post("/api/admin/simulados"), simulado("Rascunho", null, null, publicadas(2))));
        enviar(delete("/api/admin/simulados/" + rascunho), null).andExpect(status().isNoContent());
        enviar(get("/api/admin/simulados/" + rascunho), null).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM simulado_questoes WHERE id_simulado = ?",
                Integer.class, rascunho)).isZero();
    }

    /** Reordenar (arrastar no painel) grava a nova ordem. */
    @Test
    void reordenarGravaANovaOrdem() throws Exception {
        List<Integer> q = publicadas(5);
        int id = publicado(q);
        List<Integer> invertida = new ArrayList<>(q);
        java.util.Collections.reverse(invertida);

        enviar(put("/api/admin/simulados/" + id), simulado("Simulado", null, null, invertida))
                .andExpect(status().isOk());

        assertThat(idsDe(id)).containsExactlyElementsOf(invertida);
    }

    /** RF14 RN4: arquivar, anular ou desatualizar uma questao sinaliza o simulado publicado. */
    @Test
    void questaoQueDeixaDeSerElegivelSinalizaRevisao() throws Exception {
        for (String acao : List.of("arquivar", "anular", "marcar-desatualizada")) {
            List<Integer> q = publicadas(5);
            int publicado = publicado(q);
            int rascunho = idDe(enviar(post("/api/admin/simulados"),
                    simulado("Rascunho " + acao, null, null, q)));

            enviar(patch("/api/admin/questoes/" + q.get(2) + "/" + acao), null).andExpect(status().isOk());

            enviar(get("/api/admin/simulados/" + publicado), null)
                    .andExpect(jsonPath("$.precisaRevisao").value(true))
                    .andExpect(jsonPath("$.questoes[2].elegivel").value(false));
            enviar(get("/api/admin/simulados/" + rascunho), null)
                    .andExpect(jsonPath("$.precisaRevisao").value(false));
            enviar(get("/api/admin/simulados?status=PUBLICADO"), null)
                    .andExpect(jsonPath("$.content[0].precisaRevisao").value(true));
        }
    }

    /** RF14 RN4: desfazer a anulacao nao sinaliza; trocar a questao limpa a sinalizacao. */
    @Test
    void revisarOSimuladoLimpaASinalizacao() throws Exception {
        List<Integer> q = publicadas(6);
        int id = publicado(q.subList(0, 5));
        enviar(patch("/api/admin/questoes/" + q.get(0) + "/anular"), null).andExpect(status().isOk());

        List<Integer> revisada = new ArrayList<>(q.subList(1, 6));
        enviar(put("/api/admin/simulados/" + id), simulado("Simulado", null, null, revisada))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precisaRevisao").value(false));

        enviar(patch("/api/admin/questoes/" + q.get(0) + "/anular?desfazer=true"), null).andExpect(status().isOk());
        enviar(get("/api/admin/simulados/" + id), null).andExpect(jsonPath("$.precisaRevisao").value(false));
    }

    @Test
    void listaComFiltroEContagemDeQuestoes() throws Exception {
        publicado(publicadas(5));
        enviar(post("/api/admin/simulados"), simulado("Rascunho de Direito", null, null, publicadas(2)));

        enviar(get("/api/admin/simulados?busca=direito"), null)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].titulo").value("Rascunho de Direito"))
                .andExpect(jsonPath("$.content[0].totalQuestoes").value(2))
                .andExpect(jsonPath("$.content[0].status").value("RASCUNHO"));
        enviar(get("/api/admin/simulados?status=PUBLICADO"), null)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].totalQuestoes").value(5));
    }

    // ---------------- apoio ----------------

    private void verificarInelegivel(int idQuestao, String motivo) throws Exception {
        enviar(post("/api/admin/simulados"), simulado("Simulado", null, null, List.of(idQuestao)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("A questão #" + idQuestao + " não pode entrar no simulado: " + motivo + "."));
    }

    private int publicado(List<Integer> questoes) throws Exception {
        int id = idDe(enviar(post("/api/admin/simulados"),
                simulado("Simulado " + System.nanoTime(), null, null, questoes)));
        enviar(patch("/api/admin/simulados/" + id + "/publicar"), null).andExpect(status().isOk());
        return id;
    }

    private List<Integer> idsDe(int idSimulado) throws Exception {
        String json = enviar(get("/api/admin/simulados/" + idSimulado), null)
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.questoes[*].id");
    }

    private List<Integer> publicadas(int quantidade) throws Exception {
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < quantidade; i++) {
            int id = criarQuestao();
            enviar(patch("/api/admin/questoes/" + id + "/publicar"), null).andExpect(status().isOk());
            ids.add(id);
        }
        return ids;
    }

    private int criarQuestao() throws Exception {
        String json = """
                {"idMateria": %d, "idAssunto": null, "tipo": "MULTIPLA_ESCOLHA", "origem": "AUTORAL",
                 "idProva": null, "enunciado": "Enunciado", "comentario": "Comentário", "alternativas": %s}"""
                .formatted(materia, ALTERNATIVAS);
        return idDe(enviar(post("/api/admin/questoes"), json));
    }

    private static String simulado(String titulo, String slug, Integer tempo, List<Integer> ids) {
        return """
                {"titulo": "%s", "slug": %s, "descricao": "Apresentação", "tempoSugeridoMin": %s,
                 "idsQuestoes": [%s]}"""
                .formatted(titulo, slug == null ? "null" : "\"" + slug + "\"", tempo,
                        ids.stream().map(String::valueOf).collect(Collectors.joining(",")));
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
