package br.com.treinoconcursos.dto;

import br.com.treinoconcursos.model.Questao;
import br.com.treinoconcursos.model.StatusQuestao;

/** Questao dentro do simulado, com o que a torna inelegivel quando for o caso (RF14 RN1 e RN4). */
public record QuestaoDoSimuladoResponse(int ordem, Integer id, String trecho, String nomeMateria,
        StatusQuestao status, boolean anulada, boolean desatualizada, boolean elegivel) {

    public static QuestaoDoSimuladoResponse de(int ordem, Questao q) {
        return new QuestaoDoSimuladoResponse(ordem, q.getId(), QuestaoResumoResponse.de(q).trecho(),
                q.getMateria().getNome(), q.getStatus(), q.isAnulada(), q.isDesatualizada(), elegivel(q));
    }

    /** RF14 RN1: publicada, nao anulada e nao desatualizada. */
    public static boolean elegivel(Questao q) {
        return q.getStatus() == StatusQuestao.PUBLICADA && !q.isAnulada() && !q.isDesatualizada();
    }
}
