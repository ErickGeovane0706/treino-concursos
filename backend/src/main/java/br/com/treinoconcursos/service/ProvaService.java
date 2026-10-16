package br.com.treinoconcursos.service;

import br.com.treinoconcursos.dto.ProvaRequest;
import br.com.treinoconcursos.dto.ProvaResponse;
import br.com.treinoconcursos.exception.RecursoNaoEncontradoException;
import br.com.treinoconcursos.exception.RegraNegocioException;
import br.com.treinoconcursos.model.Banca;
import br.com.treinoconcursos.model.Orgao;
import br.com.treinoconcursos.model.Prova;
import br.com.treinoconcursos.repository.ProvaRepository;
import br.com.treinoconcursos.repository.QuestaoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Provas (RF03 RN2 e RN3). */
@Service
public class ProvaService {

    static final int LIMITE_SLUG = 200;

    private final ProvaRepository provaRepository;
    private final QuestaoRepository questaoRepository;
    private final BancaService bancaService;
    private final OrgaoService orgaoService;

    public ProvaService(ProvaRepository provaRepository, QuestaoRepository questaoRepository,
            BancaService bancaService, OrgaoService orgaoService) {
        this.provaRepository = provaRepository;
        this.questaoRepository = questaoRepository;
        this.bancaService = bancaService;
        this.orgaoService = orgaoService;
    }

    @Transactional(readOnly = true)
    public Page<ProvaResponse> listar(String busca, Pageable pageable) {
        String termo = busca == null ? "" : busca.trim();
        return provaRepository.buscar(termo, pageable).map(ProvaResponse::de);
    }

    @Transactional(readOnly = true)
    public ProvaResponse buscar(Integer id) {
        return ProvaResponse.de(buscarEntidade(id));
    }

    @Transactional
    public ProvaResponse criar(ProvaRequest request) {
        String cargo = request.cargo().trim();
        if (provaRepository.existsByBancaIdAndOrgaoIdAndAnoAndCargoIgnoreCase(request.idBanca(), request.idOrgao(),
                request.ano(), cargo)) {
            throw provaRepetida();
        }
        Prova prova = new Prova();
        preencher(prova, request, cargo);
        if (provaRepository.existsBySlug(prova.getSlug())) {
            throw slugRepetido(prova.getSlug());
        }
        return ProvaResponse.de(provaRepository.save(prova));
    }

    @Transactional
    public ProvaResponse atualizar(Integer id, ProvaRequest request) {
        Prova prova = buscarEntidade(id);
        String cargo = request.cargo().trim();
        if (provaRepository.existsByBancaIdAndOrgaoIdAndAnoAndCargoIgnoreCaseAndIdNot(request.idBanca(),
                request.idOrgao(), request.ano(), cargo, id)) {
            throw provaRepetida();
        }
        preencher(prova, request, cargo);
        if (provaRepository.existsBySlugAndIdNot(prova.getSlug(), id)) {
            throw slugRepetido(prova.getSlug());
        }
        return ProvaResponse.de(prova);
    }

    /** RF03 RN3 */
    @Transactional
    public void excluir(Integer id) {
        buscarEntidade(id);
        if (questaoRepository.existsByProvaId(id)) {
            throw new RegraNegocioException("A prova tem questões e não pode ser excluída.");
        }
        provaRepository.deleteById(id);
    }

    Prova buscarEntidade(Integer id) {
        return provaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Prova não encontrada."));
    }

    /** Slug sempre gerado: banca-orgao-ano-cargo (ex.: fgv-tce-go-2026-tecnico-de-controle-externo). */
    private void preencher(Prova prova, ProvaRequest request, String cargo) {
        Banca banca = bancaService.buscarEntidade(request.idBanca());
        Orgao orgao = orgaoService.buscarEntidade(request.idOrgao());
        prova.setBanca(banca);
        prova.setOrgao(orgao);
        prova.setAno(request.ano());
        prova.setCargo(cargo);
        prova.setNivel(request.nivel());
        prova.setSlug(Slugs.informadoOuGerado(null,
                banca.getSlug() + " " + orgao.getSlug() + " " + request.ano() + " " + cargo, LIMITE_SLUG));
    }

    private static RegraNegocioException provaRepetida() {
        return new RegraNegocioException("Já existe uma prova desta banca, órgão, ano e cargo.");
    }

    private static RegraNegocioException slugRepetido(String slug) {
        return new RegraNegocioException("Já existe uma prova com o endereço (slug) \"" + slug + "\".");
    }
}
