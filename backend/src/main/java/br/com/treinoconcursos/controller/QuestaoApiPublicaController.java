package br.com.treinoconcursos.controller;

import br.com.treinoconcursos.dto.EstatisticasQuestaoResponse;
import br.com.treinoconcursos.dto.QuestoesIdsResponse;
import br.com.treinoconcursos.dto.ReporteErroPublicoResponse;
import br.com.treinoconcursos.dto.ReporteErroRequest;
import br.com.treinoconcursos.dto.RespostaQuestaoRequest;
import br.com.treinoconcursos.service.EstatisticasQuestaoService;
import br.com.treinoconcursos.service.LimitadorPorIpService;
import br.com.treinoconcursos.service.NavegacaoQuestoesService;
import br.com.treinoconcursos.service.ReporteErroService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints JSON publicos de navegacao, estatisticas anonimas e reportes de erro das questoes (RF07, RF10, RF16, RF17).
 */
@Tag(name = "Questões (público)", description = "Navegação em ordem fixa por cursor, estatísticas anônimas e reportes de erro (RF07, RF10, RF16, RF17)")
@RestController
@RequestMapping("/api/questoes")
public class QuestaoApiPublicaController {

    private final NavegacaoQuestoesService navegacaoService;
    private final EstatisticasQuestaoService estatisticasService;
    private final ReporteErroService reporteErroService;

    public QuestaoApiPublicaController(
            NavegacaoQuestoesService navegacaoService,
            EstatisticasQuestaoService estatisticasService,
            ReporteErroService reporteErroService) {
        this.navegacaoService = navegacaoService;
        this.estatisticasService = estatisticasService;
        this.reporteErroService = reporteErroService;
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

    @Operation(summary = "Registrar resposta anônima da questão", description = "Perfil: público")
    @ApiResponse(responseCode = "202", description = "Resposta anônima contabilizada")
    @ApiResponse(responseCode = "400", description = "Letra inválida")
    @ApiResponse(responseCode = "404", description = "Questão não encontrada ou não publicada")
    @ApiResponse(responseCode = "409", description = "Alternativa não pertence à questão")
    @ApiResponse(responseCode = "429", description = "Limite de requisições por IP excedido")
    @PostMapping("/{id}/respostas")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void registrarResposta(
            @PathVariable Integer id,
            @Valid @RequestBody RespostaQuestaoRequest request,
            HttpServletRequest httpRequest) {
        estatisticasService.registrarResposta(id, request, LimitadorPorIpService.extrairIp(httpRequest));
    }

    @Operation(summary = "Consultar estatísticas anônimas da questão", description = "Perfil: público")
    @ApiResponse(responseCode = "200", description = "Estatísticas da questão (percentuais exibidos com 30 ou mais respostas)")
    @ApiResponse(responseCode = "404", description = "Questão não encontrada ou não publicada")
    @GetMapping("/{id}/estatisticas")
    public EstatisticasQuestaoResponse buscarEstatisticas(@PathVariable Integer id) {
        return estatisticasService.buscarEstatisticas(id);
    }

    @Operation(summary = "Reportar erro em questão publicada", description = "Perfil: público. Sem coleta de dados pessoais (RF16 RN1-RN3, US07).")
    @ApiResponse(responseCode = "201", description = "Reporte registrado com status ABERTO")
    @ApiResponse(responseCode = "400", description = "Motivo ausente/inválido ou descrição acima de 1.000 caracteres")
    @ApiResponse(responseCode = "404", description = "Questão não encontrada ou não publicada")
    @ApiResponse(responseCode = "429", description = "Limite de 5 reportes por hora por IP excedido")
    @PostMapping("/{id}/reportes")
    @ResponseStatus(HttpStatus.CREATED)
    public ReporteErroPublicoResponse registrarReporte(
            @PathVariable Integer id,
            @Valid @RequestBody ReporteErroRequest request,
            HttpServletRequest httpRequest) {
        return reporteErroService.registrarReporte(id, request, LimitadorPorIpService.extrairIp(httpRequest));
    }
}
