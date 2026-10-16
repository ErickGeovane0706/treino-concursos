package br.com.treinoconcursos.service;

import br.com.treinoconcursos.dto.OrgaoRequest;
import br.com.treinoconcursos.dto.OrgaoResponse;
import br.com.treinoconcursos.exception.RecursoNaoEncontradoException;
import br.com.treinoconcursos.exception.RegraNegocioException;
import br.com.treinoconcursos.model.EsferaOrgao;
import br.com.treinoconcursos.model.Orgao;
import br.com.treinoconcursos.repository.OrgaoRepository;
import br.com.treinoconcursos.repository.ProvaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/** Orgaos (RF03 RN1 e RN3). */
@Service
public class OrgaoService {

    static final int LIMITE_SLUG = 120;

    private final OrgaoRepository orgaoRepository;
    private final ProvaRepository provaRepository;

    public OrgaoService(OrgaoRepository orgaoRepository, ProvaRepository provaRepository) {
        this.orgaoRepository = orgaoRepository;
        this.provaRepository = provaRepository;
    }

    @Transactional(readOnly = true)
    public Page<OrgaoResponse> listar(String busca, Pageable pageable) {
        Page<Orgao> pagina = busca == null || busca.isBlank()
                ? orgaoRepository.findAll(pageable)
                : orgaoRepository.findByNomeContainingIgnoreCaseOrSiglaContainingIgnoreCase(busca.trim(), busca.trim(),
                        pageable);
        return pagina.map(OrgaoResponse::de);
    }

    @Transactional(readOnly = true)
    public OrgaoResponse buscar(Integer id) {
        return OrgaoResponse.de(buscarEntidade(id));
    }

    @Transactional
    public OrgaoResponse criar(OrgaoRequest request) {
        String sigla = request.sigla().trim();
        String slug = Slugs.informadoOuGerado(request.slug(), sigla, LIMITE_SLUG);
        if (orgaoRepository.existsBySlug(slug)) {
            throw slugRepetido(slug);
        }
        Orgao orgao = new Orgao();
        orgao.setSlug(slug);
        preencher(orgao, request);
        return OrgaoResponse.de(orgaoRepository.save(orgao));
    }

    @Transactional
    public OrgaoResponse atualizar(Integer id, OrgaoRequest request) {
        Orgao orgao = buscarEntidade(id);
        if (request.slug() != null && !request.slug().isBlank()) {
            String slug = Slugs.informadoOuGerado(request.slug(), request.sigla(), LIMITE_SLUG);
            if (orgaoRepository.existsBySlugAndIdNot(slug, id)) {
                throw slugRepetido(slug);
            }
            orgao.setSlug(slug);
        }
        preencher(orgao, request);
        return OrgaoResponse.de(orgao);
    }

    /** RF03 RN3 */
    @Transactional
    public void excluir(Integer id) {
        buscarEntidade(id);
        if (provaRepository.existsByOrgaoId(id)) {
            throw new RegraNegocioException("O órgão tem provas cadastradas e não pode ser excluído.");
        }
        orgaoRepository.deleteById(id);
    }

    Orgao buscarEntidade(Integer id) {
        return orgaoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Órgão não encontrado."));
    }

    /** RF03 RN1: UF obrigatoria fora da esfera federal; na federal, nao se guarda UF. */
    private static void preencher(Orgao orgao, OrgaoRequest request) {
        boolean federal = request.esfera() == EsferaOrgao.FEDERAL;
        if (!federal && (request.uf() == null || request.uf().isBlank())) {
            throw new RegraNegocioException("Informe a UF: ela é obrigatória para órgãos estaduais e municipais.");
        }
        orgao.setNome(request.nome().trim());
        orgao.setSigla(request.sigla().trim());
        orgao.setEsfera(request.esfera());
        orgao.setUf(federal ? null : request.uf().toUpperCase(Locale.ROOT));
    }

    private static RegraNegocioException slugRepetido(String slug) {
        return new RegraNegocioException("Já existe um órgão com o endereço (slug) \"" + slug + "\".");
    }
}
