package br.com.treinoconcursos.controller;

import br.com.treinoconcursos.service.NavegacaoQuestoesService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Paginas publicas fixas: inicio (RF07, RF10) e institucionais (RF18). */
@Controller
public class InstitucionalController {

    private final NavegacaoQuestoesService navegacaoService;

    public InstitucionalController(NavegacaoQuestoesService navegacaoService) {
        this.navegacaoService = navegacaoService;
    }

    @GetMapping("/")
    public String inicio(Model model) {
        model.addAttribute("materias", navegacaoService.listarMateriasAtivas(null));
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

    /** RF10 RN6: pagina publica (noindex) para consultar e apagar o progresso salvo no navegador. */
    @GetMapping("/meu-progresso")
    public String meuProgresso() {
        return "meu-progresso";
    }

    /** RF11 RN1-RN2, US04: pagina publica (noindex) do caderno de erros montado no navegador. */
    @GetMapping("/caderno-de-erros")
    public String cadernoDeErros() {
        return "caderno-de-erros";
    }
}

