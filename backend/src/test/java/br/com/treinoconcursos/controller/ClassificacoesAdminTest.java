package br.com.treinoconcursos.controller;

import br.com.treinoconcursos.IntegracaoTest;
import br.com.treinoconcursos.model.Perfil;
import br.com.treinoconcursos.model.Usuario;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Manutencao das classificacoes pelo painel (US10, RF02 RN1-RN4, RF03 RN1-RN3).
 */
class ClassificacoesAdminTest extends IntegracaoTest {

    private MockHttpSession editor;
    private Usuario autor;

    @BeforeEach
    void entrarComoEditor() throws Exception {
        autor = criarUsuario("editor@exemplo.com", Perfil.EDITOR);
        editor = login("editor@exemplo.com");
    }

    // ---------------- materias (RF02) ----------------

    @Test
    void semLoginNaoAcessa() throws Exception {
        mockMvc.perform(get("/api/admin/materias")).andExpect(status().isUnauthorized());
    }

    /** RF02 RN4: slug gerado do nome. */
    @Test
    void criaMateriaComSlugGeradoDoNome() throws Exception {
        enviar(post("/api/admin/materias"), "{\"nome\": \"Direito Constitucional\", \"ativa\": true}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("direito-constitucional"))
                .andExpect(jsonPath("$.ativa").value(true));
    }

    /** RF02 RN1 */
    @Test
    void nomeDeMateriaRepetidoDevolve409() throws Exception {
        criarMateria("Português");

        enviar(post("/api/admin/materias"), "{\"nome\": \"PORTUGUÊS\", \"slug\": \"outro\", \"ativa\": true}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Já existe uma matéria com este nome."));
    }

    /** RF02 RN1 */
    @Test
    void slugDeMateriaRepetidoDevolve409() throws Exception {
        criarMateria("Português");

        enviar(post("/api/admin/materias"), "{\"nome\": \"Língua\", \"slug\": \"Português\", \"ativa\": true}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("\"portugues\"")));
    }

    @Test
    void listaComBusca() throws Exception {
        criarMateria("Português");
        criarMateria("Informática");

        mockMvc.perform(get("/api/admin/materias?busca=inform").session(editor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].nome").value("Informática"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void alterarNomeSemInformarSlugMantemOSlug() throws Exception {
        int id = criarMateria("Português");

        enviar(put("/api/admin/materias/" + id), "{\"nome\": \"Língua Portuguesa\", \"ativa\": false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Língua Portuguesa"))
                .andExpect(jsonPath("$.slug").value("portugues"))
                .andExpect(jsonPath("$.ativa").value(false));
    }

    @Test
    void trocaOSlugQuandoNaoHaQuestaoPublicada() throws Exception {
        int id = criarMateria("Português");
        inserirQuestao(id, null, null, false);

        enviar(put("/api/admin/materias/" + id), "{\"nome\": \"Português\", \"slug\": \"lingua-portuguesa\", \"ativa\": true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("lingua-portuguesa"));
    }

    /** RF02 RN4: com questao publicada o slug fica congelado. */
    @Test
    void naoTrocaOSlugComQuestaoPublicada() throws Exception {
        int id = criarMateria("Português");
        inserirQuestao(id, null, null, true);

        enviar(put("/api/admin/materias/" + id), "{\"nome\": \"Português\", \"slug\": \"lingua-portuguesa\", \"ativa\": true}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("não pode mudar")));
    }

    /** RF02 RN3 */
    @Test
    void naoExcluiMateriaComQuestao() throws Exception {
        int id = criarMateria("Português");
        inserirQuestao(id, null, null, false);

        mockMvc.perform(delete("/api/admin/materias/" + id).session(editor).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A matéria tem questões e não pode ser excluída. Inative-a."));
    }

    @Test
    void excluiMateriaSemQuestaoJuntoComOsAssuntos() throws Exception {
        int id = criarMateria("Português");
        int pai = criarAssunto(id, "Crase", null);
        criarAssunto(id, "Crase facultativa", pai);

        mockMvc.perform(delete("/api/admin/materias/" + id).session(editor).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/admin/materias/" + id).session(editor)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM assuntos", Integer.class)).isZero();
    }

    // ---------------- assuntos (RF02 RN2) ----------------

    @Test
    void criaAssuntosAteTresNiveis() throws Exception {
        int materia = criarMateria("Português");
        int n1 = criarAssunto(materia, "Concordância", null);
        int n2 = criarAssunto(materia, "Concordância verbal", n1);
        criarAssunto(materia, "Verbo haver", n2);

        mockMvc.perform(get("/api/admin/materias/" + materia + "/assuntos").session(editor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.slug == 'verbo-haver')].nivel").value(3))
                .andExpect(jsonPath("$[?(@.slug == 'concordancia')].idAssuntoPai").value(contains(nullValue())));
    }

    /** RF02 RN2: no maximo 3 niveis. */
    @Test
    void recusaQuartoNivel() throws Exception {
        int materia = criarMateria("Português");
        int n3 = criarAssunto(materia, "C", criarAssunto(materia, "B", criarAssunto(materia, "A", null)));

        enviar(post("/api/admin/materias/" + materia + "/assuntos"),
                "{\"nome\": \"D\", \"idAssuntoPai\": " + n3 + ", \"ativo\": true}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Os assuntos podem ter no máximo 3 níveis."));
    }

    /** RF02 RN2: mover um assunto com filhos tambem respeita os 3 niveis. */
    @Test
    void recusaMoverSubarvoreQueUltrapassaTresNiveis() throws Exception {
        int materia = criarMateria("Português");
        int a = criarAssunto(materia, "A", null);
        int b = criarAssunto(materia, "B", a);
        int x = criarAssunto(materia, "X", null);
        criarAssunto(materia, "Y", x);

        enviar(put("/api/admin/materias/" + materia + "/assuntos/" + x),
                "{\"nome\": \"X\", \"idAssuntoPai\": " + b + ", \"ativo\": true}")
                .andExpect(status().isConflict());
    }

    /** RF02 RN2: o pai precisa ser da mesma materia. */
    @Test
    void recusaPaiDeOutraMateria() throws Exception {
        int portugues = criarMateria("Português");
        int outra = criarMateria("Informática");
        int paiDeOutra = criarAssunto(outra, "Planilhas", null);

        enviar(post("/api/admin/materias/" + portugues + "/assuntos"),
                "{\"nome\": \"Crase\", \"idAssuntoPai\": " + paiDeOutra + ", \"ativo\": true}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("O assunto pai deve ser da mesma matéria."));
    }

    @Test
    void recusaCiclo() throws Exception {
        int materia = criarMateria("Português");
        int a = criarAssunto(materia, "A", null);
        int b = criarAssunto(materia, "B", a);

        enviar(put("/api/admin/materias/" + materia + "/assuntos/" + a),
                "{\"nome\": \"A\", \"idAssuntoPai\": " + b + ", \"ativo\": true}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("dentro dele mesmo")));
    }

    @Test
    void slugDeAssuntoRepetidoNaMateriaDevolve409() throws Exception {
        int materia = criarMateria("Português");
        criarAssunto(materia, "Crase", null);

        enviar(post("/api/admin/materias/" + materia + "/assuntos"), "{\"nome\": \"CRASE\", \"ativo\": true}")
                .andExpect(status().isConflict());
    }

    @Test
    void assuntoDeOutraMateriaDevolve404() throws Exception {
        int portugues = criarMateria("Português");
        int outra = criarMateria("Informática");
        int assunto = criarAssunto(outra, "Planilhas", null);

        mockMvc.perform(get("/api/admin/materias/" + portugues + "/assuntos/" + assunto).session(editor))
                .andExpect(status().isNotFound());
    }

    /** RF02 RN3 */
    @Test
    void naoExcluiAssuntoComQuestaoNemComSubassunto() throws Exception {
        int materia = criarMateria("Português");
        int comQuestao = criarAssunto(materia, "Crase", null);
        inserirQuestao(materia, comQuestao, null, false);
        int comFilho = criarAssunto(materia, "Concordância", null);
        criarAssunto(materia, "Verbal", comFilho);

        mockMvc.perform(delete("/api/admin/materias/" + materia + "/assuntos/" + comQuestao).session(editor).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("O assunto tem questões e não pode ser excluído. Inative-o."));
        mockMvc.perform(delete("/api/admin/materias/" + materia + "/assuntos/" + comFilho).session(editor).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("O assunto tem subassuntos. Exclua-os ou mova-os antes."));
    }

    /** RF02 RN4 para assunto. */
    @Test
    void naoTrocaSlugDeAssuntoComQuestaoPublicada() throws Exception {
        int materia = criarMateria("Português");
        int assunto = criarAssunto(materia, "Crase", null);
        inserirQuestao(materia, assunto, null, true);

        enviar(put("/api/admin/materias/" + materia + "/assuntos/" + assunto),
                "{\"nome\": \"Crase\", \"slug\": \"uso-da-crase\", \"ativo\": true}")
                .andExpect(status().isConflict());
    }

    // ---------------- bancas, orgaos e provas (RF03) ----------------

    @Test
    void criaBancaComSlugDaSigla() throws Exception {
        enviar(post("/api/admin/bancas"), "{\"nome\": \"Fundação Getulio Vargas\", \"sigla\": \"FGV\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("fgv"));
    }

    /** RF03 RN1 */
    @Test
    void siglaDeBancaRepetidaDevolve409() throws Exception {
        criarBanca("FGV");

        enviar(post("/api/admin/bancas"), "{\"nome\": \"Outra\", \"sigla\": \"fgv\", \"slug\": \"outra\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Já existe uma banca com esta sigla."));
    }

    /** RF03 RN1: UF obrigatoria fora da esfera federal. */
    @Test
    void orgaoEstadualSemUfDevolve409() throws Exception {
        enviar(post("/api/admin/orgaos"),
                "{\"nome\": \"Tribunal de Contas do Estado de Goiás\", \"sigla\": \"TCE-GO\", \"esfera\": \"ESTADUAL\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("UF")));
    }

    @Test
    void orgaoFederalNaoGuardaUfEEstadualGuardaEmMaiusculas() throws Exception {
        enviar(post("/api/admin/orgaos"),
                "{\"nome\": \"Receita Federal\", \"sigla\": \"RFB\", \"esfera\": \"FEDERAL\", \"uf\": \"DF\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uf").value(nullValue()));
        enviar(post("/api/admin/orgaos"),
                "{\"nome\": \"Tribunal de Contas\", \"sigla\": \"TCE-GO\", \"esfera\": \"ESTADUAL\", \"uf\": \"go\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uf").value("GO"))
                .andExpect(jsonPath("$.slug").value("tce-go"));
    }

    @Test
    void criaProvaComSlugBancaOrgaoAnoCargo() throws Exception {
        int banca = criarBanca("FGV");
        int orgao = criarOrgao("TCE-GO");

        enviar(post("/api/admin/provas"), prova(banca, orgao, 2026, "Técnico de Controle Externo"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("fgv-tce-go-2026-tecnico-de-controle-externo"))
                .andExpect(jsonPath("$.siglaBanca").value("FGV"))
                .andExpect(jsonPath("$.siglaOrgao").value("TCE-GO"));
    }

    /** RF03 RN2 */
    @Test
    void provaRepetidaDevolve409() throws Exception {
        int banca = criarBanca("FGV");
        int orgao = criarOrgao("TCE-GO");
        enviar(post("/api/admin/provas"), prova(banca, orgao, 2026, "Analista")).andExpect(status().isCreated());

        enviar(post("/api/admin/provas"), prova(banca, orgao, 2026, "ANALISTA"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Já existe uma prova desta banca, órgão, ano e cargo."));
    }

    @Test
    void anoForaDoIntervaloDevolve400() throws Exception {
        enviar(post("/api/admin/provas"), prova(criarBanca("FGV"), criarOrgao("TCE-GO"), 1989, "Analista"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listaProvasBuscandoPelaSiglaDaBanca() throws Exception {
        int orgao = criarOrgao("TCE-GO");
        enviar(post("/api/admin/provas"), prova(criarBanca("FGV"), orgao, 2026, "Analista"));
        enviar(post("/api/admin/provas"), prova(criarBanca("CEBRASPE"), orgao, 2025, "Analista"));

        mockMvc.perform(get("/api/admin/provas?busca=cebra").session(editor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].siglaBanca").value("CEBRASPE"));
        mockMvc.perform(get("/api/admin/provas").session(editor))
                .andExpect(jsonPath("$.content[0].ano").value(2026));
    }

    /** RF03 RN3 */
    @Test
    void naoExcluiBancaOrgaoOuProvaComVinculos() throws Exception {
        int banca = criarBanca("FGV");
        int orgao = criarOrgao("TCE-GO");
        int prova = idDe(enviar(post("/api/admin/provas"), prova(banca, orgao, 2026, "Analista")));
        inserirQuestao(criarMateria("Português"), null, prova, false);

        mockMvc.perform(delete("/api/admin/provas/" + prova).session(editor).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A prova tem questões e não pode ser excluída."));
        mockMvc.perform(delete("/api/admin/bancas/" + banca).session(editor).with(csrf()))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/admin/orgaos/" + orgao).session(editor).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void excluiProvaSemQuestaoEDepoisABanca() throws Exception {
        int banca = criarBanca("FGV");
        int prova = idDe(enviar(post("/api/admin/provas"), prova(banca, criarOrgao("TCE-GO"), 2026, "Analista")));

        mockMvc.perform(delete("/api/admin/provas/" + prova).session(editor).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/admin/bancas/" + banca).session(editor).with(csrf()))
                .andExpect(status().isNoContent());
    }

    // ---------------- apoio ----------------

    private ResultActions enviar(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,
            String json) throws Exception {
        return mockMvc.perform(req.session(editor).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static int idDe(ResultActions resultado) throws Exception {
        return JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), "$.id");
    }

    private int criarMateria(String nome) throws Exception {
        return idDe(enviar(post("/api/admin/materias"), "{\"nome\": \"" + nome + "\", \"ativa\": true}")
                .andExpect(status().isCreated()));
    }

    private int criarAssunto(int materia, String nome, Integer pai) throws Exception {
        return idDe(enviar(post("/api/admin/materias/" + materia + "/assuntos"),
                "{\"nome\": \"" + nome + "\", \"idAssuntoPai\": " + pai + ", \"ativo\": true}")
                .andExpect(status().isCreated()));
    }

    private int criarBanca(String sigla) throws Exception {
        return idDe(enviar(post("/api/admin/bancas"), "{\"nome\": \"Banca " + sigla + "\", \"sigla\": \"" + sigla + "\"}")
                .andExpect(status().isCreated()));
    }

    private int criarOrgao(String sigla) throws Exception {
        return idDe(enviar(post("/api/admin/orgaos"),
                "{\"nome\": \"Órgão " + sigla + "\", \"sigla\": \"" + sigla + "\", \"esfera\": \"ESTADUAL\", \"uf\": \"GO\"}")
                .andExpect(status().isCreated()));
    }

    private static String prova(int banca, int orgao, int ano, String cargo) {
        return "{\"idBanca\": " + banca + ", \"idOrgao\": " + orgao + ", \"ano\": " + ano + ", \"cargo\": \"" + cargo
                + "\", \"nivel\": \"SUPERIOR\"}";
    }

    /** Ainda nao ha API de questoes (issue #5): grava direto no banco. */
    private void inserirQuestao(int materia, Integer assunto, Integer prova, boolean publicada) {
        jdbc.update("""
                INSERT INTO questoes (id_materia, id_assunto, id_prova, id_autor, tipo, origem, enunciado, comentario,
                                      slug, status, publicado_em)
                VALUES (?, ?, ?, ?, 'MULTIPLA_ESCOLHA', ?, 'Enunciado', 'Comentário', 'enunciado', ?, ?)
                """, materia, assunto, prova, autor.getId(), prova == null ? "AUTORAL" : "PROVA",
                publicada ? "PUBLICADA" : "RASCUNHO", publicada ? java.time.LocalDateTime.now() : null);
    }
}
