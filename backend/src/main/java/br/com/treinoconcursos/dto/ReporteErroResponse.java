package br.com.treinoconcursos.dto;

import br.com.treinoconcursos.model.MotivoReporte;
import br.com.treinoconcursos.model.ReporteErro;
import br.com.treinoconcursos.model.StatusReporte;

import java.time.LocalDateTime;

/** Item da fila de reportes de erro no painel administrativo (RF16 RN4-RN5, US12). */
public record ReporteErroResponse(
        Integer id,
        Integer idQuestao,
        String trechoQuestao,
        String nomeMateria,
        MotivoReporte motivo,
        String descricao,
        StatusReporte status,
        boolean prioritario,
        long reportesGabaritoAbertos,
        LocalDateTime criadoEm,
        Integer idResolvidoPor,
        String nomeResolvidoPor,
        LocalDateTime resolvidoEm,
        String observacaoResolucao) {

    static final int TAMANHO_TRECHO = 120;
    public static final int MINIMO_GABARITO_PARA_PRIORIDADE = 3;

    public static ReporteErroResponse de(ReporteErro r, long reportesGabaritoAbertos) {
        String enunciado = r.getQuestao().getEnunciado().strip().replaceAll("\\s+", " ");
        String trecho = enunciado.length() <= TAMANHO_TRECHO
                ? enunciado
                : enunciado.substring(0, TAMANHO_TRECHO) + "…";
        boolean prioritario = r.getStatus() == StatusReporte.ABERTO
                && reportesGabaritoAbertos >= MINIMO_GABARITO_PARA_PRIORIDADE;
        return new ReporteErroResponse(
                r.getId(),
                r.getQuestao().getId(),
                trecho,
                r.getQuestao().getMateria().getNome(),
                r.getMotivo(),
                r.getDescricao(),
                r.getStatus(),
                prioritario,
                reportesGabaritoAbertos,
                r.getCriadoEm(),
                r.getResolvidoPor() == null ? null : r.getResolvidoPor().getId(),
                r.getResolvidoPor() == null ? null : r.getResolvidoPor().getNome(),
                r.getResolvidoEm(),
                r.getObservacaoResolucao());
    }
}
