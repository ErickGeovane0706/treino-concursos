package br.com.treinoconcursos.controller;

import br.com.treinoconcursos.dto.SimuladoPaginasView;
import br.com.treinoconcursos.service.SimuladoPublicoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * Paginas publicas do modo simulado (RF13, US05). A lista e a apresentacao sao
 * indexaveis; as questoes e o resultado tem noindex (doc/api.md, secao 2).
 */
@Controller
public class SimuladoPublicoController {

    private final SimuladoPublicoService service;

    public SimuladoPublicoController(SimuladoPublicoService service) {
        this.service = service;
    }

    @GetMapping("/simulados")
    public String listar(Model model) {
        List<SimuladoPaginasView.Cartao> simulados = service.listarPublicados();
        model.addAttribute("simulados", simulados);
        return "simulados";
    }

    @GetMapping("/simulado/{slug}")
    public String apresentacao(@PathVariable String slug, Model model) {
        model.addAttribute("simulado", service.apresentacao(slug));
        return "simulado";
    }

    /** RF13 RN1: cada questao e uma pagina propria, com anuncio seguindo RF12. */
    @GetMapping("/simulado/{slug}/{n:\\d{1,4}}")
    public String questao(@PathVariable String slug, @PathVariable int n, Model model) {
        model.addAttribute("questao", service.questao(slug, n));
        model.addAttribute("exibirAnuncios", true);
        return "simulado-questao";
    }

    @GetMapping("/simulado/{slug}/resultado")
    public String resultado(@PathVariable String slug, Model model) {
        model.addAttribute("resultado", service.resultado(slug));
        return "simulado-resultado";
    }
}
