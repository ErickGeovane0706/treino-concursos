package br.com.treinoconcursos.dto;

import br.com.treinoconcursos.model.Questao;
import br.com.treinoconcursos.model.StatusQuestao;
import br.com.treinoconcursos.model.TipoQuestao;

import java.time.LocalDateTime;

/** Linha da lista de questoes do painel. */
public record QuestaoResumoResponse(Integer id, String trecho, String nomeMateria, TipoQuestao tipo,
        StatusQuestao status, boolean anulada, boolean desatualizada, LocalDateTime atualizadoEm) {

    static final int TAMANHO_TRECHO = 120;

    public static QuestaoResumoResponse de(Questao q) {
        String enunciado = q.getEnunciado().strip().replaceAll("\s+", " ");
        String trecho = enunciado.length() <= TAMANHO_TRECHO ? enunciado
                : enunciado.substring(0, TAMANHO_TRECHO) + "…";
        return new QuestaoResumoResponse(q.getId(), trecho, q.getMateria().getNome(), q.getTipo(), q.getStatus(),
                q.isAnulada(), q.isDesatualizada(), q.getAtualizadoEm());
    }
}
