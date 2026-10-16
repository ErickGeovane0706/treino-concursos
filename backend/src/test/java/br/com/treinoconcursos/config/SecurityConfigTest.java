package br.com.treinoconcursos.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Garante a seguranca minima do esqueleto: area publica aberta, painel
 * fechado (401) ate a issue #2 e CSRF ligado.
 */
@WebMvcTest
@Import({SecurityConfig.class, SecurityConfigTest.ControllerFalso.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rotaPublicaFicaAbertaSemLogin() throws Exception {
        mockMvc.perform(get("/api/publico"))
                .andExpect(status().isOk());
    }

    @Test
    void apiDoPainelExigeAutenticacao() throws Exception {
        mockMvc.perform(get("/api/admin/questoes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void painelExigeAutenticacao() throws Exception {
        mockMvc.perform(get("/admin/"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postSemTokenCsrfEhRecusado() throws Exception {
        mockMvc.perform(post("/api/publico"))
                .andExpect(status().isForbidden());
    }

    @RestController
    static class ControllerFalso {

        @GetMapping("/api/publico")
        public String publico() {
            return "ok";
        }

        @PostMapping("/api/publico")
        public String publicoPost() {
            return "ok";
        }

        @GetMapping({"/api/admin/questoes", "/admin/"})
        public String admin() {
            return "nao deveria chegar aqui";
        }
    }
}
