package br.com.treinoconcursos.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Tratamento centralizado de erros da API JSON, no formato Problem Details
 * (RFC 9457), conforme doc/api.md (secao 6).
 *
 * Como funciona:
 * 1. O @RestControllerAdvice vigia apenas os @RestController (API). As
 *    paginas Thymeleaf tem templates de erro proprios, sem anuncio.
 * 2. Estender ResponseEntityExceptionHandler faz os erros padrao do Spring MVC
 *    (JSON mal formado, metodo nao suportado, etc.) tambem sairem como
 *    ProblemDetail, com o status certo.
 * 3. Os services so lancam a excecao certa; este handler monta a resposta.
 */
@RestControllerAdvice(annotations = RestController.class)
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /** Recurso inexistente -> 404 Not Found, com a mensagem do service. */
    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ProblemDetail tratarRecursoNaoEncontrado(RecursoNaoEncontradoException ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problema.setTitle("Recurso não encontrado");
        return problema;
    }

    /** Regra de negocio violada -> 409 Conflict, com a mensagem do service. */
    @ExceptionHandler(RegraNegocioException.class)
    public ProblemDetail tratarRegraNegocio(RegraNegocioException ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problema.setTitle("Violação de regra de negócio");
        return problema;
    }

    /** E-mail ou senha errados no login -> 401, sempre com a mesma mensagem. */
    @ExceptionHandler(CredenciaisInvalidasException.class)
    public ProblemDetail tratarCredenciaisInvalidas(CredenciaisInvalidasException ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problema.setTitle("Não autenticado");
        return problema;
    }

    /** Conta bloqueada por senhas erradas (RF01 RN3) -> 423 Locked. */
    @ExceptionHandler(ContaBloqueadaException.class)
    public ProblemDetail tratarContaBloqueada(ContaBloqueadaException ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.LOCKED, ex.getMessage());
        problema.setTitle("Conta bloqueada");
        return problema;
    }

    /**
     * Falha no @Valid de um DTO -> 400 Bad Request com mensagem fixa.
     *
     * A mensagem original da excecao e tecnica, em ingles e expoe nomes
     * internos. Qual campo esta errado quem mostra e o formulario do painel.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Um ou mais campos estão inválidos.");
        problema.setTitle("Validação de argumentos");
        return handleExceptionInternal(ex, problema, headers, HttpStatus.BAD_REQUEST, request);
    }
}
