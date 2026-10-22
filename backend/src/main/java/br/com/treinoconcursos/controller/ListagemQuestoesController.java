package br.com.treinoconcursos.controller;

import br.com.treinoconcursos.dto.ListagemQuestoesView;
import br.com.treinoconcursos.service.DadosEstruturadosService;
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
    private final DadosEstruturadosService dadosEstruturados;

    public ListagemQuestoesController(NavegacaoQuestoesService navegacaoService,
            DadosEstruturadosService dadosEstruturados) {
        this.navegacaoService = navegacaoService;
        this.dadosEstruturados = dadosEstruturados;
    }

    @GetMapping("/questoes")
    public String todas(@RequestParam(required = false) Integer depoisDe, Model model) {
        return mostrar(navegacaoService.listarPagina(null, null, depoisDe), model);
    }

    @GetMapping("/questoes/{materia}")
    public String porMateria(@PathVariable String materia,
            @RequestParam(required = false) Integer depoisDe, Model model) {
        return mostrar(navegacaoService.listarPagina(materia, null, depoisDe), model);
    }

    @GetMapping("/questoes/{materia}/{assunto}")
    public String porAssunto(@PathVariable String materia, @PathVariable String assunto,
            @RequestParam(required = false) Integer depoisDe, Model model) {
        return mostrar(navegacaoService.listarPagina(materia, assunto, depoisDe), model);
    }

    private String mostrar(ListagemQuestoesView listagem, Model model) {
        model.addAttribute("listagem", listagem);
        model.addAttribute("dadosEstruturados", dadosEstruturados.trilhaDaListagem(listagem));
        return "listagem";
    }
}
