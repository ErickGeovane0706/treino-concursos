package br.com.treinoconcursos.service;

import br.com.treinoconcursos.dto.EstatisticasQuestaoResponse;
import br.com.treinoconcursos.dto.EstatisticasQuestaoResponse.EstatisticaAlternativaResponse;
import br.com.treinoconcursos.dto.RespostaQuestaoRequest;
import br.com.treinoconcursos.exception.RecursoNaoEncontradoException;
import br.com.treinoconcursos.exception.RegraNegocioException;
import br.com.treinoconcursos.model.Questao;
import br.com.treinoconcursos.model.StatusQuestao;
import br.com.treinoconcursos.repository.AlternativaRepository;
import br.com.treinoconcursos.repository.QuestaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Registro atomico de respostas anonimas e consulta de estatisticas (RF17 RN1-RN4, RNF08).
 *
 * Regras:
 * - RN1: incrementa total_respostas, total_acertos e total_marcacoes via UPDATE ... SET x = x + 1.
 * - RN3: percentuais so sao exibidos quando a questao tem 30 ou mais respostas.
 * - RN4 / RNF08: nenhum identificador ou IP do visitante e gravado no banco; o limite por IP
 *   fica exclusivamente em memoria no LimitadorPorIpService.
 */
@Service
public class EstatisticasQuestaoService {

    /** RF17 RN3: minimo de respostas para exibir os percentuais ao visitante. */
    public static final int MINIMO_RESPOSTAS_PARA_EXIBIR = 30;

    private final QuestaoRepository questaoRepository;
    private final AlternativaRepository alternativaRepository;
    private final LimitadorPorIpService limitadorPorIpService;

    public EstatisticasQuestaoService(QuestaoRepository questaoRepository,
            AlternativaRepository alternativaRepository,
            LimitadorPorIpService limitadorPorIpService) {
        this.questaoRepository = questaoRepository;
        this.alternativaRepository = alternativaRepository;
        this.limitadorPorIpService = limitadorPorIpService;
    }

    /**
     * Registra uma resposta anonima de forma atomica no banco (RF17 RN1, RN4).
     */
    @Transactional
    public void registrarResposta(Integer idQuestao, RespostaQuestaoRequest request, String ip) {
        limitadorPorIpService.verificarResposta(ip);

        StatusQuestao status = questaoRepository.buscarStatusPorId(idQuestao)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Questão não encontrada."));
        if (status != StatusQuestao.PUBLICADA) {
            throw new RecursoNaoEncontradoException("Questão não encontrada.");
        }

        String letra = request.letra().trim();
        boolean correta = alternativaRepository.buscarCorretaPorQuestaoELetra(idQuestao, letra)
                .orElseThrow(() -> new RegraNegocioException(
                        "A alternativa " + letra + " não pertence a esta questão."));

        int atualizadas = questaoRepository.incrementarRespostas(idQuestao, correta);
        if (atualizadas == 0) {
            throw new RecursoNaoEncontradoException("Questão não encontrada.");
        }
        alternativaRepository.incrementarMarcacoes(idQuestao, letra);
    }

    /**
     * Retorna as estatisticas anonimas da questao publicada (RF17 RN3).
     * Abaixo de 30 respostas, disponivel = false, percentualAcertos = null e alternativas = [].
     */
    @Transactional(readOnly = true)
    public EstatisticasQuestaoResponse buscarEstatisticas(Integer idQuestao) {
        Questao questao = questaoRepository.findComAlternativasById(idQuestao)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Questão não encontrada."));
        if (questao.getStatus() != StatusQuestao.PUBLICADA) {
            throw new RecursoNaoEncontradoException("Questão não encontrada.");
        }

        int totalRespostas = questao.getTotalRespostas();
        if (totalRespostas < MINIMO_RESPOSTAS_PARA_EXIBIR) {
            return new EstatisticasQuestaoResponse(false, totalRespostas, null, List.of());
        }

        int percentualAcertos = calcularPercentual(questao.getTotalAcertos(), totalRespostas);
        List<EstatisticaAlternativaResponse> alternativas = questao.getAlternativas().stream()
                .map(alt -> new EstatisticaAlternativaResponse(
                        alt.getLetra(),
                        calcularPercentual(alt.getTotalMarcacoes(), totalRespostas)))
                .toList();

        return new EstatisticasQuestaoResponse(true, totalRespostas, percentualAcertos, alternativas);
    }

    static int calcularPercentual(int parte, int total) {
        if (total <= 0) {
            return 0;
        }
        return (int) Math.round((parte * 100.0) / total);
    }
}
