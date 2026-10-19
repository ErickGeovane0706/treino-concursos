package br.com.treinoconcursos.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PreviaMarkdownRequest(@NotNull @Size(max = 100_000) String markdown) {
}
