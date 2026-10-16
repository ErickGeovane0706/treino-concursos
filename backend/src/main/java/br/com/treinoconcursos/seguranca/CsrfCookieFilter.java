package br.com.treinoconcursos.seguranca;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Faz o cookie XSRF-TOKEN ser enviado ja na primeira resposta.
 *
 * O Spring Security so gera o token quando alguem o le (token "adiado"). Uma
 * pagina Thymeleaf le o token ao montar o formulario, mas a SPA do painel nao:
 * sem este filtro, o primeiro POST (o login) iria sem token e levaria 403.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token != null) {
            token.getToken();
        }
        chain.doFilter(request, response);
    }
}
