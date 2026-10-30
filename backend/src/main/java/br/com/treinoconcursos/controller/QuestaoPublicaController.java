package br.com.treinoconcursos.controller;

import br.com.treinoconcursos.dto.QuestaoPaginaView;
import br.com.treinoconcursos.service.DadosEstruturadosService;
import br.com.treinoconcursos.service.QuestaoPublicaService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Pagina publica permanente de cada questao (RF06, RF08, US01).
 */
@Controller
public class QuestaoPublicaController {

    private final QuestaoPublicaService service;
    private final DadosEstruturadosService dadosEstruturados;

    public QuestaoPublicaController(QuestaoPublicaService service, DadosEstruturadosService dadosEstruturados) {
        this.service = service;
        this.dadosEstruturados = dadosEstruturados;
    }

    /**
     * RF08 RN1: /questao/{id}/{slug} e o endereco permanente. Se o slug for
     * omitido ou diferente do atual, redireciona com 301 para o canonico.
     */
    @GetMapping({"/questao/{id}", "/questao/{id}/{slug}"})
    public String ver(@PathVariable Integer id, @PathVariable(required = false) String slug,
            @RequestParam(required = false) String de, Model model, HttpServletResponse response) {
        QuestaoPaginaView questao = service.buscarParaPagina(id, de);
        if (!questao.slug().equals(slug)) {
            response.setStatus(HttpStatus.MOVED_PERMANENTLY.value());
            response.setHeader(HttpHeaders.LOCATION,
                    QuestaoPublicaService.urlCanonica(questao.id(), questao.slug(), questao.filtroAtivo()));
            return null;
        }
        model.addAttribute("questao", questao);
        model.addAttribute("dadosEstruturados", dadosEstruturados.trilhaDaQuestao(questao));
        model.addAttribute("exibirAnuncios", true);
        return "questao";
    }
}
