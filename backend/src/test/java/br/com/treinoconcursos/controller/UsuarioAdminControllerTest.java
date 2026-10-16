package br.com.treinoconcursos.controller;

import br.com.treinoconcursos.IntegracaoTest;
import br.com.treinoconcursos.model.Perfil;
import br.com.treinoconcursos.model.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Manutencao de usuarios pelo ADMIN (US13, RF01 RN1, RN2 e RN4).
 */
class UsuarioAdminControllerTest extends IntegracaoTest {

    private static final String ADMIN = "admin@exemplo.com";
    private static final String EDITOR = "editor@exemplo.com";

    @Test
    void semLoginDevolve401() throws Exception {
        mockMvc.perform(get("/api/admin/usuarios"))
                .andExpect(status().isUnauthorized());
    }

    /** RF01 RN2: so o ADMIN gerencia usuarios. */
    @Test
    void editorRecebe403() throws Exception {
        criarUsuario(EDITOR, Perfil.EDITOR);

        mockMvc.perform(get("/api/admin/usuarios").session(login(EDITOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminListaUsuariosPaginados() throws Exception {
        criarUsuario(ADMIN, Perfil.ADMIN);
        criarUsuario(EDITOR, Perfil.EDITOR);

        mockMvc.perform(get("/api/admin/usuarios").session(login(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].email").value(ADMIN))
                .andExpect(jsonPath("$.content[0].senhaHash").doesNotExist())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.size").value(20));
    }

    /** RF01 RN1: contas sao criadas so pelo ADMIN. */
    @Test
    void adminCriaUsuarioComEmailEmMinusculas() throws Exception {
        criarUsuario(ADMIN, Perfil.ADMIN);

        mockMvc.perform(post("/api/admin/usuarios").session(login(ADMIN)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Nova Editora", "email": "Nova@Exemplo.com",
                                 "senha": "senha-forte-123", "perfil": "EDITOR"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/admin/usuarios/\\d+")))
                .andExpect(jsonPath("$.email").value("nova@exemplo.com"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.criadoEm").isNotEmpty())
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.senhaHash").doesNotExist());

        tentarLogin("nova@exemplo.com", "senha-forte-123").andExpect(status().isOk());
    }

    @Test
    void emailRepetidoDevolve409() throws Exception {
        criarUsuario(ADMIN, Perfil.ADMIN);
        criarUsuario(EDITOR, Perfil.EDITOR);

        mockMvc.perform(post("/api/admin/usuarios").session(login(ADMIN)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Repetido", "email": "EDITOR@exemplo.com",
                                 "senha": "senha-forte-123", "perfil": "EDITOR"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Já existe um usuário com este e-mail."));
    }

    @Test
    void senhaCurtaDevolve400() throws Exception {
        criarUsuario(ADMIN, Perfil.ADMIN);

        mockMvc.perform(post("/api/admin/usuarios").session(login(ADMIN)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Curta", "email": "curta@exemplo.com", "senha": "1234567", "perfil": "EDITOR"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criarSemTokenCsrfEhRecusado() throws Exception {
        criarUsuario(ADMIN, Perfil.ADMIN);

        mockMvc.perform(post("/api/admin/usuarios").session(login(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void usuarioInexistenteDevolve404() throws Exception {
        criarUsuario(ADMIN, Perfil.ADMIN);

        mockMvc.perform(get("/api/admin/usuarios/999999").session(login(ADMIN)))
                .andExpect(status().isNotFound());
    }

    /** RF01 RN4 */
    @Test
    void naoInativaOUltimoAdminAtivo() throws Exception {
        Usuario admin = criarUsuario(ADMIN, Perfil.ADMIN);

        mockMvc.perform(patch("/api/admin/usuarios/{id}/inativar", admin.getId()).session(login(ADMIN)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("O último administrador ativo não pode ser inativado."));
    }

    /** RF01 RN4 */
    @Test
    void naoRebaixaOUltimoAdminAtivo() throws Exception {
        Usuario admin = criarUsuario(ADMIN, Perfil.ADMIN);

        mockMvc.perform(put("/api/admin/usuarios/{id}", admin.getId()).session(login(ADMIN)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"Admin\", \"email\": \"" + ADMIN + "\", \"perfil\": \"EDITOR\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("O último administrador ativo não pode ter o perfil rebaixado."));
    }

    /** RF01 RN4: com outro ADMIN ativo, inativar e permitido. */
    @Test
    void inativaAdminQuandoHaOutroAdminAtivo() throws Exception {
        criarUsuario(ADMIN, Perfil.ADMIN);
        Usuario outro = criarUsuario("outro-admin@exemplo.com", Perfil.ADMIN);

        mockMvc.perform(patch("/api/admin/usuarios/{id}/inativar", outro.getId()).session(login(ADMIN)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
    }

    /** Admin inativo nao conta como "ultimo ativo": pode ser rebaixado. */
    @Test
    void rebaixaAdminInativo() throws Exception {
        criarUsuario(ADMIN, Perfil.ADMIN);
        Usuario inativo = criarUsuario("inativo@exemplo.com", Perfil.ADMIN);
        inativo.setAtivo(false);
        usuarioRepository.save(inativo);

        mockMvc.perform(put("/api/admin/usuarios/{id}", inativo.getId()).session(login(ADMIN)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"Ex-admin\", \"email\": \"inativo@exemplo.com\", \"perfil\": \"EDITOR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil").value("EDITOR"));
    }

    @Test
    void reativaUsuario() throws Exception {
        criarUsuario(ADMIN, Perfil.ADMIN);
        Usuario editor = criarUsuario(EDITOR, Perfil.EDITOR);
        editor.setAtivo(false);
        usuarioRepository.save(editor);

        mockMvc.perform(patch("/api/admin/usuarios/{id}/reativar", editor.getId()).session(login(ADMIN)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(true));
        tentarLogin(EDITOR, SENHA).andExpect(status().isOk());
    }

    @Test
    void desbloqueiaConta() throws Exception {
        criarUsuario(ADMIN, Perfil.ADMIN);
        Usuario editor = criarUsuario(EDITOR, Perfil.EDITOR);
        editor.setBloqueadoAte(LocalDateTime.now(relogio).plusMinutes(10));
        usuarioRepository.save(editor);
        tentarLogin(EDITOR, SENHA).andExpect(status().isLocked());

        mockMvc.perform(patch("/api/admin/usuarios/{id}/desbloquear", editor.getId()).session(login(ADMIN)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bloqueadoAte").value(nullValue()));

        tentarLogin(EDITOR, SENHA).andExpect(status().isOk());
    }

    @Test
    void redefineSenha() throws Exception {
        criarUsuario(ADMIN, Perfil.ADMIN);
        Usuario editor = criarUsuario(EDITOR, Perfil.EDITOR);

        mockMvc.perform(patch("/api/admin/usuarios/{id}/redefinir-senha", editor.getId())
                        .session(login(ADMIN)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"novaSenha\": \"nova-senha-forte\"}"))
                .andExpect(status().isOk());

        tentarLogin(EDITOR, SENHA).andExpect(status().isUnauthorized());
        tentarLogin(EDITOR, "nova-senha-forte").andExpect(status().isOk());
    }

    /** Rebaixado durante a sessao perde o acesso de ADMIN na proxima requisicao. */
    @Test
    void adminRebaixadoPerdeAcessoNaHora() throws Exception {
        criarUsuario(ADMIN, Perfil.ADMIN);
        Usuario outro = criarUsuario("outro-admin@exemplo.com", Perfil.ADMIN);
        MockHttpSession sessaoDoOutro = login("outro-admin@exemplo.com");

        mockMvc.perform(get("/api/admin/usuarios").session(sessaoDoOutro)).andExpect(status().isOk());

        outro.setPerfil(Perfil.EDITOR);
        usuarioRepository.save(outro);

        mockMvc.perform(get("/api/admin/usuarios").session(sessaoDoOutro)).andExpect(status().isForbidden());
        assertThat(usuarioRepository.findById(outro.getId()).orElseThrow().getPerfil()).isEqualTo(Perfil.EDITOR);
    }
}
