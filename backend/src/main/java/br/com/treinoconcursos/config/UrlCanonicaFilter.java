package br.com.treinoconcursos.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

/**
 * URLs publicas em minusculas e sem barra no final (doc/api.md secao 2, RF09 RN3):
 * {@code /Questoes/Portugues/} responde 301 para {@code /questoes/portugues},
 * mantendo a query string. Assim o Google nao ve duas URLs para a mesma pagina.
 *
 * Fica de fora o que nao e pagina: painel (/admin usa a barra final), API e
 * arquivos (com ponto no nome, como os do Vite, que tem maiusculas no hash).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UrlCanonicaFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String caminho = caminho(request);
        return !("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))
                || caminho.equals("/")
                || caminho.startsWith("/admin")
                || caminho.startsWith("/api/")
                || caminho.startsWith("/assets/")
                || caminho.contains(".");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String caminho = caminho(request);
        String canonico = caminho.toLowerCase(Locale.ROOT).replaceAll("/+$", "");
        if (canonico.isEmpty()) {
            canonico = "/";
        }
        if (canonico.equals(caminho)) {
            chain.doFilter(request, response);
            return;
        }
        String query = request.getQueryString();
        response.setStatus(HttpStatus.MOVED_PERMANENTLY.value());
        response.setHeader(HttpHeaders.LOCATION,
                request.getContextPath() + canonico + (query == null ? "" : "?" + query));
    }

    private static String caminho(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }
}
