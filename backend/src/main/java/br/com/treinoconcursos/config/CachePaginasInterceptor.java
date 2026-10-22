package br.com.treinoconcursos.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.time.Duration;

/**
 * Cache-Control das paginas publicas (RF09, RNF03, RNF08).
 *
 * As paginas sao iguais para todo visitante (sem cookie nem sessao), entao podem
 * ficar em cache: pouco tempo no navegador, para uma questao corrigida aparecer
 * logo, e mais no CDN (s-maxage, lido pela Cloudflare).
 *
 * So vale para GET com 200 de um handler que renderiza view (Thymeleaf). Tudo o mais
 * (API, painel, erros, redirecionamentos) fica com o padrao do Spring Security,
 * "no-cache, no-store", que ele so escreve quando a resposta ainda nao tem Cache-Control.
 */
public class CachePaginasInterceptor implements HandlerInterceptor {

    static final CacheControl CACHE_PAGINA = CacheControl.maxAge(Duration.ofMinutes(5))
            .sMaxAge(Duration.ofHours(1))
            .cachePublic();

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
            ModelAndView modelAndView) {
        if (modelAndView == null
                || !(handler instanceof HandlerMethod)
                || !"GET".equals(request.getMethod())
                || response.getStatus() != HttpStatus.OK.value()
                || response.containsHeader(HttpHeaders.CACHE_CONTROL)) {
            return;
        }
        String caminho = request.getRequestURI().substring(request.getContextPath().length());
        if (caminho.startsWith("/admin") || caminho.startsWith("/api/") || caminho.startsWith("/error")) {
            return;
        }
        response.setHeader(HttpHeaders.CACHE_CONTROL, CACHE_PAGINA.getHeaderValue());
    }
}
