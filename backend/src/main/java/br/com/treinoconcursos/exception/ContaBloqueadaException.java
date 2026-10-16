package br.com.treinoconcursos.exception;

/**
 * Conta bloqueada por senhas erradas seguidas (RF01 RN3). O
 * GlobalExceptionHandler converte em 423 Locked.
 */
public class ContaBloqueadaException extends RuntimeException {

    public ContaBloqueadaException(String mensagem) {
        super(mensagem);
    }
}
