package br.com.treinoconcursos.controller;

import br.com.treinoconcursos.dto.LoginRequest;
import br.com.treinoconcursos.dto.UsuarioResponse;
import br.com.treinoconcursos.model.Usuario;
import br.com.treinoconcursos.seguranca.UsuarioAutenticado;
import br.com.treinoconcursos.service.AuthService;
import br.com.treinoconcursos.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login e sessao do painel. O logout (POST /api/auth/logout -> 204) e
 * atendido pelo proprio Spring Security (SecurityConfig).
 */
@Tag(name = "Autenticação", description = "Login por sessão no painel (RF01, RNF01)")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UsuarioService usuarioService;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();

    public AuthController(AuthService authService, UsuarioService usuarioService,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            SecurityContextRepository securityContextRepository) {
        this.authService = authService;
        this.usuarioService = usuarioService;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.securityContextRepository = securityContextRepository;
    }

    @Operation(summary = "Entrar no painel", description = "Perfil: nenhum. Exige o cabeçalho X-XSRF-TOKEN.")
    @ApiResponse(responseCode = "200", description = "Sessão criada")
    @ApiResponse(responseCode = "401", description = "E-mail ou senha inválidos")
    @ApiResponse(responseCode = "423", description = "Conta bloqueada após 5 senhas erradas (RF01 RN3)")
    @PostMapping("/login")
    public UsuarioResponse login(@Valid @RequestBody LoginRequest login, HttpServletRequest request,
            HttpServletResponse response) {
        Usuario usuario = authService.autenticar(login.email(), login.senha());

        UsuarioAutenticado principal = new UsuarioAutenticado(usuario.getId(), usuario.getPerfil());
        Authentication autenticacao =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.autoridades());
        sessionAuthenticationStrategy.onAuthentication(autenticacao, request, response);

        SecurityContext contexto = holder.createEmptyContext();
        contexto.setAuthentication(autenticacao);
        holder.setContext(contexto);
        securityContextRepository.saveContext(contexto, request, response);

        return UsuarioResponse.de(usuario);
    }

    @Operation(summary = "Usuário da sessão atual", description = "Perfil: EDITOR ou ADMIN")
    @ApiResponse(responseCode = "200", description = "Usuário logado")
    @ApiResponse(responseCode = "401", description = "Sem sessão")
    @GetMapping("/eu")
    public UsuarioResponse eu(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return usuarioService.buscar(usuario.id());
    }
}
