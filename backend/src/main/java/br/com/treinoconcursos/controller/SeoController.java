package br.com.treinoconcursos.controller;

import br.com.treinoconcursos.service.SitemapService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Arquivos de raiz para os buscadores (RF09 RN1-RN2, US14). Nao e API: fica fora
 * de /api, que o robots.txt bloqueia.
 */
@Controller
public class SeoController {

    /** Os robos releem com frequencia; uma hora no CDN segura a origem (RNF03). */
    static final CacheControl CACHE_ARQUIVOS = CacheControl.maxAge(Duration.ofHours(1)).cachePublic();

    private static final MediaType XML = new MediaType("application", "xml", StandardCharsets.UTF_8);

    private final SitemapService sitemapService;

    public SeoController(SitemapService sitemapService) {
        this.sitemapService = sitemapService;
    }

    @GetMapping("/robots.txt")
    public ResponseEntity<String> robots() {
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                .cacheControl(CACHE_ARQUIVOS)
                .body(sitemapService.robots());
    }

    @GetMapping("/sitemap.xml")
    public ResponseEntity<String> indice() {
        return xml(sitemapService.indice());
    }

    @GetMapping("/sitemaps/paginas.xml")
    public ResponseEntity<String> paginas() {
        return xml(sitemapService.paginas());
    }

    @GetMapping("/sitemaps/questoes-{bloco:\\d{1,6}}.xml")
    public ResponseEntity<String> questoes(@PathVariable int bloco) {
        return sitemapService.questoes(bloco)
                .map(this::xml)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private ResponseEntity<String> xml(String corpo) {
        return ResponseEntity.ok().contentType(XML).cacheControl(CACHE_ARQUIVOS).body(corpo);
    }
}
