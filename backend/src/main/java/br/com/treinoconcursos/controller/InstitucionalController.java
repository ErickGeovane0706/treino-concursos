package br.com.treinoconcursos.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Paginas publicas fixas: inicio provisorio e institucionais (RF18). */
@Controller
public class InstitucionalController {

    /** Provisoria: materias e "Continuar de onde parei" entram na issue #8. */
    @GetMapping("/")
    public String inicio() {
        return "inicio";
    }

    @GetMapping("/sobre")
    public String sobre() {
        return "institucional/sobre";
    }

    /** RF18 RN4: so e-mail, sem formulario. */
    @GetMapping("/contato")
    public String contato() {
        return "institucional/contato";
    }

    /** RF18 RN3 */
    @GetMapping("/privacidade")
    public String privacidade() {
        return "institucional/privacidade";
    }

    @GetMapping("/termos")
    public String termos() {
        return "institucional/termos";
    }

    @GetMapping("/cookies")
    public String cookies() {
        return "institucional/cookies";
    }
}
