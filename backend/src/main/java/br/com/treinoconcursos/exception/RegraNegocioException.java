package br.com.treinoconcursos.exception;

/**
 * Lancada quando os dados estao em formato valido, mas uma regra do sistema
 * impede a operacao (ex.: duas alternativas corretas, excluir materia com
 * questoes).
 *
 * O GlobalExceptionHandler converte esta excecao em 409 Conflict. Erros de
 * formato (campo vazio, letra invalida) NAO usam esta excecao: sao barrados
 * antes pelo @Valid e viram 400.
 *
 * Uso no service:
 *   throw new RegraNegocioException("Uma questão de múltipla escolha deve ter exatamente uma alternativa correta.");
 */
public class RegraNegocioException extends RuntimeException {

    /**
     * @param mensagem texto exibido ao usuario, em portugues. Vai para o
     *                 campo "detail" da resposta de erro.
     */
    public RegraNegocioException(String mensagem) {
        super(mensagem);
    }
}
