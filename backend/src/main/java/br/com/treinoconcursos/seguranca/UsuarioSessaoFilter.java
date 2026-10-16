package br.com.treinoconcursos.seguranca;

import br.com.treinoconcursos.model.Usuario;
import br.com.treinoconcursos.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Confere no banco, a cada requisicao autenticada, se o usuario da sessao
 * continua ativo e com o mesmo perfil.
 *
 * Sem isto, quem fosse inativado ou rebaixado pelo ADMIN continuaria com os
 * poderes antigos ate a sessao expirar (8 horas). O custo e uma consulta pela
 * chave primaria, so em requisicoes de quem esta logado no painel.
 *
 * Nao e um @Component de proposito: o Spring Boot registraria o filtro uma
 * segunda vez, fora da cadeia do Spring Security.
 */
public class UsuarioSessaoFilter extends OncePerRequestFilter {

    private final UsuarioRepository usuarioRepository;
    private final SecurityContextRepository securityContextRepository;
    private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();

    public UsuarioSessaoFilter(UsuarioRepository usuarioRepository,
            SecurityContextRepository securityContextRepository) {
        this.usuarioRepository = usuarioRepository;
        this.securityContextRepository = securityContextRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication autenticacao = holder.getContext().getAuthentication();
        if (autenticacao != null && autenticacao.getPrincipal() instanceof UsuarioAutenticado sessao) {
            Usuario usuario = usuarioRepository.findById(sessao.id()).orElse(null);
            if (usuario == null || !usuario.isAtivo()) {
                encerrarSessao(request);
            } else if (usuario.getPerfil() != sessao.perfil()) {
                atualizarPerfil(request, response, usuario);
            }
        }
        chain.doFilter(request, response);
    }

    private void encerrarSessao(HttpServletRequest request) {
        holder.clearContext();
        HttpSession sessao = request.getSession(false);
        if (sessao != null) {
            sessao.invalidate();
        }
    }

    private void atualizarPerfil(HttpServletRequest request, HttpServletResponse response, Usuario usuario) {
        UsuarioAutenticado atualizado = new UsuarioAutenticado(usuario.getId(), usuario.getPerfil());
        SecurityContext contexto = holder.createEmptyContext();
        contexto.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(atualizado, null, atualizado.autoridades()));
        holder.setContext(contexto);
        securityContextRepository.saveContext(contexto, request, response);
    }
}
