package br.com.treinoconcursos.controller;

import br.com.treinoconcursos.config.SecurityConfig;
import br.com.treinoconcursos.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PainelSpaController.class)
@Import(SecurityConfig.class)
class PainelSpaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    @Test
    void rotasDoPainelEncaminhamParaIndexHtmlDaSpa() throws Exception {
        for (String rota : new String[]{"/admin", "/admin/", "/admin/questoes", "/admin/materias/3/assuntos/novo"}) {
            mockMvc.perform(get(rota))
                    .andExpect(status().isOk())
                    .andExpect(forwardedUrl("/admin/index.html"));
        }
    }
}
