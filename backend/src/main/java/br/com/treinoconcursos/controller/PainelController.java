package br.com.treinoconcursos.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Casco da SPA do painel (RF01, doc/ambiente-de-desenvolvimento.md secao 10).
 *
 * O React Router usa basename "/admin", entao /admin, /admin/ e qualquer rota
 * abaixo (ex.: /admin/materias/3) devolvem o index.html do build do Vite, igual
 * ao plugin do vite.config.ts em dev. Ultimo trecho com ponto e arquivo: se nao
 * for o index.html, 404 (os arquivos do painel ficam em /assets).
 *
 * O index.html nao tem dados (quem protege e a API, SecurityConfig) e sai com o
 * "no-store" do Spring Security, para um deploy novo valer na hora.
 */
@Controller
public class PainelController {

    private final Resource indice = new ClassPathResource("static/admin/index.html");

    @GetMapping({"/admin", "/admin/{*rota}"})
    public ResponseEntity<Resource> casco(@PathVariable(required = false) String rota) {
        String ultimoTrecho = rota == null ? "" : rota.substring(rota.lastIndexOf('/') + 1);
        boolean ehArquivo = ultimoTrecho.contains(".") && !ultimoTrecho.equals("index.html");
        if (ehArquivo || !indice.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(indice);
    }
}
