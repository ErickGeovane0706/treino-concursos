package br.com.treinoconcursos.controller;

import br.com.treinoconcursos.IntegracaoTest;
import br.com.treinoconcursos.model.Alternativa;
import br.com.treinoconcursos.model.Materia;
import br.com.treinoconcursos.model.OrigemQuestao;
import br.com.treinoconcursos.model.Perfil;
import br.com.treinoconcursos.model.Questao;
import br.com.treinoconcursos.model.StatusQuestao;
import br.com.treinoconcursos.model.TipoQuestao;
import br.com.treinoconcursos.model.Usuario;
import br.com.treinoconcursos.repository.MateriaRepository;
import br.com.treinoconcursos.repository.QuestaoRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de integracao para reporte de erro em questao e fila de tratamento no painel
 * (RF16 RN1-RN5, US07, US12, RNF08, issue #14).
 */
class ReporteErroTest extends IntegracaoTest {

    @Autowired
    private MateriaRepository materiaRepository;

    @Autowired
    private QuestaoRepository questaoRepository;

    private Usuario editor;
    private Materia portugues;

    @BeforeEach
    void prepararDados() {
        editor = criarUsuario("editor@teste.com.br", Perfil.EDITOR);

        portugues = new Materia();
        portugues.setNome("Português");
        portugues.setSlug("portugues");
        portugues = materiaRepository.save(portugues);
    }

    /**
     * US07 e RF16 RN1-RN2: visitante registra reporte com status ABERTO em questao
     * publicada, sem sessao, sem cookie CSRF e sem dados pessoais no banco.
     */
    @Test
    void registraReporteAbertoEmQuestaoPublicadaSemDadosPessoais() throws Exception {
        Questao questao = criarQuestao("Questão sobre crase", StatusQuestao.PUBLICADA);

        MvcResult resultado = mockMvc.perform(post("/api/questoes/" + questao.getId() + "/reportes")
                        .header("X-Forwarded-For", "198.51.100.10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "motivo": "GABARITO_ERRADO",
                                  "descricao": "O gabarito oficial preliminar foi alterado para a letra C."
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(cookie().doesNotExist("XSRF-TOKEN"))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.idQuestao", is(questao.getId())))
                .andExpect(jsonPath("$.motivo", is("GABARITO_ERRADO")))
                .andExpect(jsonPath("$.descricao", is("O gabarito oficial preliminar foi alterado para a letra C.")))
                .andExpect(jsonPath("$.status", is("ABERTO")))
                .andExpect(jsonPath("$.criadoEm", notNullValue()))
                .andReturn();

        assertThat(resultado.getRequest().getSession(false)).isNull();

        // Descricao opcional (null ou em branco) tambem e aceita (RF16 RN1)
        mockMvc.perform(post("/api/questoes/" + questao.getId() + "/reportes")
                        .header("X-Forwarded-For", "198.51.100.11")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "motivo": "QUESTAO_DESATUALIZADA",
                                  "descricao": "   "
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.motivo", is("QUESTAO_DESATUALIZADA")))
                .andExpect(jsonPath("$.descricao", nullValue()))
                .andExpect(jsonPath("$.status", is("ABERTO")));

        // RF16 RN2 e RNF08: nenhuma coluna de IP, nome ou e-mail do visitante em reportes_erro
        List<String> colunasReportes = jdbc.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'reportes_erro'
                """, String.class);
        assertThat(colunasReportes).containsExactlyInAnyOrder(
                "id_reporte",
                "id_questao",
                "motivo",
                "descricao",
                "status",
                "criado_em",
                "id_resolvido_por",
                "resolvido_em",
                "observacao_resolucao");
    }

    /**
     * RF16 RN1 e US07 pre-condicao: valida motivo obrigatorio, descricao ate 1.000
     * caracteres (400) e questao publicada (404 para rascunho, arquivada ou inexistente).
     */
    @Test
    void rejeitaMotivoAusenteDescricaoAcimaDeMilCaracteresEQuestaoNaoPublicada() throws Exception {
        Questao publicada = criarQuestao("Questão publicada", StatusQuestao.PUBLICADA);
        Questao rascunho = criarQuestao("Questão rascunho", StatusQuestao.RASCUNHO);
        Questao arquivada = criarQuestao("Questão arquivada", StatusQuestao.ARQUIVADA);

        // Sem motivo -> 400 Bad Request
        mockMvc.perform(post("/api/questoes/" + publicada.getId() + "/reportes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descricao\": \"Faltou o motivo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));

        // Descricao com 1.001 caracteres -> 400 Bad Request (RF16 RN1)
        String descricaoLonga = "a".repeat(1001);
        mockMvc.perform(post("/api/questoes/" + publicada.getId() + "/reportes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\": \"OUTRO\", \"descricao\": \"" + descricaoLonga + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));

        // Questao rascunho, arquivada ou inexistente -> 404 Not Found
        for (int idInvalido : List.of(rascunho.getId(), arquivada.getId(), 99999)) {
            mockMvc.perform(post("/api/questoes/" + idInvalido + "/reportes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"motivo\": \"ENUNCIADO_COM_ERRO\"}"))
                    .andExpect(status().isNotFound());
        }
    }

    /**
     * RF16 RN3: limite de 5 reportes por hora por IP em memoria. O 6o envio do
     * mesmo IP na mesma hora recebe 429 Too Many Requests, sem afetar outros IPs
     * e expirando apos 1 hora.
     */
    @Test
    void limitaCincoReportesPorHoraPorIpEmMemoriaERetorna429NoSexto() throws Exception {
        Questao questao = criarQuestao("Questão para teste de limite", StatusQuestao.PUBLICADA);
        int id = questao.getId();
        String ipVisitante = "203.0.113.45";
        String outroIp = "203.0.113.46";

        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/api/questoes/" + id + "/reportes")
                            .header("X-Forwarded-For", ipVisitante)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"motivo\": \"ENUNCIADO_COM_ERRO\", \"descricao\": \"Reporte " + i + "\"}"))
                    .andExpect(status().isCreated());
        }

        // 6o reporte na mesma hora do mesmo IP -> 429 Too Many Requests
        mockMvc.perform(post("/api/questoes/" + id + "/reportes")
                        .header("X-Forwarded-For", ipVisitante)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\": \"ENUNCIADO_COM_ERRO\", \"descricao\": \"Sexto reporte\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status", is(429)))
                .andExpect(jsonPath("$.title", is("Limite de requisições excedido")))
                .andExpect(jsonPath("$.detail", containsString("Muitas requisições")));

        // Outro IP não é afetado
        mockMvc.perform(post("/api/questoes/" + id + "/reportes")
                        .header("X-Forwarded-For", outroIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\": \"COMENTARIO_INCORRETO\"}"))
                .andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM reportes_erro", Integer.class)).isEqualTo(6);

        // Apos 1 hora, a janela expira da memoria e o IP pode enviar novamente
        relogio.avancar(Duration.ofHours(1).plusSeconds(1));
        assertThat(limitadorPorIpService.entradasAtivasEmMemoria()).isZero();

        mockMvc.perform(post("/api/questoes/" + id + "/reportes")
                        .header("X-Forwarded-For", ipVisitante)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\": \"OUTRO\"}"))
                .andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM reportes_erro", Integer.class)).isEqualTo(7);
    }

    /**
     * RF16 RN5 e US12: quando uma questao acumula 3 ou mais reportes ABERTOS de
     * GABARITO_ERRADO, o painel a exibe no topo da fila como prioridade, mesmo que
     * outra questao tenha reportes mais antigos.
     */
    @Test
    void filaPriorizaNoTopoQuestaoComTresOuMaisReportesAbertosDeGabaritoErrado() throws Exception {
        Questao questaoAntiga = criarQuestao("Questão antiga com um reporte", StatusQuestao.PUBLICADA);
        Questao questaoComDoisGabarito = criarQuestao("Questão com apenas dois de gabarito", StatusQuestao.PUBLICADA);
        Questao questaoPrioritaria = criarQuestao("Questão prioritária com três de gabarito", StatusQuestao.PUBLICADA);

        // 1. Reporte mais antigo na questaoAntiga (1 de GABARITO_ERRADO)
        enviarReportePublico(questaoAntiga.getId(), "GABARITO_ERRADO", "Acho que é A", "192.0.2.1");
        relogio.avancar(Duration.ofMinutes(5));

        // 2. Dois reportes de GABARITO_ERRADO + um de ENUNCIADO_COM_ERRO na questaoComDoisGabarito
        // (total 3 reportes, mas apenas 2 de GABARITO_ERRADO -> nao ativa RN5)
        enviarReportePublico(questaoComDoisGabarito.getId(), "GABARITO_ERRADO", "Gabarito 1", "192.0.2.2");
        enviarReportePublico(questaoComDoisGabarito.getId(), "GABARITO_ERRADO", "Gabarito 2", "192.0.2.3");
        enviarReportePublico(questaoComDoisGabarito.getId(), "ENUNCIADO_COM_ERRO", "Erro de digitação", "192.0.2.4");
        relogio.avancar(Duration.ofMinutes(5));

        // 3. Tres reportes ABERTOS de GABARITO_ERRADO na questaoPrioritaria (ativa RN5)
        int rPrio1 = enviarReportePublico(questaoPrioritaria.getId(), "GABARITO_ERRADO", "Correta é D", "192.0.2.10");
        int rPrio2 = enviarReportePublico(questaoPrioritaria.getId(), "GABARITO_ERRADO", "Banca mudou para D", "192.0.2.11");
        int rPrio3 = enviarReportePublico(questaoPrioritaria.getId(), "GABARITO_ERRADO", "Gabarito definitivo D", "192.0.2.12");

        MockHttpSession sessaoEditor = login("editor@teste.com.br");

        mockMvc.perform(get("/api/admin/reportes-erro?status=ABERTO").session(sessaoEditor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(7)))
                // Os 3 reportes da questaoPrioritaria aparecem no topo da fila
                .andExpect(jsonPath("$.content[0].id", is(rPrio1)))
                .andExpect(jsonPath("$.content[0].idQuestao", is(questaoPrioritaria.getId())))
                .andExpect(jsonPath("$.content[0].prioritario", is(true)))
                .andExpect(jsonPath("$.content[0].reportesGabaritoAbertos", is(3)))
                .andExpect(jsonPath("$.content[1].id", is(rPrio2)))
                .andExpect(jsonPath("$.content[1].prioritario", is(true)))
                .andExpect(jsonPath("$.content[2].id", is(rPrio3)))
                .andExpect(jsonPath("$.content[2].prioritario", is(true)))
                // Os demais veem depois, em ordem de chegada, com prioritario = false
                .andExpect(jsonPath("$.content[3].idQuestao", is(questaoAntiga.getId())))
                .andExpect(jsonPath("$.content[3].prioritario", is(false)))
                .andExpect(jsonPath("$.content[3].reportesGabaritoAbertos", is(1)))
                .andExpect(jsonPath("$.content[4].idQuestao", is(questaoComDoisGabarito.getId())))
                .andExpect(jsonPath("$.content[4].prioritario", is(false)))
                .andExpect(jsonPath("$.content[4].reportesGabaritoAbertos", is(2)));
    }

    /**
     * RF16 RN4 e US12: Editor resolve e descarta reportes com observacao; ao encerrar
     * um dos 3 reportes de gabarito errado, a questao deixa de ser prioritaria.
     * Tentar encerrar reporte ja encerrado retorna 409 Conflict.
     */
    @Test
    void editorResolveEDescartaReportesComObservacaoEAtualizaPrioridadeDaFila() throws Exception {
        Questao questaoNormal = criarQuestao("Questão normal mais antiga", StatusQuestao.PUBLICADA);
        Questao questaoGabarito = criarQuestao("Questão com erro no gabarito", StatusQuestao.PUBLICADA);

        int idReporteNormal = enviarReportePublico(questaoNormal.getId(), "ENUNCIADO_COM_ERRO", "Falta vírgula", "198.51.100.1");
        relogio.avancar(Duration.ofMinutes(2));

        int idGabarito1 = enviarReportePublico(questaoGabarito.getId(), "GABARITO_ERRADO", "Gabarito é C", "198.51.100.2");
        int idGabarito2 = enviarReportePublico(questaoGabarito.getId(), "GABARITO_ERRADO", "Gabarito oficial C", "198.51.100.3");
        int idGabarito3 = enviarReportePublico(questaoGabarito.getId(), "GABARITO_ERRADO", "Letra C certa", "198.51.100.4");

        // Sem login -> 401
        mockMvc.perform(get("/api/admin/reportes-erro"))
                .andExpect(status().isUnauthorized());

        MockHttpSession sessaoEditor = login("editor@teste.com.br");

        // Sem CSRF no PATCH -> 403
        mockMvc.perform(patch("/api/admin/reportes-erro/" + idGabarito1 + "/resolver")
                        .session(sessaoEditor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"observacao\": \"Gabarito atualizado.\"}"))
                .andExpect(status().isForbidden());

        // Editor resolve o primeiro reporte de gabarito com observacao (RF16 RN4)
        mockMvc.perform(patch("/api/admin/reportes-erro/" + idGabarito1 + "/resolver")
                        .session(sessaoEditor)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"observacao\": \"Gabarito alterado de B para C.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(idGabarito1)))
                .andExpect(jsonPath("$.status", is("RESOLVIDO")))
                .andExpect(jsonPath("$.idResolvidoPor", is(editor.getId())))
                .andExpect(jsonPath("$.nomeResolvidoPor", is(editor.getNome())))
                .andExpect(jsonPath("$.resolvidoEm", notNullValue()))
                .andExpect(jsonPath("$.observacaoResolucao", is("Gabarito alterado de B para C.")));

        // Tentar resolver ou descartar novamente -> 409 Conflict
        mockMvc.perform(patch("/api/admin/reportes-erro/" + idGabarito1 + "/descartar")
                        .session(sessaoEditor)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"observacao\": \"Duplicado\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("já foi encerrado")));

        // Editor descarta o reporte normal com observacao (RF16 RN4)
        mockMvc.perform(patch("/api/admin/reportes-erro/" + idReporteNormal + "/descartar")
                        .session(sessaoEditor)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"observacao\": \"O texto confere com a prova original.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(idReporteNormal)))
                .andExpect(jsonPath("$.status", is("DESCARTADO")))
                .andExpect(jsonPath("$.idResolvidoPor", is(editor.getId())))
                .andExpect(jsonPath("$.resolvidoEm", notNullValue()))
                .andExpect(jsonPath("$.observacaoResolucao", is("O texto confere com a prova original.")));

        // Na fila de ABERTOS sobram apenas idGabarito2 e idGabarito3 (2 abertos -> prioritario = false)
        mockMvc.perform(get("/api/admin/reportes-erro?status=ABERTO").session(sessaoEditor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].id", is(idGabarito2)))
                .andExpect(jsonPath("$.content[0].prioritario", is(false)))
                .andExpect(jsonPath("$.content[0].reportesGabaritoAbertos", is(2)))
                .andExpect(jsonPath("$.content[1].id", is(idGabarito3)))
                .andExpect(jsonPath("$.content[1].prioritario", is(false)));
    }

    private int enviarReportePublico(int idQuestao, String motivo, String descricao, String ip) throws Exception {
        String resposta = mockMvc.perform(post("/api/questoes/" + idQuestao + "/reportes")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "motivo": "%s",
                                  "descricao": "%s"
                                }
                                """.formatted(motivo, descricao)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(resposta, "$.id");
    }

    private Questao criarQuestao(String enunciado, StatusQuestao status) {
        Questao q = new Questao();
        q.setMateria(portugues);
        q.setAutor(editor);
        q.setTipo(TipoQuestao.MULTIPLA_ESCOLHA);
        q.setOrigem(OrigemQuestao.AUTORAL);
        q.setEnunciado(enunciado);
        q.setSlug("questao-" + System.nanoTime());
        q.setStatus(status);
        if (status != StatusQuestao.RASCUNHO) {
            q.setComentario("Comentário da questão.");
            q.setPublicadoEm(LocalDateTime.now(relogio));
        }
        q.adicionarAlternativa(new Alternativa("A", "Alternativa A", false));
        q.adicionarAlternativa(new Alternativa("B", "Alternativa B", true));
        q.adicionarAlternativa(new Alternativa("C", "Alternativa C", false));
        q.adicionarAlternativa(new Alternativa("D", "Alternativa D", false));
        q.adicionarAlternativa(new Alternativa("E", "Alternativa E", false));
        return questaoRepository.save(q);
    }
}
