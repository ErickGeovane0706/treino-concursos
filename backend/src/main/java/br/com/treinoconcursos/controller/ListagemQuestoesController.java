package br.com.treinoconcursos.controller;

import br.com.treinoconcursos.dto.FiltroQuestoes;
import br.com.treinoconcursos.service.NavegacaoQuestoesService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

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
    public String todas(
            @RequestParam(required = false) String materia,
            @RequestParam(required = false) String assunto,
            @RequestParam(required = false) String banca,
            @RequestParam(required = false) String orgao,
            @RequestParam(required = false) String ano,
            @RequestParam(required = false) String nivel,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) Integer depoisDe,
            Model model) {
        FiltroQuestoes filtro = FiltroQuestoes.deParametros(materia, assunto, banca, orgao, ano, nivel, tipo);
        model.addAttribute("listagem", navegacaoService.listarPagina(filtro, depoisDe, false));
        return "listagem";
    }

    @GetMapping("/questoes/banca/{banca}")
    public String porBanca(
            @PathVariable String banca,
            @RequestParam(required = false) String materia,
            @RequestParam(required = false) String assunto,
            @RequestParam(required = false) String orgao,
            @RequestParam(required = false) String ano,
            @RequestParam(required = false) String nivel,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) Integer depoisDe,
            Model model) {
        FiltroQuestoes filtro = FiltroQuestoes.deParametros(materia, assunto, banca, orgao, ano, nivel, tipo);
        model.addAttribute("listagem", navegacaoService.listarPagina(filtro, depoisDe, true));
        return "listagem";
    }

    @GetMapping("/questoes/orgao/{orgao}")
    public String porOrgao(
            @PathVariable String orgao,
            @RequestParam(required = false) String materia,
            @RequestParam(required = false) String assunto,
            @RequestParam(required = false) String banca,
            @RequestParam(required = false) String ano,
            @RequestParam(required = false) String nivel,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) Integer depoisDe,
            Model model) {
        FiltroQuestoes filtro = FiltroQuestoes.deParametros(materia, assunto, banca, orgao, ano, nivel, tipo);
        model.addAttribute("listagem", navegacaoService.listarPagina(filtro, depoisDe, true));
        return "listagem";
    }

    @GetMapping("/questoes/ano/{ano}")
    public String porAno(
            @PathVariable String ano,
            @RequestParam(required = false) String materia,
            @RequestParam(required = false) String assunto,
            @RequestParam(required = false) String banca,
            @RequestParam(required = false) String orgao,
            @RequestParam(required = false) String nivel,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) Integer depoisDe,
            Model model) {
        if (FiltroQuestoes.converterAno(ano) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        FiltroQuestoes filtro = FiltroQuestoes.deParametros(materia, assunto, banca, orgao, ano, nivel, tipo);
        model.addAttribute("listagem", navegacaoService.listarPagina(filtro, depoisDe, true));
        return "listagem";
    }

    @GetMapping("/questoes/{materia}")
    public String porMateria(
            @PathVariable String materia,
            @RequestParam(required = false) String banca,
            @RequestParam(required = false) String orgao,
            @RequestParam(required = false) String ano,
            @RequestParam(required = false) String nivel,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) Integer depoisDe,
            Model model) {
        FiltroQuestoes filtro = FiltroQuestoes.deParametros(materia, null, banca, orgao, ano, nivel, tipo);
        model.addAttribute("listagem", navegacaoService.listarPagina(filtro, depoisDe, true));
        return "listagem";
    }

    @GetMapping("/questoes/{materia}/{assunto}")
    public String porAssunto(
            @PathVariable String materia,
            @PathVariable String assunto,
            @RequestParam(required = false) String banca,
            @RequestParam(required = false) String orgao,
            @RequestParam(required = false) String ano,
            @RequestParam(required = false) String nivel,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) Integer depoisDe,
            Model model) {
        FiltroQuestoes filtro = FiltroQuestoes.deParametros(materia, assunto, banca, orgao, ano, nivel, tipo);
        model.addAttribute("listagem", navegacaoService.listarPagina(filtro, depoisDe, true));
        return "listagem";
    }
}
