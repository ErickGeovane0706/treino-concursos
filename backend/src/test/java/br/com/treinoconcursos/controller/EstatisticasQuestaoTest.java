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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Estatisticas anonimas de respostas (RF17 RN1-RN4, RF04 RN5, RNF08, issue #13).
 */
class EstatisticasQuestaoTest extends IntegracaoTest {

    @Autowired
    private MateriaRepository materiaRepository;

    @Autowired
    private QuestaoRepository questaoRepository;

    private Usuario autor;
    private Materia portugues;

    @BeforeEach
    void prepararDados() {
        autor = criarUsuario("editor@teste.com.br", Perfil.EDITOR);

        portugues = new Materia();
        portugues.setNome("Português");
        portugues.setSlug("portugues");
        portugues = materiaRepository.save(portugues);
    }

    /**
     * Teste de concorrencia (issue #13): 100 respostas simultaneas devem virar
     * exatamente 100 no contador (total_respostas = 100, soma de total_marcacoes = 100)
     * gracas ao UPDATE ... SET x = x + 1 atomico no PostgreSQL.
     */
    @Test
    void cemRespostasSimultaneasViramExatamenteCemNoContador() throws Exception {
        Questao questao = criarQuestaoMultiplaEscolha(StatusQuestao.PUBLICADA);
        int id = questao.getId();

        int totalConcorrentes = 100;
        CountDownLatch prontos = new CountDownLatch(totalConcorrentes);
        CountDownLatch largada = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(totalConcorrentes);
        try {
            List<Callable<Integer>> tarefas = new ArrayList<>(totalConcorrentes);
            for (int i = 0; i < totalConcorrentes; i++) {
                final int indice = i;
                // 62 acertos (B), 20 em A e 18 em C -> "62% acertaram"
                final String letra = indice < 62 ? "B" : (indice < 82 ? "A" : "C");
                final String ip = "198.51.100." + ((indice % 50) + 1);
                tarefas.add(() -> {
                    prontos.countDown();
                    assertThat(largada.await(10, TimeUnit.SECONDS)).isTrue();
                    return mockMvc.perform(post("/api/questoes/" + id + "/respostas")
                                    .header("X-Forwarded-For", ip)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"letra\": \"" + letra + "\"}"))
                            .andReturn()
                            .getResponse()
                            .getStatus();
                });
            }

            List<Future<Integer>> futuros = new ArrayList<>(totalConcorrentes);
            for (Callable<Integer> tarefa : tarefas) {
                futuros.add(pool.submit(tarefa));
            }

            assertThat(prontos.await(10, TimeUnit.SECONDS)).isTrue();
            largada.countDown();

            for (Future<Integer> futuro : futuros) {
                assertThat(futuro.get(30, TimeUnit.SECONDS)).isEqualTo(202);
            }
        } finally {
            pool.shutdownNow();
        }

        Integer totalRespostas = jdbc.queryForObject(
                "SELECT total_respostas FROM questoes WHERE id_questao = ?", Integer.class, id);
        Integer totalAcertos = jdbc.queryForObject(
                "SELECT total_acertos FROM questoes WHERE id_questao = ?", Integer.class, id);
        Integer somaMarcacoes = jdbc.queryForObject(
                "SELECT SUM(total_marcacoes) FROM alternativas WHERE id_questao = ?", Integer.class, id);

        assertThat(totalRespostas).isEqualTo(100);
        assertThat(totalAcertos).isEqualTo(62);
        assertThat(somaMarcacoes).isEqualTo(100);

        mockMvc.perform(get("/api/questoes/" + id + "/estatisticas"))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist("XSRF-TOKEN"))
                .andExpect(jsonPath("$.disponivel", is(true)))
                .andExpect(jsonPath("$.totalRespostas", is(100)))
                .andExpect(jsonPath("$.percentualAcertos", is(62)))
                .andExpect(jsonPath("$.alternativas", hasSize(5)))
                .andExpect(jsonPath("$.alternativas[0].letra", is("A")))
                .andExpect(jsonPath("$.alternativas[0].percentual", is(20)))
                .andExpect(jsonPath("$.alternativas[1].letra", is("B")))
                .andExpect(jsonPath("$.alternativas[1].percentual", is(62)))
                .andExpect(jsonPath("$.alternativas[2].letra", is("C")))
                .andExpect(jsonPath("$.alternativas[2].percentual", is(18)))
                .andExpect(jsonPath("$.alternativas[3].letra", is("D")))
                .andExpect(jsonPath("$.alternativas[3].percentual", is(0)))
                .andExpect(jsonPath("$.alternativas[4].letra", is("E")))
                .andExpect(jsonPath("$.alternativas[4].percentual", is(0)))
                // doc/api.md secao 4: endpoint publico nunca expoe qual alternativa e a correta
                .andExpect(jsonPath("$.alternativas[1].correta").doesNotExist());
    }

    /**
     * RF17 RN3: o percentual de acertos e o % por alternativa so sao exibidos
     * quando a questao atinge 30 respostas ou mais.
     */
    @Test
    void soExibePercentuaisComTrintaOuMaisRespostas() throws Exception {
        Questao questao = criarQuestaoMultiplaEscolha(StatusQuestao.PUBLICADA);
        int id = questao.getId();

        // 0 respostas -> disponivel = false
        mockMvc.perform(get("/api/questoes/" + id + "/estatisticas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponivel", is(false)))
                .andExpect(jsonPath("$.totalRespostas", is(0)))
                .andExpect(jsonPath("$.percentualAcertos", nullValue()))
                .andExpect(jsonPath("$.alternativas", hasSize(0)));

        // 29 respostas -> ainda indisponivel
        for (int i = 0; i < 29; i++) {
            String letra = i < 18 ? "B" : "A";
            mockMvc.perform(post("/api/questoes/" + id + "/respostas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"letra\": \"" + letra + "\"}"))
                    .andExpect(status().isAccepted())
                    .andExpect(cookie().doesNotExist("XSRF-TOKEN"));
        }

        mockMvc.perform(get("/api/questoes/" + id + "/estatisticas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponivel", is(false)))
                .andExpect(jsonPath("$.totalRespostas", is(29)))
                .andExpect(jsonPath("$.percentualAcertos", nullValue()))
                .andExpect(jsonPath("$.alternativas", hasSize(0)));

        // 30a resposta -> passa a exibir (18 acertos em 30 = 60%)
        mockMvc.perform(post("/api/questoes/" + id + "/respostas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"letra\": \"A\"}"))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/questoes/" + id + "/estatisticas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponivel", is(true)))
                .andExpect(jsonPath("$.totalRespostas", is(30)))
                .andExpect(jsonPath("$.percentualAcertos", is(60)))
                .andExpect(jsonPath("$.alternativas", hasSize(5)))
                .andExpect(jsonPath("$.alternativas[0].percentual", is(40)))
                .andExpect(jsonPath("$.alternativas[1].percentual", is(60)));
    }

    /**
     * RF17 RN4 e RNF08: limitador por IP controlado exclusivamente em memoria,
     * com expiracao apos a janela de 1 hora.
     */
    @Test
    void limitaEnviosPorIpEmMemoriaComExpiracaoSemBloquearOutrosIps() throws Exception {
        Questao questao = criarQuestaoMultiplaEscolha(StatusQuestao.PUBLICADA);
        int id = questao.getId();
        String ipLimitado = "203.0.113.77";
        String outroIp = "203.0.113.88";

        for (int i = 0; i < 120; i++) {
            mockMvc.perform(post("/api/questoes/" + id + "/respostas")
                            .header("X-Forwarded-For", ipLimitado)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"letra\": \"B\"}"))
                    .andExpect(status().isAccepted());
        }

        // 121o envio do mesmo IP na mesma hora -> 429 Too Many Requests
        mockMvc.perform(post("/api/questoes/" + id + "/respostas")
                        .header("X-Forwarded-For", ipLimitado)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"letra\": \"B\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status", is(429)))
                .andExpect(jsonPath("$.title", is("Limite de requisições excedido")));

        // Outro IP continua respondendo normalmente
        mockMvc.perform(post("/api/questoes/" + id + "/respostas")
                        .header("X-Forwarded-For", outroIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"letra\": \"A\"}"))
                .andExpect(status().isAccepted());

        assertThat(jdbc.queryForObject("SELECT total_respostas FROM questoes WHERE id_questao = ?",
                Integer.class, id)).isEqualTo(121);
        assertThat(limitadorPorIpService.entradasAtivasEmMemoria()).isEqualTo(2);

        // Avanca o relogio alem da janela de 1 hora: as entradas expiram da memoria
        relogio.avancar(Duration.ofHours(1).plusSeconds(1));
        assertThat(limitadorPorIpService.entradasAtivasEmMemoria()).isZero();

        mockMvc.perform(post("/api/questoes/" + id + "/respostas")
                        .header("X-Forwarded-For", ipLimitado)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"letra\": \"B\"}"))
                .andExpect(status().isAccepted());

        assertThat(jdbc.queryForObject("SELECT total_respostas FROM questoes WHERE id_questao = ?",
                Integer.class, id)).isEqualTo(122);
    }

    /**
     * Validacao de formato (400), alternativa inexistente na questao (409) e
     * questao inexistente / rascunho / arquivada (404).
     */
    @Test
    void rejeitaLetraMalFormadaAlternativaInvalidaEQuestaoNaoPublicada() throws Exception {
        Questao publicadaCertoErrado = new Questao();
        publicadaCertoErrado.setMateria(portugues);
        publicadaCertoErrado.setAutor(autor);
        publicadaCertoErrado.setTipo(TipoQuestao.CERTO_ERRADO);
        publicadaCertoErrado.setOrigem(OrigemQuestao.AUTORAL);
        publicadaCertoErrado.setEnunciado("Julgue o item.");
        publicadaCertoErrado.setComentario("Item certo.");
        publicadaCertoErrado.setSlug("julgue-o-item");
        publicadaCertoErrado.setStatus(StatusQuestao.PUBLICADA);
        publicadaCertoErrado.setPublicadoEm(LocalDateTime.now(relogio));
        publicadaCertoErrado.adicionarAlternativa(new Alternativa("C", "Certo", true));
        publicadaCertoErrado.adicionarAlternativa(new Alternativa("E", "Errado", false));
        publicadaCertoErrado = questaoRepository.save(publicadaCertoErrado);

        Questao rascunho = criarQuestaoMultiplaEscolha(StatusQuestao.RASCUNHO);
        Questao arquivada = criarQuestaoMultiplaEscolha(StatusQuestao.ARQUIVADA);

        // Letra F -> 400 Bad Request (doc/api.md secao 7)
        mockMvc.perform(post("/api/questoes/" + publicadaCertoErrado.getId() + "/respostas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"letra\": \"F\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));

        // Letra A em questao Certo/Errado (so tem C e E) -> 409 Conflict
        mockMvc.perform(post("/api/questoes/" + publicadaCertoErrado.getId() + "/respostas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"letra\": \"A\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("não pertence a esta questão")));

        // Rascunho, arquivada e inexistente -> 404 Not Found
        for (int idInvalido : List.of(rascunho.getId(), arquivada.getId(), 99999)) {
            mockMvc.perform(post("/api/questoes/" + idInvalido + "/respostas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"letra\": \"A\"}"))
                    .andExpect(status().isNotFound());

            mockMvc.perform(get("/api/questoes/" + idInvalido + "/estatisticas"))
                    .andExpect(status().isNotFound());
        }
    }

    /**
     * RF04 RN5: quando o gabarito de uma questao publicada muda, as estatisticas
     * sao zeradas.
     */
    @Test
    void alterarGabaritoZeraEstatisticasAcumuladasPelaApiPublica() throws Exception {
        Questao questao = criarQuestaoMultiplaEscolha(StatusQuestao.PUBLICADA);
        int id = questao.getId();

        for (int i = 0; i < 30; i++) {
            mockMvc.perform(post("/api/questoes/" + id + "/respostas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"letra\": \"B\"}"))
                    .andExpect(status().isAccepted());
        }

        mockMvc.perform(get("/api/questoes/" + id + "/estatisticas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponivel", is(true)))
                .andExpect(jsonPath("$.totalRespostas", is(30)))
                .andExpect(jsonPath("$.percentualAcertos", is(100)));

        MockHttpSession sessaoEditor = login("editor@teste.com.br");
        String novoGabarito = """
                {
                  "idMateria": %d,
                  "idAssunto": null,
                  "tipo": "MULTIPLA_ESCOLHA",
                  "origem": "AUTORAL",
                  "idProva": null,
                  "enunciado": "Enunciado da questão",
                  "comentario": "Comentário atualizado.",
                  "alternativas": [
                    {"letra": "A", "texto": "Alternativa A", "correta": false},
                    {"letra": "B", "texto": "Alternativa B", "correta": false},
                    {"letra": "C", "texto": "Alternativa C", "correta": true},
                    {"letra": "D", "texto": "Alternativa D", "correta": false},
                    {"letra": "E", "texto": "Alternativa E", "correta": false}
                  ]
                }
                """.formatted(portugues.getId());

        mockMvc.perform(put("/api/admin/questoes/" + id)
                        .session(sessaoEditor)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(novoGabarito))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRespostas", is(0)))
                .andExpect(jsonPath("$.totalAcertos", is(0)));

        mockMvc.perform(get("/api/questoes/" + id + "/estatisticas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponivel", is(false)))
                .andExpect(jsonPath("$.totalRespostas", is(0)))
                .andExpect(jsonPath("$.percentualAcertos", nullValue()))
                .andExpect(jsonPath("$.alternativas", hasSize(0)));
    }

    /**
     * RNF08 e RF17 RN4: nenhuma tabela do banco possui coluna de IP ou
     * identificador de visitante, e nenhum dado do visitante vai para o banco.
     */
    @Test
    void nenhumaTabelaPossuiColunaDeIpOuIdentificadorDeVisitante() throws Exception {
        Questao questao = criarQuestaoMultiplaEscolha(StatusQuestao.PUBLICADA);
        String ipVisitante = "192.0.2.123";

        mockMvc.perform(post("/api/questoes/" + questao.getId() + "/respostas")
                        .header("X-Forwarded-For", ipVisitante)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"letra\": \"B\"}"))
                .andExpect(status().isAccepted());

        List<String> colunasSuspeitas = jdbc.queryForList("""
                SELECT table_name || '.' || column_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name <> 'flyway_schema_history'
                  AND (
                    column_name ~* '(^|_)(ip|visitante|session|sessao|cookie|fingerprint|user_agent|navegador)($|_)'
                  )
                """, String.class);

        assertThat(colunasSuspeitas).isEmpty();
    }

    private Questao criarQuestaoMultiplaEscolha(StatusQuestao status) {
        Questao q = new Questao();
        q.setMateria(portugues);
        q.setAutor(autor);
        q.setTipo(TipoQuestao.MULTIPLA_ESCOLHA);
        q.setOrigem(OrigemQuestao.AUTORAL);
        q.setEnunciado("Enunciado da questão");
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
