package br.com.treinoconcursos.exception;

/**
 * Lancada quando um recurso buscado pelo id nao existe no banco
 * (ex.: GET /api/admin/questoes/999).
 *
 * O GlobalExceptionHandler converte esta excecao em 404 Not Found.
 *
 * Uso no service:
 *   throw new RecursoNaoEncontradoException("Questão não encontrada.");
 */
public class RecursoNaoEncontradoException extends RuntimeException {

    /**
     * @param mensagem texto exibido ao usuario, em portugues. Vai para o
     *                 campo "detail" da resposta de erro.
     */
    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
