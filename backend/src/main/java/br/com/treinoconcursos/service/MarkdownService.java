package br.com.treinoconcursos.service;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Unico caminho de Markdown para HTML (RF04 RN4), usado pela previa do painel
 * e pelas paginas publicas. Duas barreiras:
 * 1. o commonmark escapa qualquer HTML bruto escrito no texto (vira texto
 *    visivel) e descarta links com protocolo perigoso (javascript:);
 * 2. o sanitizer OWASP so deixa passar a allowlist abaixo.
 *
 * So o HTML que sai daqui pode ir para um th:utext.
 */
@Service
public class MarkdownService {

    private static final List<Extension> EXTENSOES = List.of(TablesExtension.create());

    private static final PolicyFactory POLITICA = new HtmlPolicyBuilder()
            .allowElements("p", "br", "hr", "strong", "em", "code", "pre", "blockquote", "ul", "ol", "li",
                    "h3", "h4", "h5", "h6", "table", "thead", "tbody", "tr", "th", "td", "a", "img")
            .allowAttributes("start").onElements("ol")
            .allowAttributes("href").onElements("a")
            .allowAttributes("src", "alt", "title").onElements("img")
            .allowUrlProtocols("http", "https")
            .requireRelsOnLinks("nofollow", "noopener")
            .toFactory();

    private final Parser parser = Parser.builder().extensions(EXTENSOES).build();

    private final HtmlRenderer renderer = HtmlRenderer.builder()
            .extensions(EXTENSOES)
            .escapeHtml(true)
            .sanitizeUrls(true)
            .build();

    /** @return HTML seguro; "" para texto nulo ou vazio */
    public String paraHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        return POLITICA.sanitize(renderer.render(parser.parse(markdown)));
    }
}
