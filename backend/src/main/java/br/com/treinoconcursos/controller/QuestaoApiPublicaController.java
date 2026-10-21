package br.com.treinoconcursos.controller;

import br.com.treinoconcursos.dto.QuestoesIdsResponse;
import br.com.treinoconcursos.service.NavegacaoQuestoesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints JSON publicos de navegacao das questoes (RF07, RF10).
 */
@Tag(name = "Questões (público)", description = "Navegação em ordem fixa por cursor (RF07, RF10)")
@RestController
@RequestMapping("/api/questoes")
public class QuestaoApiPublicaController {

    private final NavegacaoQuestoesService navegacaoService;

    public QuestaoApiPublicaController(NavegacaoQuestoesService navegacaoService) {
        this.navegacaoService = navegacaoService;
    }

    @Operation(summary = "Listar ids de questões publicadas do filtro em ordem crescente", description = "Perfil: público")
    @ApiResponse(responseCode = "200", description = "Ids publicados em ordem crescente e próximo cursor")
    @GetMapping("/ids")
    public QuestoesIdsResponse listarIds(
            @RequestParam(required = false) String materia,
            @RequestParam(required = false) String assunto,
            @RequestParam(required = false) Integer depoisDe,
            @RequestParam(required = false) Integer limite) {
        return navegacaoService.buscarIds(materia, assunto, depoisDe, limite);
    }
}
