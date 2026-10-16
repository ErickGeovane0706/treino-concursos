package br.com.treinoconcursos.controller;

import br.com.treinoconcursos.dto.RedefinirSenhaRequest;
import br.com.treinoconcursos.dto.UsuarioAtualizarRequest;
import br.com.treinoconcursos.dto.UsuarioRequest;
import br.com.treinoconcursos.dto.UsuarioResponse;
import br.com.treinoconcursos.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Usuarios do painel. Todas as rotas exigem perfil ADMIN (SecurityConfig).
 */
@Tag(name = "Usuários (painel)", description = "Contas de editores e administradores (RF01, US13)")
@RestController
@RequestMapping("/api/admin/usuarios")
public class UsuarioAdminController {

    private final UsuarioService usuarioService;

    public UsuarioAdminController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @Operation(summary = "Listar usuários", description = "Perfil: ADMIN")
    @GetMapping
    public Page<UsuarioResponse> listar(@PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return usuarioService.listar(pageable);
    }

    @Operation(summary = "Buscar usuário", description = "Perfil: ADMIN")
    @ApiResponse(responseCode = "404", description = "Usuário não encontrado")
    @GetMapping("/{id}")
    public UsuarioResponse buscar(@PathVariable Integer id) {
        return usuarioService.buscar(id);
    }

    @Operation(summary = "Criar usuário", description = "Perfil: ADMIN")
    @ApiResponse(responseCode = "201", description = "Usuário criado")
    @ApiResponse(responseCode = "409", description = "E-mail já cadastrado")
    @PostMapping
    public ResponseEntity<UsuarioResponse> criar(@Valid @RequestBody UsuarioRequest request) {
        UsuarioResponse criado = usuarioService.criar(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(location).body(criado);
    }

    @Operation(summary = "Alterar nome, e-mail e perfil", description = "Perfil: ADMIN")
    @ApiResponse(responseCode = "409", description = "E-mail já cadastrado ou rebaixamento do último ADMIN (RF01 RN4)")
    @PutMapping("/{id}")
    public UsuarioResponse atualizar(@PathVariable Integer id, @Valid @RequestBody UsuarioAtualizarRequest request) {
        return usuarioService.atualizar(id, request);
    }

    @Operation(summary = "Inativar usuário", description = "Perfil: ADMIN")
    @ApiResponse(responseCode = "409", description = "Último ADMIN ativo (RF01 RN4)")
    @PatchMapping("/{id}/inativar")
    public UsuarioResponse inativar(@PathVariable Integer id) {
        return usuarioService.inativar(id);
    }

    @Operation(summary = "Reativar usuário", description = "Perfil: ADMIN")
    @PatchMapping("/{id}/reativar")
    public UsuarioResponse reativar(@PathVariable Integer id) {
        return usuarioService.reativar(id);
    }

    @Operation(summary = "Desbloquear conta bloqueada por senhas erradas", description = "Perfil: ADMIN")
    @PatchMapping("/{id}/desbloquear")
    public UsuarioResponse desbloquear(@PathVariable Integer id) {
        return usuarioService.desbloquear(id);
    }

    @Operation(summary = "Redefinir senha", description = "Perfil: ADMIN")
    @PatchMapping("/{id}/redefinir-senha")
    public UsuarioResponse redefinirSenha(@PathVariable Integer id, @Valid @RequestBody RedefinirSenhaRequest request) {
        return usuarioService.redefinirSenha(id, request.novaSenha());
    }
}
