package br.com.treinoconcursos.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Fallback da SPA do painel em producao (doc/ambiente-de-desenvolvimento.md, 10.3):
 * qualquer rota de navegacao em /admin/** (sem extensao de arquivo) entrega o
 * static/admin/index.html gerado pelo Vite para o React Router assumir.
 */
@Controller
public class PainelSpaController {

    @GetMapping({
            "/admin",
            "/admin/",
            "/admin/{s1:[^.]+}",
            "/admin/{s1:[^.]+}/{s2:[^.]+}",
            "/admin/{s1:[^.]+}/{s2:[^.]+}/{s3:[^.]+}",
            "/admin/{s1:[^.]+}/{s2:[^.]+}/{s3:[^.]+}/{s4:[^.]+}"
    })
    public String painel() {
        return "forward:/admin/index.html";
    }
}
