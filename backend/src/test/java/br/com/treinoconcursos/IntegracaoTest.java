package br.com.treinoconcursos;

import br.com.treinoconcursos.model.Perfil;
import br.com.treinoconcursos.model.Usuario;
import br.com.treinoconcursos.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base dos testes de integracao: aplicacao inteira + PostgreSQL do
 * Testcontainers + MockMvc. Todas as classes filhas usam a mesma
 * configuracao, entao o Spring sobe o contexto uma vez so.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, IntegracaoTest.RelogioTeste.class})
public abstract class IntegracaoTest {

    protected static final String SENHA = "senha-de-teste-1";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected UsuarioRepository usuarioRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected RelogioAjustavel relogio;

    @BeforeEach
    void limparBanco() {
        usuarioRepository.deleteAll();
        relogio.reiniciar();
    }

    protected Usuario criarUsuario(String email, Perfil perfil) {
        Usuario usuario = new Usuario();
        usuario.setNome("Usuário " + perfil);
        usuario.setEmail(email);
        usuario.setSenhaHash(passwordEncoder.encode(SENHA));
        usuario.setPerfil(perfil);
        return usuarioRepository.save(usuario);
    }

    protected ResultActions tentarLogin(String email, String senha) throws Exception {
        return mockMvc.perform(post("/api/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + email + "\", \"senha\": \"" + senha + "\"}"));
    }

    /** Faz login e devolve a sessao, para usar nas proximas requisicoes. */
    protected MockHttpSession login(String email) throws Exception {
        return (MockHttpSession) tentarLogin(email, SENHA)
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RelogioTeste {

        @Bean
        @Primary
        RelogioAjustavel relogioAjustavel() {
            return new RelogioAjustavel();
        }
    }
}
