package br.com.treinoconcursos.config;

import br.com.treinoconcursos.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regras de acesso por rota (RNF01, doc/api.md secao 9), so na camada web.
 * O fluxo de login de verdade e testado em AuthControllerTest.
 */
@WebMvcTest(controllers = SecurityConfigTest.ControllerFalso.class)
@Import({SecurityConfig.class, SecurityConfigTest.ControllerFalso.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    /** Usado pelo UsuarioSessaoFilter; aqui nao ha banco. */
    @MockitoBean
    private UsuarioRepository usuarioRepository;

    @Test
    void rotaPublicaFicaAbertaSemLogin() throws Exception {
        mockMvc.perform(get("/api/publico"))
                .andExpect(status().isOk());
    }

    @Test
    void cascoDaSpaDoPainelFicaAbertoParaATelaDeLogin() throws Exception {
        mockMvc.perform(get("/admin/"))
                .andExpect(status().isOk());
    }

    @Test
    void apiDoPainelExigeAutenticacao() throws Exception {
        mockMvc.perform(get("/api/admin/questoes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void editorAcessaApiDoPainel() throws Exception {
        mockMvc.perform(get("/api/admin/questoes").with(user("editor").roles("EDITOR")))
                .andExpect(status().isOk());
    }

    @Test
    void editorNaoGerenciaUsuarios() throws Exception {
        mockMvc.perform(get("/api/admin/usuarios").with(user("editor").roles("EDITOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminGerenciaUsuarios() throws Exception {
        mockMvc.perform(get("/api/admin/usuarios").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void postSemTokenCsrfEhRecusado() throws Exception {
        mockMvc.perform(post("/api/publico"))
                .andExpect(status().isForbidden());
    }

    /**
     * A SPA precisa receber o cookie XSRF-TOKEN antes do primeiro POST (o login).
     * Fica aqui porque o csrf() dos testes de integracao troca o repositorio de
     * token da cadeia compartilhada e esconderia o cookie.
     */
    @Test
    void primeiraRequisicaoRecebeOCookieCsrfLegivelPeloJavaScript() throws Exception {
        mockMvc.perform(get("/api/publico"))
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
    }

    /** 401 anonimo nao cria sessao (o site publico nao usa sessao). */
    @Test
    void naoCriaSessaoParaVisitanteAnonimo() throws Exception {
        mockMvc.perform(get("/api/admin/questoes"))
                .andExpect(status().isUnauthorized())
                .andExpect(request().sessionAttributeDoesNotExist("SPRING_SECURITY_SAVED_REQUEST"));
    }

    /**
     * RNF01: cabecalhos de seguranca obrigatorios (CSP compativel com o AdSense,
     * X-Content-Type-Options, Referrer-Policy e Strict-Transport-Security).
     */
    @Test
    void enviaCabecalhosDeSegurancaRnf01() throws Exception {
        mockMvc.perform(get("/api/publico"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"))
                .andExpect(header().string("Content-Security-Policy",
                        containsString("default-src 'self'")))
                .andExpect(header().string("Content-Security-Policy",
                        containsString("https://pagead2.googlesyndication.com")))
                .andExpect(header().string("Content-Security-Policy",
                        containsString("object-src 'none'")));
    }

    @Test
    void cspIncluiServidorViteQuandoEmDesenvolvimento() {
        String cspProd = SecurityConfig.politicaCsp("");
        String cspDev = SecurityConfig.politicaCsp("http://localhost:5173");

        assertThat(cspProd).doesNotContain("localhost:5173");
        assertThat(cspDev)
                .contains("http://localhost:5173")
                .contains("ws://localhost:5173");
    }

    @RestController
    static class ControllerFalso {

        @GetMapping({"/api/publico", "/admin/", "/api/admin/questoes", "/api/admin/usuarios"})
        public String ok() {
            return "ok";
        }

        @PostMapping("/api/publico")
        public String publicoPost() {
            return "ok";
        }
    }
}
