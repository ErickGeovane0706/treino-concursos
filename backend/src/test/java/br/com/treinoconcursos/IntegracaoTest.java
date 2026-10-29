package br.com.treinoconcursos;

import br.com.treinoconcursos.model.Perfil;
import br.com.treinoconcursos.model.Usuario;
import br.com.treinoconcursos.repository.UsuarioRepository;
import br.com.treinoconcursos.service.ClassificacaoCacheService;
import br.com.treinoconcursos.service.LimitadorPorIpService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
 *
 * Perfil "test": sem os dados de exemplo do perfil dev. Cada teste comeca
 * com o banco vazio. O admin inicial fica desligado: os testes nao dependem
 * do backend/.env de quem esta rodando, e o mesmo vale para o servidor do
 * Vite e o contato (valores fixos, para os templates sairem iguais em toda maquina).
 */
@SpringBootTest(properties = {"app.admin-inicial.email=", "app.admin-inicial.senha=",
        "app.vite-dev-server=http://vite.teste", "app.contato.email=contato@teste.com.br",
        "app.contato.responsavel=Equipe de Teste"})
@ActiveProfiles("test")
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

    @Autowired
    protected LimitadorPorIpService limitadorPorIpService;

    @Autowired
    protected ClassificacaoCacheService classificacaoCacheService;

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void limparBanco() {
        jdbc.execute("TRUNCATE reportes_erro, alternativas, questoes, provas, orgaos, bancas, assuntos, materias, usuarios "
                + "RESTART IDENTITY CASCADE");
        relogio.reiniciar();
        limitadorPorIpService.limpar();
        classificacaoCacheService.invalidar();
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
