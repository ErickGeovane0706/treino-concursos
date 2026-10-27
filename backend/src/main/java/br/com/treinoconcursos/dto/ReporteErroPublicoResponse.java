package br.com.treinoconcursos.dto;

import br.com.treinoconcursos.model.MotivoReporte;
import br.com.treinoconcursos.model.ReporteErro;
import br.com.treinoconcursos.model.StatusReporte;

import java.time.LocalDateTime;

/** Resposta de confirmacao do reporte registrado pela API publica (RF16, US07). */
public record ReporteErroPublicoResponse(
        Integer id,
        Integer idQuestao,
        MotivoReporte motivo,
        String descricao,
        StatusReporte status,
        LocalDateTime criadoEm) {

    public static ReporteErroPublicoResponse de(ReporteErro r) {
        return new ReporteErroPublicoResponse(
                r.getId(),
                r.getQuestao().getId(),
                r.getMotivo(),
                r.getDescricao(),
                r.getStatus(),
                r.getCriadoEm());
    }
}
