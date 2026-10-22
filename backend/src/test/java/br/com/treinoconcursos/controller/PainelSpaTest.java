package br.com.treinoconcursos.controller;

import br.com.treinoconcursos.IntegracaoTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fallback da SPA do painel (RF01): toda rota do React Router abre o index.html.
 * Usa o casco falso de src/test/resources/static/admin/index.html.
 */
class PainelSpaTest extends IntegracaoTest {

    @ParameterizedTest
    @ValueSource(strings = {"/admin", "/admin/", "/admin/index.html", "/admin/materias", "/admin/questoes/12/editar"})
    void rotaDoPainelDevolveOCascoDaSpaSemCache(String caminho) throws Exception {
        mockMvc.perform(get(caminho))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("data-casco-painel-teste")))
                // O cookie XSRF-TOKEN fica em SecurityConfigTest: aqui o contexto e compartilhado e o
                // csrf() do spring-security-test (AuthControllerTest) troca o repositorio de tokens nele.
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
    }

    /** Ultimo trecho com ponto e arquivo, nao rota: arquivo inexistente nao vira o index.html. */
    @ParameterizedTest
    @ValueSource(strings = {"/admin/favicon.ico", "/admin/materias/script.js"})
    void arquivoInexistenteNoPainelResponde404(String caminho) throws Exception {
        mockMvc.perform(get(caminho))
                .andExpect(status().isNotFound());
    }
}
