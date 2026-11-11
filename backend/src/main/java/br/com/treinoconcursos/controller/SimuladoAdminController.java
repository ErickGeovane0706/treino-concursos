package br.com.treinoconcursos.controller;

import br.com.treinoconcursos.dto.SimuladoRequest;
import br.com.treinoconcursos.dto.SimuladoResponse;
import br.com.treinoconcursos.dto.SimuladoResumoResponse;
import br.com.treinoconcursos.model.StatusSimulado;
import br.com.treinoconcursos.seguranca.UsuarioAutenticado;
import br.com.treinoconcursos.service.SimuladoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Tag(name = "Simulados (painel)", description = "Montagem e ciclo de vida dos simulados (RF14, US11)")
@RestController
@RequestMapping("/api/admin/simulados")
public class SimuladoAdminController {

    private final SimuladoService service;

    public SimuladoAdminController(SimuladoService service) {
        this.service = service;
    }

    @Operation(summary = "Listar simulados", description = "Perfil: EDITOR. Filtros opcionais: status e título.")
    @GetMapping
    public Page<SimuladoResumoResponse> listar(@RequestParam(required = false) StatusSimulado status,
            @RequestParam(required = false) String busca,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.listar(status, busca, pageable);
    }

    @Operation(summary = "Buscar por id", description = "Perfil: EDITOR. Inclui as questões na ordem.")
    @ApiResponse(responseCode = "404", description = "Não encontrado")
    @GetMapping("/{id}")
    public SimuladoResponse buscar(@PathVariable Integer id) {
        return service.buscar(id);
    }

    @Operation(summary = "Cadastrar (como rascunho)", description = "Perfil: EDITOR")
    @ApiResponse(responseCode = "201", description = "Cadastrado")
    @ApiResponse(responseCode = "409", description = "Slug em uso, questão repetida ou não elegível (RF14 RN1 e RN2)")
    @PostMapping
    public ResponseEntity<SimuladoResponse> criar(@Valid @RequestBody SimuladoRequest request,
            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        SimuladoResponse criado = service.criar(request, usuario.id());
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(location).body(criado);
    }

    @Operation(summary = "Alterar", description = "Perfil: EDITOR. Substitui as questões pela lista enviada, "
            + "na ordem. Publicado exige de 5 a 100 questões elegíveis (RF14 RN1) e deixa de precisar de revisão.")
    @ApiResponse(responseCode = "409", description = "Regra de negócio")
    @PutMapping("/{id}")
    public SimuladoResponse atualizar(@PathVariable Integer id, @Valid @RequestBody SimuladoRequest request) {
        return service.atualizar(id, request);
    }

    @Operation(summary = "Excluir rascunho", description = "Perfil: EDITOR. Só se nunca foi publicado.")
    @ApiResponse(responseCode = "204", description = "Excluído")
    @ApiResponse(responseCode = "409", description = "Já foi publicado: arquive")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Integer id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Publicar", description = "Perfil: EDITOR. Exige de 5 a 100 questões elegíveis (RF14 RN1).")
    @ApiResponse(responseCode = "409", description = "Já publicado ou questões insuficientes")
    @PatchMapping("/{id}/publicar")
    public SimuladoResponse publicar(@PathVariable Integer id) {
        return service.publicar(id);
    }

    @Operation(summary = "Arquivar", description = "Perfil: EDITOR. O simulado sai do site (RF14 RN3).")
    @ApiResponse(responseCode = "409", description = "Já arquivado")
    @PatchMapping("/{id}/arquivar")
    public SimuladoResponse arquivar(@PathVariable Integer id) {
        return service.arquivar(id);
    }
}
