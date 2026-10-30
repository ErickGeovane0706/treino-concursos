package br.com.treinoconcursos.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;

/** Dados para encerrar um reporte como RESOLVIDO ou DESCARTADO (RF16 RN4, US12). */
public record EncerrarReporteRequest(
        @JsonAlias("observacaoResolucao")
        @Size(max = 1000)
        String observacao) {
}
