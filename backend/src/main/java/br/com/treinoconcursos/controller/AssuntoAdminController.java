package br.com.treinoconcursos.controller;

import br.com.treinoconcursos.dto.AssuntoRequest;
import br.com.treinoconcursos.dto.AssuntoResponse;
import br.com.treinoconcursos.service.AssuntoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

/** Assuntos aninhados na materia. A lista vem inteira (poucos por materia), com o nivel de cada um. */
@Tag(name = "Assuntos (painel)", description = "Assuntos e subassuntos de uma matéria, até 3 níveis (RF02, US10)")
@RestController
@RequestMapping("/api/admin/materias/{idMateria}/assuntos")
public class AssuntoAdminController {

    private final AssuntoService service;

    public AssuntoAdminController(AssuntoService service) {
        this.service = service;
    }

    @Operation(summary = "Listar assuntos da matéria", description = "Perfil: EDITOR")
    @GetMapping
    public List<AssuntoResponse> listar(@PathVariable Integer idMateria) {
        return service.listar(idMateria);
    }

    @Operation(summary = "Buscar assunto", description = "Perfil: EDITOR")
    @ApiResponse(responseCode = "404", description = "Não encontrado nesta matéria")
    @GetMapping("/{id}")
    public AssuntoResponse buscar(@PathVariable Integer idMateria, @PathVariable Integer id) {
        return service.buscar(idMateria, id);
    }

    @Operation(summary = "Cadastrar assunto", description = "Perfil: EDITOR")
    @ApiResponse(responseCode = "201", description = "Cadastrado")
    @ApiResponse(responseCode = "409", description = "Slug repetido na matéria, pai de outra matéria ou mais de 3 níveis")
    @PostMapping
    public ResponseEntity<AssuntoResponse> criar(@PathVariable Integer idMateria,
            @Valid @RequestBody AssuntoRequest request) {
        AssuntoResponse criado = service.criar(idMateria, request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(location).body(criado);
    }

    @Operation(summary = "Alterar assunto", description = "Perfil: EDITOR")
    @ApiResponse(responseCode = "409", description = "Slug com questões publicadas, ciclo ou mais de 3 níveis")
    @PutMapping("/{id}")
    public AssuntoResponse atualizar(@PathVariable Integer idMateria, @PathVariable Integer id,
            @Valid @RequestBody AssuntoRequest request) {
        return service.atualizar(idMateria, id, request);
    }

    @Operation(summary = "Excluir assunto", description = "Perfil: EDITOR")
    @ApiResponse(responseCode = "204", description = "Excluído")
    @ApiResponse(responseCode = "409", description = "Possui questões ou subassuntos (RF02 RN3)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Integer idMateria, @PathVariable Integer id) {
        service.excluir(idMateria, id);
        return ResponseEntity.noContent().build();
    }
}
