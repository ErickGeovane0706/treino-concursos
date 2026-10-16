package br.com.treinoconcursos.exception;

/**
 * E-mail ou senha errados, ou conta inativa. O GlobalExceptionHandler converte
 * em 401. A mensagem e sempre a mesma, para nao revelar quais e-mails existem.
 */
public class CredenciaisInvalidasException extends RuntimeException {

    public CredenciaisInvalidasException() {
        super("E-mail ou senha inválidos.");
    }
}
