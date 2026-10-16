package br.com.treinoconcursos.controller;

import br.com.treinoconcursos.dto.OrgaoRequest;
import br.com.treinoconcursos.dto.OrgaoResponse;
import br.com.treinoconcursos.service.OrgaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Tag(name = "Órgãos (painel)", description = "Órgãos dos concursos (RF03, US10)")
@RestController
@RequestMapping("/api/admin/orgaos")
public class OrgaoAdminController {

    private final OrgaoService service;

    public OrgaoAdminController(OrgaoService service) {
        this.service = service;
    }

    @Operation(summary = "Listar órgãos", description = "Perfil: EDITOR. Busca opcional por texto.")
    @GetMapping
    public Page<OrgaoResponse> listar(@RequestParam(required = false) String busca,
            @PageableDefault(size = 20, sort = "sigla", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.listar(busca, pageable);
    }

    @Operation(summary = "Buscar por id", description = "Perfil: EDITOR")
    @ApiResponse(responseCode = "404", description = "Não encontrado")
    @GetMapping("/{id}")
    public OrgaoResponse buscar(@PathVariable Integer id) {
        return service.buscar(id);
    }

    @Operation(summary = "Cadastrar", description = "Perfil: EDITOR")
    @ApiResponse(responseCode = "201", description = "Cadastrado")
    @ApiResponse(responseCode = "409", description = "Duplicidade (nome, sigla, slug ou combinação única)")
    @PostMapping
    public ResponseEntity<OrgaoResponse> criar(@Valid @RequestBody OrgaoRequest request) {
        OrgaoResponse criado = service.criar(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(location).body(criado);
    }

    @Operation(summary = "Alterar", description = "Perfil: EDITOR")
    @ApiResponse(responseCode = "409", description = "Duplicidade ou regra de negócio")
    @PutMapping("/{id}")
    public OrgaoResponse atualizar(@PathVariable Integer id, @Valid @RequestBody OrgaoRequest request) {
        return service.atualizar(id, request);
    }

    @Operation(summary = "Excluir", description = "Perfil: EDITOR")
    @ApiResponse(responseCode = "204", description = "Excluído")
    @ApiResponse(responseCode = "409", description = "Possui vínculos (RF02 RN3 / RF03 RN3)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Integer id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
