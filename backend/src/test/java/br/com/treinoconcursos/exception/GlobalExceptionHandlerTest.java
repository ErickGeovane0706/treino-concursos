package br.com.treinoconcursos.exception;

import br.com.treinoconcursos.config.SecurityConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa se o GlobalExceptionHandler converte cada excecao na resposta HTTP
 * certa (status, "title" e "detail" no formato Problem Details).
 *
 * O @WebMvcTest sobe so a camada web, sem banco, entao nao precisa do Docker.
 * Como ainda nao ha controllers reais, o ControllerFalso abaixo so lanca as
 * excecoes, simulando um service.
 */
@WebMvcTest
@Import({SecurityConfig.class, GlobalExceptionHandlerTest.ControllerFalso.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deveRetornar404QuandoRecursoNaoEncontrado() throws Exception {
        mockMvc.perform(get("/api/teste/recurso-nao-encontrado"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Recurso não encontrado"))
                .andExpect(jsonPath("$.detail").value("Questão não encontrada."))
                .andExpect(jsonPath("$.instance").value("/api/teste/recurso-nao-encontrado"));
    }

    @Test
    void deveRetornar409QuandoRegraNegocioViolada() throws Exception {
        mockMvc.perform(get("/api/teste/regra-negocio-violada"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Violação de regra de negócio"))
                .andExpect(jsonPath("$.detail").value("Duas alternativas corretas."));
    }

    /** Campo obrigatorio vazio: passa no @NotNull, mas falha no @NotBlank. */
    @Test
    void deveRetornar400ComMensagemFixaQuandoFalhaValidacao() throws Exception {
        mockMvc.perform(post("/api/teste/validacao").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"campoObrigatorio\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validação de argumentos"))
                .andExpect(jsonPath("$.detail").value("Um ou mais campos estão inválidos."));
    }

    /** Erros padrao do Spring MVC tambem saem como Problem Details. */
    @Test
    void deveRetornar400EmProblemDetailsQuandoJsonMalFormado() throws Exception {
        mockMvc.perform(post("/api/teste/validacao").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{nao e json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @RestController
    static class ControllerFalso {

        @GetMapping("/api/teste/recurso-nao-encontrado")
        public void recursoNaoEncontrado() {
            throw new RecursoNaoEncontradoException("Questão não encontrada.");
        }

        @GetMapping("/api/teste/regra-negocio-violada")
        public void regraNegocioViolada() {
            throw new RegraNegocioException("Duas alternativas corretas.");
        }

        @PostMapping("/api/teste/validacao")
        public void validacao(@Valid @RequestBody DtoFalso dto) {
        }
    }

    record DtoFalso(@NotBlank String campoObrigatorio) {
    }
}
