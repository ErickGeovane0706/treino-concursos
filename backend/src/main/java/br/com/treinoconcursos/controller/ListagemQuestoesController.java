package br.com.treinoconcursos.controller;

import br.com.treinoconcursos.service.NavegacaoQuestoesService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Paginas publicas de listagem e navegacao por cursor (RF07 RN1-RN5, US02).
 */
@Controller
public class ListagemQuestoesController {

    private final NavegacaoQuestoesService navegacaoService;

    public ListagemQuestoesController(NavegacaoQuestoesService navegacaoService) {
        this.navegacaoService = navegacaoService;
    }

    @GetMapping("/questoes")
    public String todas(@RequestParam(required = false) Integer depoisDe, Model model) {
        model.addAttribute("listagem", navegacaoService.listarPagina(null, null, depoisDe));
        return "listagem";
    }

    @GetMapping("/questoes/{materia}")
    public String porMateria(@PathVariable String materia,
            @RequestParam(required = false) Integer depoisDe, Model model) {
        model.addAttribute("listagem", navegacaoService.listarPagina(materia, null, depoisDe));
        return "listagem";
    }

    @GetMapping("/questoes/{materia}/{assunto}")
    public String porAssunto(@PathVariable String materia, @PathVariable String assunto,
            @RequestParam(required = false) Integer depoisDe, Model model) {
        model.addAttribute("listagem", navegacaoService.listarPagina(materia, assunto, depoisDe));
        return "listagem";
    }
}
