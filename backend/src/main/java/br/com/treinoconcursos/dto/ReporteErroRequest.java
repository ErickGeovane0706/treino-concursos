package br.com.treinoconcursos.dto;

import br.com.treinoconcursos.model.MotivoReporte;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Envio publico e anonimo de reporte de erro em questao (RF16 RN1 e RN2, US07).
 * Nao coleta nenhum dado pessoal do visitante.
 */
public record ReporteErroRequest(
        @NotNull MotivoReporte motivo,
        @Size(max = 1000) String descricao) {
}
