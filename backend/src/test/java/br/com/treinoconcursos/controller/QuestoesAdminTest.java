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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cadastro (RF04 RN1-RN5) e ciclo de vida (RF05 RN1-RN5) das questoes pelo painel (US09). */
class QuestoesAdminTest extends IntegracaoTest {

    private static final String ME_CORRETA_B = """
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
        mockMvc.perform(get("/api/admin/questoes")).andExpect(status().isUnauthorized());
    }

    // ---------------- cadastro (RF04) ----------------

    @Test
    void criaRascunhoComAutorESlugDoEnunciado() throws Exception {
        enviar(post("/api/admin/questoes"), questao("AUTORAL", null, "Sobre a **crase**, assinale:", null, ME_CORRETA_B))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/admin/questoes/")))
                .andExpect(jsonPath("$.status").value("RASCUNHO"))
                .andExpect(jsonPath("$.slug").value("sobre-a-crase-assinale"))
                .andExpect(jsonPath("$.nomeAutor").value("Usuário EDITOR"))
                .andExpect(jsonPath("$.comentario").value(nullValue()))
                .andExpect(jsonPath("$.alternativas.length()").value(5))
                .andExpect(jsonPath("$.alternativas[1].correta").value(true));
    }

    /** RF04 RN1 */
    @Test
    void enunciadoVazioDevolve400() throws Exception {
        enviar(post("/api/admin/questoes"), questao("AUTORAL", null, "  ", null, ME_CORRETA_B))
                .andExpect(status().isBadRequest());
    }

    /** RF04 RN2 */
    @Test
    void multiplaEscolhaComDuasCorretasDevolve409() throws Exception {
        String alternativas = ME_CORRETA_B.replace("\"um\", \"correta\": false", "\"um\", \"correta\": true");
        enviar(post("/api/admin/questoes"), questao("AUTORAL", null, "Enunciado", null, alternativas))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Uma questão de múltipla escolha deve ter exatamente uma alternativa correta."));
    }

    /** RF04 RN2 */
    @Test
    void multiplaEscolhaSemCorretaDevolve409() throws Exception {
        String alternativas = ME_CORRETA_B.replace("\"dois\", \"correta\": true", "\"dois\", \"correta\": false");
        enviar(post("/api/admin/questoes"), questao("AUTORAL", null, "Enunciado", null, alternativas))
                .andExpect(status().isConflict());
    }

    /** RF04 RN2 */
    @Test
    void multiplaEscolhaComTresAlternativasOuForaDeSequenciaDevolve409() throws Exception {
        String tres = """
                [{"letra": "A", "texto": "um", "correta": true},
                 {"letra": "B", "texto": "dois", "correta": false},
                 {"letra": "C", "texto": "três", "correta": false}]""";
        String pulandoLetra = """
                [{"letra": "A", "texto": "um", "correta": true},
                 {"letra": "B", "texto": "dois", "correta": false},
                 {"letra": "C", "texto": "três", "correta": false},
                 {"letra": "E", "texto": "cinco", "correta": false}]""";

        for (String alternativas : new String[] {tres, pulandoLetra}) {
            enviar(post("/api/admin/questoes"), questao("AUTORAL", null, "Enunciado", null, alternativas))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value(containsString("4 ou 5 alternativas")));
        }
    }

    /** RF04 RN2: quatro alternativas (A a D) tambem valem. */
    @Test
    void multiplaEscolhaComQuatroAlternativas() throws Exception {
        enviar(post("/api/admin/questoes"), questao("AUTORAL", null, "Enunciado", null, quatroCorreta("D")))
                .andExpect(status().isCreated());
    }

    /** RF04 RN2 */
    @Test
    void alternativaSemTextoDevolve409() throws Exception {
        String alternativas = ME_CORRETA_B.replace("\"três\"", "\" \"");
        enviar(post("/api/admin/questoes"), questao("AUTORAL", null, "Enunciado", null, alternativas))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A alternativa C está sem texto."));
    }

    /** RF04 RN2: Certo/Errado so com C e E, e o texto e fixo. */
    @Test
    void certoErradoGravaTextosFixos() throws Exception {
        String ce = """
                [{"letra": "C", "texto": "", "correta": false}, {"letra": "E", "texto": null, "correta": true}]""";
        enviar(post("/api/admin/questoes"), questao("CERTO_ERRADO", "AUTORAL", null, "Julgue o item.", null, ce))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alternativas[0].texto").value("Certo"))
                .andExpect(jsonPath("$.alternativas[1].texto").value("Errado"))
                .andExpect(jsonPath("$.alternativas[1].correta").value(true));
    }

    /** RF04 RN2 */
    @Test
    void certoErradoComOutrasLetrasDevolve409() throws Exception {
        String ab = """
                [{"letra": "A", "texto": "x", "correta": true}, {"letra": "B", "texto": "y", "correta": false}]""";
        enviar(post("/api/admin/questoes"), questao("CERTO_ERRADO", "AUTORAL", null, "Julgue.", null, ab))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("C (Certo) e E (Errado)")));
    }

    /** RF04 RN3 */
    @Test
    void questaoDeProvaSemProvaDevolve409() throws Exception {
        enviar(post("/api/admin/questoes"), questao("PROVA", null, "Enunciado", null, ME_CORRETA_B))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("Informe a prova")));
    }

    /** RF04 RN3 */
    @Test
    void questaoAutoralComProvaDevolve409() throws Exception {
        int prova = criarProva();
        enviar(post("/api/admin/questoes"), questao("AUTORAL", prova, "Enunciado", null, ME_CORRETA_B))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Uma questão autoral não pode ter prova."));
    }

    /** RF04 RN3 */
    @Test
    void questaoDeProvaMostraAProva() throws Exception {
        int prova = criarProva();
        enviar(post("/api/admin/questoes"), questao("PROVA", prova, "Enunciado", null, ME_CORRETA_B))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idProva").value(prova))
                .andExpect(jsonPath("$.descricaoProva").value("FGV · TCE-GO · 2026 · Analista"));
    }

    @Test
    void assuntoDeOutraMateriaDevolve409() throws Exception {
        int outra = idDe(enviar(post("/api/admin/materias"), "{\"nome\": \"Informática\", \"ativa\": true}"));
        int assunto = idDe(enviar(post("/api/admin/materias/" + outra + "/assuntos"),
                "{\"nome\": \"Redes\", \"idAssuntoPai\": null, \"ativo\": true}"));
        String json = questao("AUTORAL", null, "Enunciado", null, ME_CORRETA_B)
                .replace("\"idAssunto\": null", "\"idAssunto\": " + assunto);

        enviar(post("/api/admin/questoes"), json)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("O assunto escolhido não pertence à matéria da questão."));
    }

    // ---------------- ciclo de vida (RF05) ----------------

    /** RF04 RN1 / RF05 RN1 */
    @Test
    void naoPublicaSemComentario() throws Exception {
        int id = criarQuestao(null);

        enviar(patch("/api/admin/questoes/" + id + "/publicar"), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("O comentário é obrigatório para publicar a questão."));
    }

    /** RF05 RN1 */
    @Test
    void publicaComComentario() throws Exception {
        int id = criarQuestao("Porque sim.");

        enviar(patch("/api/admin/questoes/" + id + "/publicar"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLICADA"))
                .andExpect(jsonPath("$.publicadoEm").value(not(nullValue())));
        enviar(patch("/api/admin/questoes/" + id + "/publicar"), null)
                .andExpect(status().isConflict());
    }

    /** RF05 RN2: editar a publicada nao volta a rascunho, e o comentario nao pode sumir. */
    @Test
    void editarPublicadaMantemStatusEExigeComentario() throws Exception {
        int id = criarPublicada();

        enviar(put("/api/admin/questoes/" + id), questao("AUTORAL", null, "Novo enunciado", "Outro comentário",
                ME_CORRETA_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLICADA"))
                .andExpect(jsonPath("$.enunciado").value("Novo enunciado"))
                // slug de questao ja publicada nao muda
                .andExpect(jsonPath("$.slug").value("enunciado"));

        enviar(put("/api/admin/questoes/" + id), questao("AUTORAL", null, "Novo enunciado", " ", ME_CORRETA_B))
                .andExpect(status().isConflict());
    }

    /** RF05 RN3 */
    @Test
    void arquivaERepublica() throws Exception {
        int id = criarPublicada();

        enviar(patch("/api/admin/questoes/" + id + "/arquivar"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARQUIVADA"));
        enviar(patch("/api/admin/questoes/" + id + "/arquivar"), null).andExpect(status().isConflict());
        enviar(patch("/api/admin/questoes/" + id + "/publicar"), null)
                .andExpect(jsonPath("$.status").value("PUBLICADA"));
    }

    /** RF05 RN4 */
    @Test
    void anulaContinuaPublicadaEPodeDesfazer() throws Exception {
        int id = criarPublicada();

        enviar(patch("/api/admin/questoes/" + id + "/anular"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anulada").value(true))
                .andExpect(jsonPath("$.status").value("PUBLICADA"));
        enviar(patch("/api/admin/questoes/" + id + "/anular?desfazer=true"), null)
                .andExpect(jsonPath("$.anulada").value(false));
    }

    /** RF05 RN5 */
    @Test
    void marcaDesatualizadaContinuaPublicada() throws Exception {
        int id = criarPublicada();

        enviar(patch("/api/admin/questoes/" + id + "/marcar-desatualizada"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desatualizada").value(true))
                .andExpect(jsonPath("$.status").value("PUBLICADA"));
    }

    @Test
    void excluiRascunhoMasNaoQuestaoJaPublicada() throws Exception {
        int rascunho = criarQuestao(null);
        int publicada = criarPublicada();

        enviar(delete("/api/admin/questoes/" + rascunho), null).andExpect(status().isNoContent());
        enviar(delete("/api/admin/questoes/" + publicada), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("Arquive-a")));
    }

    // ---------------- gabarito e estatisticas (RF04 RN5) ----------------

    /** RF04 RN5 */
    @Test
    void trocarGabaritoZeraEstatisticas() throws Exception {
        int id = criarPublicada();
        simularRespostas(id);
        String correta = ME_CORRETA_B.replace("\"dois\", \"correta\": true", "\"dois\", \"correta\": false")
                .replace("\"cinco\", \"correta\": false", "\"cinco\", \"correta\": true");

        enviar(put("/api/admin/questoes/" + id), questao("AUTORAL", null, "Enunciado", "Comentário", correta))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRespostas").value(0))
                .andExpect(jsonPath("$.alternativas[4].correta").value(true));

        assertThat(jdbc.queryForObject("SELECT total_respostas + total_acertos FROM questoes WHERE id_questao = ?",
                Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT sum(total_marcacoes) FROM alternativas WHERE id_questao = ?",
                Integer.class, id)).isZero();
    }

    /** RF04 RN5: corrigir so o texto mantem as estatisticas. */
    @Test
    void corrigirTextoMantemEstatisticas() throws Exception {
        int id = criarPublicada();
        simularRespostas(id);

        enviar(put("/api/admin/questoes/" + id), questao("AUTORAL", null, "Enunciado corrigido", "Comentário",
                ME_CORRETA_B.replace("\"um\"", "\"um (corrigido)\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRespostas").value(10))
                .andExpect(jsonPath("$.alternativas[0].texto").value("um (corrigido)"));
    }

    /** Trocar de 5 para 4 alternativas, mudar a correta e mudar de tipo respeitam as restricoes do banco. */
    @Test
    void trocaAlternativasEDeTipo() throws Exception {
        int id = criarQuestao("Comentário");
        enviar(put("/api/admin/questoes/" + id), questao("AUTORAL", null, "Enunciado", "Comentário", quatroCorreta("A")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alternativas.length()").value(4))
                .andExpect(jsonPath("$.alternativas[0].correta").value(true));

        String ce = """
                [{"letra": "C", "texto": null, "correta": true}, {"letra": "E", "texto": null, "correta": false}]""";
        enviar(put("/api/admin/questoes/" + id), questao("CERTO_ERRADO", "AUTORAL", null, "Julgue.", "C.", ce))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("CERTO_ERRADO"))
                .andExpect(jsonPath("$.alternativas.length()").value(2))
                .andExpect(jsonPath("$.alternativas[0].texto").value("Certo"));

        enviar(put("/api/admin/questoes/" + id), questao("AUTORAL", null, "Enunciado", "Comentário", ME_CORRETA_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alternativas.length()").value(5))
                .andExpect(jsonPath("$.alternativas[2].texto").value("três"));
    }

    // ---------------- listagem e previa ----------------

    @Test
    void listaComFiltros() throws Exception {
        criarQuestao(null);
        criarPublicada();

        mockMvc.perform(get("/api/admin/questoes?status=PUBLICADA&idMateria=" + materia + "&busca=ENUNC")
                        .session(editor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("PUBLICADA"))
                .andExpect(jsonPath("$.content[0].nomeMateria").value("Português"));
        mockMvc.perform(get("/api/admin/questoes?busca=inexistente").session(editor))
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    /** RF04 RN4 */
    @Test
    void previaNaoDeixaPassarScript() throws Exception {
        enviar(post("/api/admin/previa-markdown"), "{\"markdown\": \"**ok** <script>alert(1)</script>\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.html").value(containsString("<strong>ok</strong>")))
                .andExpect(jsonPath("$.html").value(not(containsString("<script"))));
    }

    @Test
    void previaExigeLogin() throws Exception {
        mockMvc.perform(post("/api/admin/previa-markdown").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"markdown\": \"x\"}")).andExpect(status().isUnauthorized());
    }

    // ---------------- apoio ----------------

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

    private String questao(String origem, Integer prova, String enunciado, String comentario, String alternativas) {
        return questao("MULTIPLA_ESCOLHA", origem, prova, enunciado, comentario, alternativas);
    }

    private String questao(String tipo, String origem, Integer prova, String enunciado, String comentario,
            String alternativas) {
        return """
                {"idMateria": %d, "idAssunto": null, "tipo": "%s", "origem": "%s", "idProva": %s,
                 "enunciado": "%s", "comentario": %s, "alternativas": %s}
                """.formatted(materia, tipo, origem, prova, enunciado,
                comentario == null ? "null" : "\"" + comentario + "\"", alternativas);
    }

    private static String quatroCorreta(String letraCorreta) {
        StringBuilder json = new StringBuilder("[");
        for (String letra : new String[] {"A", "B", "C", "D"}) {
            json.append(json.length() > 1 ? "," : "").append("{\"letra\": \"").append(letra)
                    .append("\", \"texto\": \"texto ").append(letra).append("\", \"correta\": ")
                    .append(letra.equals(letraCorreta)).append("}");
        }
        return json.append("]").toString();
    }

    private int criarQuestao(String comentario) throws Exception {
        return idDe(enviar(post("/api/admin/questoes"),
                questao("AUTORAL", null, "Enunciado", comentario, ME_CORRETA_B)).andExpect(status().isCreated()));
    }

    private int criarPublicada() throws Exception {
        int id = criarQuestao("Comentário");
        enviar(patch("/api/admin/questoes/" + id + "/publicar"), null).andExpect(status().isOk());
        return id;
    }

    private int criarProva() throws Exception {
        int banca = idDe(enviar(post("/api/admin/bancas"),
                "{\"nome\": \"Fundação Getulio Vargas\", \"sigla\": \"FGV\"}"));
        int orgao = idDe(enviar(post("/api/admin/orgaos"),
                "{\"nome\": \"Tribunal de Contas\", \"sigla\": \"TCE-GO\", \"esfera\": \"ESTADUAL\", \"uf\": \"GO\"}"));
        return idDe(enviar(post("/api/admin/provas"), "{\"idBanca\": " + banca + ", \"idOrgao\": " + orgao
                + ", \"ano\": 2026, \"cargo\": \"Analista\", \"nivel\": \"SUPERIOR\"}"));
    }

    /** Ainda nao ha API de respostas (RF17, issue #13): grava os contadores direto. */
    private void simularRespostas(int id) {
        jdbc.update("UPDATE questoes SET total_respostas = 10, total_acertos = 6 WHERE id_questao = ?", id);
        jdbc.update("UPDATE alternativas SET total_marcacoes = 2 WHERE id_questao = ?", id);
    }
}
