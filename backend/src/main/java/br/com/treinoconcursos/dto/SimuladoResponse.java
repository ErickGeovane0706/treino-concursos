package br.com.treinoconcursos.dto;

import br.com.treinoconcursos.model.Simulado;
import br.com.treinoconcursos.model.StatusSimulado;

import java.time.LocalDateTime;
import java.util.List;

/** Simulado completo no painel, com as questoes na ordem (US11). */
public record SimuladoResponse(Integer id, String titulo, String slug, String descricao, Integer tempoSugeridoMin,
        StatusSimulado status, boolean precisaRevisao, String nomeAutor, LocalDateTime criadoEm,
        LocalDateTime publicadoEm, List<QuestaoDoSimuladoResponse> questoes) {

    public static SimuladoResponse de(Simulado s, List<QuestaoDoSimuladoResponse> questoes) {
        return new SimuladoResponse(s.getId(), s.getTitulo(), s.getSlug(), s.getDescricao(), s.getTempoSugeridoMin(),
                s.getStatus(), s.isPrecisaRevisao(), s.getAutor().getNome(), s.getCriadoEm(), s.getPublicadoEm(),
                questoes);
    }
}
