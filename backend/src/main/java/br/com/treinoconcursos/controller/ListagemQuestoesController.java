package br.com.treinoconcursos.controller;

import br.com.treinoconcursos.dto.FiltroQuestoes;
import br.com.treinoconcursos.dto.ListagemQuestoesView;
import br.com.treinoconcursos.service.DadosEstruturadosService;
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
    private final DadosEstruturadosService dadosEstruturados;

    public ListagemQuestoesController(NavegacaoQuestoesService navegacaoService,
            DadosEstruturadosService dadosEstruturados) {
        this.navegacaoService = navegacaoService;
        this.dadosEstruturados = dadosEstruturados;
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
        return mostrar(navegacaoService.listarPagina(filtro, depoisDe, false), model);
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
        return mostrar(navegacaoService.listarPagina(filtro, depoisDe, true), model);
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
        return mostrar(navegacaoService.listarPagina(filtro, depoisDe, true), model);
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
        return mostrar(navegacaoService.listarPagina(filtro, depoisDe, true), model);
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
        return mostrar(navegacaoService.listarPagina(filtro, depoisDe, true), model);
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
        return mostrar(navegacaoService.listarPagina(filtro, depoisDe, true), model);
    }

    private String mostrar(ListagemQuestoesView listagem, Model model) {
        model.addAttribute("listagem", listagem);
        model.addAttribute("dadosEstruturados", dadosEstruturados.trilhaDaListagem(listagem));
        return "listagem";
    }
}
