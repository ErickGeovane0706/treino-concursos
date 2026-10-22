package br.com.treinoconcursos.seguranca;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

/**
 * Igual ao csrf().spa() do Spring Security (o token chega no cabecalho
 * X-XSRF-TOKEN, cru; em formulario, mascarado), mas sem carregar o token em
 * toda requisicao.
 *
 * O handler do spa() le o nome do parametro do token para registrar o atributo
 * da requisicao, e isso gera o token e grava o cookie XSRF-TOKEN ate em pagina
 * publica e arquivo estatico. Com o nome fixo ("_csrf"), o token so e gerado
 * quando alguem o usa: o CsrfCookieFilter, apenas na API e no painel.
 */
public final class CsrfSpaHandler implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestAttributeHandler simples = new CsrfTokenRequestAttributeHandler();
    private final XorCsrfTokenRequestAttributeHandler mascarado = new XorCsrfTokenRequestAttributeHandler();

    public CsrfSpaHandler() {
        mascarado.setCsrfRequestAttributeName("_csrf");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> token) {
        mascarado.handle(request, response, token);
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken token) {
        boolean noCabecalho = StringUtils.hasText(request.getHeader(token.getHeaderName()));
        return (noCabecalho ? simples : mascarado).resolveCsrfTokenValue(request, token);
    }
}
