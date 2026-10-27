package br.com.treinoconcursos.controller;

import br.com.treinoconcursos.dto.EncerrarReporteRequest;
import br.com.treinoconcursos.dto.ReporteErroResponse;
import br.com.treinoconcursos.model.MotivoReporte;
import br.com.treinoconcursos.model.StatusReporte;
import br.com.treinoconcursos.seguranca.UsuarioAutenticado;
import br.com.treinoconcursos.service.ReporteErroService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fila e tratamento de reportes de erro no painel administrativo (RF16 RN4-RN5, US12).
 */
@Tag(name = "Reportes de erro (painel)", description = "Fila com prioridade para 3+ reportes de gabarito errado e encerramento (RF16, US12)")
@RestController
@RequestMapping("/api/admin/reportes-erro")
public class ReporteErroAdminController {

    private final ReporteErroService service;

    public ReporteErroAdminController(ReporteErroService service) {
        this.service = service;
    }

    @Operation(
            summary = "Listar fila de reportes de erro",
            description = "Perfil: EDITOR. Exibe no topo as questões com 3 ou mais reportes abertos de GABARITO_ERRADO (RF16 RN5).")
    @ApiResponse(responseCode = "200", description = "Página da fila de reportes")
    @GetMapping
    public Page<ReporteErroResponse> listar(
            @RequestParam(required = false) StatusReporte status,
            @RequestParam(required = false) MotivoReporte motivo,
            @RequestParam(required = false) Integer idQuestao,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.listarFila(status, motivo, idQuestao, pageable);
    }

    @Operation(summary = "Buscar reporte por id", description = "Perfil: EDITOR")
    @ApiResponse(responseCode = "200", description = "Reporte encontrado")
    @ApiResponse(responseCode = "404", description = "Reporte não encontrado")
    @GetMapping("/{id}")
    public ReporteErroResponse buscar(@PathVariable Integer id) {
        return service.buscar(id);
    }

    @Operation(
            summary = "Marcar reporte como resolvido",
            description = "Perfil: EDITOR. Encerra o reporte como RESOLVIDO com observação opcional (RF16 RN4).")
    @ApiResponse(responseCode = "200", description = "Reporte resolvido")
    @ApiResponse(responseCode = "404", description = "Reporte não encontrado")
    @ApiResponse(responseCode = "409", description = "Reporte já encerrado")
    @PatchMapping("/{id}/resolver")
    public ReporteErroResponse resolver(
            @PathVariable Integer id,
            @Valid @RequestBody(required = false) EncerrarReporteRequest request,
            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return service.resolver(id, request, usuario.id());
    }

    @Operation(
            summary = "Descartar reporte",
            description = "Perfil: EDITOR. Encerra o reporte como DESCARTADO com observação opcional (RF16 RN4).")
    @ApiResponse(responseCode = "200", description = "Reporte descartado")
    @ApiResponse(responseCode = "404", description = "Reporte não encontrado")
    @ApiResponse(responseCode = "409", description = "Reporte já encerrado")
    @PatchMapping("/{id}/descartar")
    public ReporteErroResponse descartar(
            @PathVariable Integer id,
            @Valid @RequestBody(required = false) EncerrarReporteRequest request,
            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return service.descartar(id, request, usuario.id());
    }
}
