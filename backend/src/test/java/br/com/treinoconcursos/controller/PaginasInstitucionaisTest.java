package br.com.treinoconcursos.controller;

import br.com.treinoconcursos.IntegracaoTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Layout publico e paginas institucionais (RF18 RN1-RN4, RNF08). */
class PaginasInstitucionaisTest extends IntegracaoTest {

    /** RF18 RN1: rodape com os cinco links em todas as paginas. */
    @ParameterizedTest
    @ValueSource(strings = {"/", "/sobre", "/contato", "/privacidade", "/termos", "/cookies"})
    void paginaAbreComLayoutERodape(String caminho) throws Exception {
        mockMvc.perform(get(caminho))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(allOf(
                        containsString("<html lang=\"pt-BR\">"),
                        matchesPattern("(?s).*href=\"/css/site-[0-9a-f]{32}\\.css\".*"),
                        containsString("href=\"/sobre\""),
                        containsString("href=\"/contato\""),
                        containsString("href=\"/privacidade\""),
                        containsString("href=\"/termos\""),
                        containsString("href=\"/cookies\""),
                        not(containsString("noindex")))));
    }

    /** RNF08: visitante nao recebe cookie nem sessao (a pagina pode ficar em cache). */
    @ParameterizedTest
    @ValueSource(strings = {"/", "/privacidade", "/css/site.css"})
    void paginaPublicaNaoGravaCookie(String caminho) throws Exception {
        mockMvc.perform(get(caminho))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(request().sessionAttributeDoesNotExist("SPRING_SECURITY_CONTEXT"));
    }

    @Test
    void tituloLevaONomeDoSite() throws Exception {
        mockMvc.perform(get("/sobre"))
                .andExpect(content().string(containsString("<title>Sobre | Treino de Concursos</title>")));
        mockMvc.perform(get("/"))
                .andExpect(content().string(containsString("<title>Treino de Concursos</title>")));
    }

    /** RF18 RN2: o aviso vem escondido e o script do consentimento o mostra no primeiro acesso. */
    @Test
    void avisoDeCookiesVemEscondidoComOScript() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(content().string(allOf(
                        containsString("data-aviso-cookies hidden"),
                        containsString("data-consentimento=\"personalizados\""),
                        containsString("data-consentimento=\"nao-personalizados\""),
                        containsString("src=\"http://vite.teste/src/consentimento/main.ts\""),
                        containsString("src=\"http://vite.teste/@vite/client\""),
                        containsString("src=\"http://vite.teste/src/dev/preambulo-react.ts\""))));
    }

    /** RF18 RN3 */
    @Test
    void privacidadeExplicaArmazenamentoAnunciosEEstatisticas() throws Exception {
        mockMvc.perform(get("/privacidade"))
                .andExpect(content().string(allOf(
                        containsString("localStorage"),
                        containsString("Google AdSense"),
                        containsString("Estatísticas anônimas"),
                        containsString("LGPD"),
                        containsString("mailto:contato@teste.com.br"),
                        containsString("Equipe de Teste"))));
    }

    /** RF18 RN4: contato por e-mail, sem formulario em nenhuma pagina publica. */
    @ParameterizedTest
    @ValueSource(strings = {"/", "/sobre", "/contato", "/privacidade", "/termos", "/cookies"})
    void semFormulario(String caminho) throws Exception {
        mockMvc.perform(get(caminho))
                .andExpect(content().string(not(containsString("<form"))));
    }

    @Test
    void contatoMostraOEmail() throws Exception {
        mockMvc.perform(get("/contato"))
                .andExpect(content().string(containsString(
                        "<a href=\"mailto:contato@teste.com.br\">contato@teste.com.br</a>")));
    }

    /** Os TODOs dos rascunhos ficam so no template, nunca no HTML entregue. */
    @Test
    void comentariosDoTemplateNaoVazam() throws Exception {
        mockMvc.perform(get("/privacidade"))
                .andExpect(content().string(not(containsString("TODO"))));
    }

    /** RF10 RN6 e doc/api.md secao 2: /meu-progresso tem noindex, explica que fica so no navegador e monta a ilha. */
    @Test
    void meuProgressoUsaNoindexInformaArmazenamentoLocalECarregaIlha() throws Exception {
        mockMvc.perform(get("/meu-progresso"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(content().string(allOf(
                        containsString("<title>Meu progresso | Treino de Concursos</title>"),
                        containsString("<meta name=\"robots\" content=\"noindex\">"),
                        containsString("apenas neste navegador"),
                        containsString("localStorage"),
                        containsString("data-ilha=\"meu-progresso\""),
                        containsString("src=\"http://vite.teste/src/ilhas/listagem.tsx\""),
                        not(containsString("<form")))));
    }
}

