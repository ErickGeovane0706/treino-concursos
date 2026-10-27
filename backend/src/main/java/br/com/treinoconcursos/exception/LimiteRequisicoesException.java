package br.com.treinoconcursos.exception;

/**
 * Lancada quando um mesmo endereco IP ultrapassa o limite de requisicoes
 * permitido na janela de tempo controlada em memoria (RF16 RN3, RF17 RN4, RNF08).
 * Convertida em 429 Too Many Requests pelo GlobalExceptionHandler.
 */
public class LimiteRequisicoesException extends RuntimeException {

    public LimiteRequisicoesException(String mensagem) {
        super(mensagem);
    }
}
