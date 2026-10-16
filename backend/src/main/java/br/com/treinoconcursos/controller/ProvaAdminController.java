package br.com.treinoconcursos.controller;

import br.com.treinoconcursos.dto.ProvaRequest;
import br.com.treinoconcursos.dto.ProvaResponse;
import br.com.treinoconcursos.service.ProvaService;
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

@Tag(name = "Provas (painel)", description = "Provas: banca + órgão + ano + cargo (RF03, US10)")
@RestController
@RequestMapping("/api/admin/provas")
public class ProvaAdminController {

    private final ProvaService service;

    public ProvaAdminController(ProvaService service) {
        this.service = service;
    }

    @Operation(summary = "Listar provas", description = "Perfil: EDITOR. Busca opcional por texto.")
    @GetMapping
    public Page<ProvaResponse> listar(@RequestParam(required = false) String busca,
            @PageableDefault(size = 20, sort = "ano", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.listar(busca, pageable);
    }

    @Operation(summary = "Buscar por id", description = "Perfil: EDITOR")
    @ApiResponse(responseCode = "404", description = "Não encontrado")
    @GetMapping("/{id}")
    public ProvaResponse buscar(@PathVariable Integer id) {
        return service.buscar(id);
    }

    @Operation(summary = "Cadastrar", description = "Perfil: EDITOR")
    @ApiResponse(responseCode = "201", description = "Cadastrado")
    @ApiResponse(responseCode = "409", description = "Duplicidade (nome, sigla, slug ou combinação única)")
    @PostMapping
    public ResponseEntity<ProvaResponse> criar(@Valid @RequestBody ProvaRequest request) {
        ProvaResponse criado = service.criar(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(location).body(criado);
    }

    @Operation(summary = "Alterar", description = "Perfil: EDITOR")
    @ApiResponse(responseCode = "409", description = "Duplicidade ou regra de negócio")
    @PutMapping("/{id}")
    public ProvaResponse atualizar(@PathVariable Integer id, @Valid @RequestBody ProvaRequest request) {
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
