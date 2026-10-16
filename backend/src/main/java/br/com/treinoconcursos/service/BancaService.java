package br.com.treinoconcursos.service;

import br.com.treinoconcursos.dto.BancaRequest;
import br.com.treinoconcursos.dto.BancaResponse;
import br.com.treinoconcursos.exception.RecursoNaoEncontradoException;
import br.com.treinoconcursos.exception.RegraNegocioException;
import br.com.treinoconcursos.model.Banca;
import br.com.treinoconcursos.repository.BancaRepository;
import br.com.treinoconcursos.repository.ProvaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bancas (RF03 RN1 e RN3). */
@Service
public class BancaService {

    static final int LIMITE_SLUG = 120;

    private final BancaRepository bancaRepository;
    private final ProvaRepository provaRepository;

    public BancaService(BancaRepository bancaRepository, ProvaRepository provaRepository) {
        this.bancaRepository = bancaRepository;
        this.provaRepository = provaRepository;
    }

    @Transactional(readOnly = true)
    public Page<BancaResponse> listar(String busca, Pageable pageable) {
        Page<Banca> pagina = busca == null || busca.isBlank()
                ? bancaRepository.findAll(pageable)
                : bancaRepository.findByNomeContainingIgnoreCaseOrSiglaContainingIgnoreCase(busca.trim(), busca.trim(),
                        pageable);
        return pagina.map(BancaResponse::de);
    }

    @Transactional(readOnly = true)
    public BancaResponse buscar(Integer id) {
        return BancaResponse.de(buscarEntidade(id));
    }

    @Transactional
    public BancaResponse criar(BancaRequest request) {
        String nome = request.nome().trim();
        String sigla = request.sigla().trim();
        String slug = Slugs.informadoOuGerado(request.slug(), sigla, LIMITE_SLUG);
        if (bancaRepository.existsByNomeIgnoreCase(nome)) {
            throw new RegraNegocioException("Já existe uma banca com este nome.");
        }
        if (bancaRepository.existsBySiglaIgnoreCase(sigla)) {
            throw new RegraNegocioException("Já existe uma banca com esta sigla.");
        }
        if (bancaRepository.existsBySlug(slug)) {
            throw new RegraNegocioException("Já existe uma banca com o endereço (slug) \"" + slug + "\".");
        }
        Banca banca = new Banca();
        banca.setNome(nome);
        banca.setSigla(sigla);
        banca.setSlug(slug);
        return BancaResponse.de(bancaRepository.save(banca));
    }

    @Transactional
    public BancaResponse atualizar(Integer id, BancaRequest request) {
        Banca banca = buscarEntidade(id);
        String nome = request.nome().trim();
        String sigla = request.sigla().trim();
        if (bancaRepository.existsByNomeIgnoreCaseAndIdNot(nome, id)) {
            throw new RegraNegocioException("Já existe uma banca com este nome.");
        }
        if (bancaRepository.existsBySiglaIgnoreCaseAndIdNot(sigla, id)) {
            throw new RegraNegocioException("Já existe uma banca com esta sigla.");
        }
        if (request.slug() != null && !request.slug().isBlank()) {
            String slug = Slugs.informadoOuGerado(request.slug(), sigla, LIMITE_SLUG);
            if (bancaRepository.existsBySlugAndIdNot(slug, id)) {
                throw new RegraNegocioException("Já existe uma banca com o endereço (slug) \"" + slug + "\".");
            }
            banca.setSlug(slug);
        }
        banca.setNome(nome);
        banca.setSigla(sigla);
        return BancaResponse.de(banca);
    }

    /** RF03 RN3: com provas cadastradas (e, portanto, possivelmente com questoes), nao exclui. */
    @Transactional
    public void excluir(Integer id) {
        buscarEntidade(id);
        if (provaRepository.existsByBancaId(id)) {
            throw new RegraNegocioException("A banca tem provas cadastradas e não pode ser excluída.");
        }
        bancaRepository.deleteById(id);
    }

    Banca buscarEntidade(Integer id) {
        return bancaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Banca não encontrada."));
    }
}
