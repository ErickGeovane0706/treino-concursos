package br.com.treinoconcursos.controller;

import br.com.treinoconcursos.dto.QuestaoRequest;
import br.com.treinoconcursos.dto.QuestaoResponse;
import br.com.treinoconcursos.dto.QuestaoResumoResponse;
import br.com.treinoconcursos.model.StatusQuestao;
import br.com.treinoconcursos.seguranca.UsuarioAutenticado;
import br.com.treinoconcursos.service.QuestaoService;
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

@Tag(name = "Questões (painel)", description = "Cadastro e ciclo de vida das questões (RF04, RF05, US09)")
@RestController
@RequestMapping("/api/admin/questoes")
public class QuestaoAdminController {

    private final QuestaoService service;

    public QuestaoAdminController(QuestaoService service) {
        this.service = service;
    }

    @Operation(summary = "Listar questões",
            description = "Perfil: EDITOR. Filtros opcionais: status, matéria e texto do enunciado.")
    @GetMapping
    public Page<QuestaoResumoResponse> listar(@RequestParam(required = false) StatusQuestao status,
            @RequestParam(required = false) Integer idMateria, @RequestParam(required = false) String busca,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.listar(status, idMateria, busca, pageable);
    }

    @Operation(summary = "Buscar por id", description = "Perfil: EDITOR")
    @ApiResponse(responseCode = "404", description = "Não encontrada")
    @GetMapping("/{id}")
    public QuestaoResponse buscar(@PathVariable Integer id) {
        return service.buscar(id);
    }

    @Operation(summary = "Cadastrar (como rascunho)", description = "Perfil: EDITOR")
    @ApiResponse(responseCode = "201", description = "Cadastrada")
    @ApiResponse(responseCode = "409", description = "Alternativas ou prova inválidas (RF04 RN2 e RN3)")
    @PostMapping
    public ResponseEntity<QuestaoResponse> criar(@Valid @RequestBody QuestaoRequest request,
            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        QuestaoResponse criada = service.criar(request, usuario.id());
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(criada.id()).toUri();
        return ResponseEntity.created(location).body(criada);
    }

    @Operation(summary = "Alterar", description = "Perfil: EDITOR. O status não muda; trocar o gabarito zera as "
            + "estatísticas (RF04 RN5).")
    @ApiResponse(responseCode = "409", description = "Regra de negócio")
    @PutMapping("/{id}")
    public QuestaoResponse atualizar(@PathVariable Integer id, @Valid @RequestBody QuestaoRequest request) {
        return service.atualizar(id, request);
    }

    @Operation(summary = "Excluir rascunho", description = "Perfil: EDITOR. Só se nunca foi publicada.")
    @ApiResponse(responseCode = "204", description = "Excluída")
    @ApiResponse(responseCode = "409", description = "Já foi publicada: arquive")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Integer id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Publicar", description = "Perfil: EDITOR. Exige comentário (RF04 RN1, RF05 RN1).")
    @ApiResponse(responseCode = "409", description = "Já publicada ou sem comentário")
    @PatchMapping("/{id}/publicar")
    public QuestaoResponse publicar(@PathVariable Integer id) {
        return service.publicar(id);
    }

    @Operation(summary = "Arquivar", description = "Perfil: EDITOR. O endereço passa a responder 410 (RF05 RN3).")
    @ApiResponse(responseCode = "409", description = "Já arquivada")
    @PatchMapping("/{id}/arquivar")
    public QuestaoResponse arquivar(@PathVariable Integer id) {
        return service.arquivar(id);
    }

    @Operation(summary = "Marcar como anulada pela banca",
            description = "Perfil: EDITOR. RF05 RN4. Com ?desfazer=true, tira a marcação.")
    @PatchMapping("/{id}/anular")
    public QuestaoResponse anular(@PathVariable Integer id,
            @RequestParam(defaultValue = "false") boolean desfazer) {
        return service.anular(id, !desfazer);
    }

    @Operation(summary = "Marcar como desatualizada",
            description = "Perfil: EDITOR. RF05 RN5. Com ?desfazer=true, tira a marcação.")
    @PatchMapping("/{id}/marcar-desatualizada")
    public QuestaoResponse marcarDesatualizada(@PathVariable Integer id,
            @RequestParam(defaultValue = "false") boolean desfazer) {
        return service.marcarDesatualizada(id, !desfazer);
    }
}
