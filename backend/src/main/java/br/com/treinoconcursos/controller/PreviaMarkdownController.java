package br.com.treinoconcursos.controller;

import br.com.treinoconcursos.dto.PreviaMarkdownRequest;
import br.com.treinoconcursos.dto.PreviaMarkdownResponse;
import br.com.treinoconcursos.service.MarkdownService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Prévia de Markdown (painel)", description = "Markdown → HTML sanitizado, igual ao do site (RF04 RN4)")
@RestController
public class PreviaMarkdownController {

    private final MarkdownService markdownService;

    public PreviaMarkdownController(MarkdownService markdownService) {
        this.markdownService = markdownService;
    }

    @Operation(summary = "Converter Markdown em HTML", description = "Perfil: EDITOR")
    @PostMapping("/api/admin/previa-markdown")
    public PreviaMarkdownResponse converter(@Valid @RequestBody PreviaMarkdownRequest request) {
        return new PreviaMarkdownResponse(markdownService.paraHtml(request.markdown()));
    }
}
