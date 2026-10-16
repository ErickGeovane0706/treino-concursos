package br.com.treinoconcursos.controller;

import br.com.treinoconcursos.IntegracaoTest;
import br.com.treinoconcursos.model.Perfil;
import br.com.treinoconcursos.model.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Login por sessao do painel (RF01 RN1 e RN3, RNF01) contra o banco real.
 */
class AuthControllerTest extends IntegracaoTest {

    private static final String EMAIL = "editora@exemplo.com";

    @Test
    void loginCorretoCriaSessaoENaoDevolveOHashDaSenha() throws Exception {
        criarUsuario(EMAIL, Perfil.EDITOR);

        tentarLogin(EMAIL, SENHA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.perfil").value("EDITOR"))
                .andExpect(jsonPath("$.senhaHash").doesNotExist())
                .andExpect(jsonPath("$.senha").doesNotExist());
    }

    @Test
    void euDevolveOUsuarioDaSessao() throws Exception {
        criarUsuario(EMAIL, Perfil.EDITOR);
        MockHttpSession sessao = login(EMAIL);

        mockMvc.perform(get("/api/auth/eu").session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    void euSemSessaoDevolve401() throws Exception {
        mockMvc.perform(get("/api/auth/eu"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void emailEhComparadoSemDiferenciarMaiusculas() throws Exception {
        criarUsuario(EMAIL, Perfil.EDITOR);

        tentarLogin("Editora@Exemplo.COM", SENHA)
                .andExpect(status().isOk());
    }

    @Test
    void senhaErradaDevolve401ComMensagemGenerica() throws Exception {
        criarUsuario(EMAIL, Perfil.EDITOR);

        tentarLogin(EMAIL, "senha-errada")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos."));
    }

    @Test
    void emailInexistenteDevolveAMesmaMensagemDaSenhaErrada() throws Exception {
        tentarLogin("ninguem@exemplo.com", SENHA)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos."));
    }

    @Test
    void usuarioInativoNaoEntra() throws Exception {
        Usuario usuario = criarUsuario(EMAIL, Perfil.EDITOR);
        usuario.setAtivo(false);
        usuarioRepository.save(usuario);

        tentarLogin(EMAIL, SENHA)
                .andExpect(status().isUnauthorized());
    }

    /** RF01 RN3: a 5a senha errada seguida bloqueia por 15 minutos. */
    @Test
    void quintaSenhaErradaBloqueiaAContaPor15Minutos() throws Exception {
        criarUsuario(EMAIL, Perfil.EDITOR);
        for (int i = 1; i <= 4; i++) {
            tentarLogin(EMAIL, "errada-" + i).andExpect(status().isUnauthorized());
        }

        tentarLogin(EMAIL, "errada-5")
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.detail").value(
                        "Conta bloqueada por excesso de tentativas. Tente novamente em 15 minutos."));

        // Nem a senha certa entra durante o bloqueio
        tentarLogin(EMAIL, SENHA).andExpect(status().isLocked());

        relogio.avancar(Duration.ofMinutes(14));
        tentarLogin(EMAIL, SENHA).andExpect(status().isLocked());

        relogio.avancar(Duration.ofMinutes(1).plusSeconds(1));
        tentarLogin(EMAIL, SENHA).andExpect(status().isOk());

        Usuario depois = usuarioRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(depois.getTentativasLogin()).isZero();
        assertThat(depois.getBloqueadoAte()).isNull();
    }

    /** RF01 RN3: "seguidas" - o login correto zera a contagem. */
    @Test
    void loginCorretoZeraAsTentativasErradas() throws Exception {
        criarUsuario(EMAIL, Perfil.EDITOR);
        for (int i = 1; i <= 4; i++) {
            tentarLogin(EMAIL, "errada").andExpect(status().isUnauthorized());
        }
        tentarLogin(EMAIL, SENHA).andExpect(status().isOk());

        for (int i = 1; i <= 4; i++) {
            tentarLogin(EMAIL, "errada").andExpect(status().isUnauthorized());
        }
        assertThat(usuarioRepository.findByEmail(EMAIL).orElseThrow().getTentativasLogin()).isEqualTo(4);
    }

    @Test
    void loginSemTokenCsrfEhRecusado() throws Exception {
        criarUsuario(EMAIL, Perfil.EDITOR);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + EMAIL + "\", \"senha\": \"" + SENHA + "\"}"))
                .andExpect(status().isForbidden());
    }


    @Test
    void logoutEncerraASessao() throws Exception {
        criarUsuario(EMAIL, Perfil.EDITOR);
        MockHttpSession sessao = login(EMAIL);

        mockMvc.perform(post("/api/auth/logout").session(sessao).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/eu").session(sessao))
                .andExpect(status().isUnauthorized());
    }

    /** Quem e inativado perde o acesso na hora, sem esperar a sessao expirar. */
    @Test
    void usuarioInativadoPerdeASessaoNaProximaRequisicao() throws Exception {
        Usuario usuario = criarUsuario(EMAIL, Perfil.EDITOR);
        MockHttpSession sessao = login(EMAIL);

        usuario.setAtivo(false);
        usuarioRepository.save(usuario);

        mockMvc.perform(get("/api/auth/eu").session(sessao))
                .andExpect(status().isUnauthorized());
    }
}
