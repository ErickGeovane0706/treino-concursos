package br.com.treinoconcursos.service;

import br.com.treinoconcursos.dto.MateriaRequest;
import br.com.treinoconcursos.dto.MateriaResponse;
import br.com.treinoconcursos.exception.RecursoNaoEncontradoException;
import br.com.treinoconcursos.exception.RegraNegocioException;
import br.com.treinoconcursos.model.Materia;
import br.com.treinoconcursos.repository.AssuntoRepository;
import br.com.treinoconcursos.repository.MateriaRepository;
import br.com.treinoconcursos.repository.QuestaoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Materias (RF02 RN1, RN3 e RN4). */
@Service
public class MateriaService {

    static final int LIMITE_SLUG = 120;

    private final MateriaRepository materiaRepository;
    private final AssuntoRepository assuntoRepository;
    private final QuestaoRepository questaoRepository;

    public MateriaService(MateriaRepository materiaRepository, AssuntoRepository assuntoRepository,
            QuestaoRepository questaoRepository) {
        this.materiaRepository = materiaRepository;
        this.assuntoRepository = assuntoRepository;
        this.questaoRepository = questaoRepository;
    }

    @Transactional(readOnly = true)
    public Page<MateriaResponse> listar(String busca, Pageable pageable) {
        Page<Materia> pagina = busca == null || busca.isBlank()
                ? materiaRepository.findAll(pageable)
                : materiaRepository.findByNomeContainingIgnoreCase(busca.trim(), pageable);
        return pagina.map(MateriaResponse::de);
    }

    @Transactional(readOnly = true)
    public MateriaResponse buscar(Integer id) {
        return MateriaResponse.de(buscarEntidade(id));
    }

    @Transactional
    public MateriaResponse criar(MateriaRequest request) {
        String nome = request.nome().trim();
        String slug = Slugs.informadoOuGerado(request.slug(), nome, LIMITE_SLUG);
        if (materiaRepository.existsByNomeIgnoreCase(nome)) {
            throw new RegraNegocioException("Já existe uma matéria com este nome.");
        }
        if (materiaRepository.existsBySlug(slug)) {
            throw new RegraNegocioException("Já existe uma matéria com o endereço (slug) \"" + slug + "\".");
        }
        Materia materia = new Materia();
        materia.setNome(nome);
        materia.setSlug(slug);
        materia.setAtiva(request.ativa());
        return MateriaResponse.de(materiaRepository.save(materia));
    }

    @Transactional
    public MateriaResponse atualizar(Integer id, MateriaRequest request) {
        Materia materia = buscarEntidade(id);
        String nome = request.nome().trim();
        if (materiaRepository.existsByNomeIgnoreCaseAndIdNot(nome, id)) {
            throw new RegraNegocioException("Já existe uma matéria com este nome.");
        }
        if (request.slug() != null && !request.slug().isBlank()) {
            String slug = Slugs.informadoOuGerado(request.slug(), nome, LIMITE_SLUG);
            if (!slug.equals(materia.getSlug())) {
                if (questaoRepository.existsByMateriaIdAndPublicadoEmIsNotNull(id)) {
                    throw new RegraNegocioException("O endereço (slug) não pode mudar: a matéria já tem questões "
                            + "publicadas e o endereço antigo pode estar no Google.");
                }
                if (materiaRepository.existsBySlugAndIdNot(slug, id)) {
                    throw new RegraNegocioException("Já existe uma matéria com o endereço (slug) \"" + slug + "\".");
                }
                materia.setSlug(slug);
            }
        }
        materia.setNome(nome);
        materia.setAtiva(request.ativa());
        return MateriaResponse.de(materia);
    }

    /** RF02 RN3: com questoes, nao exclui (o editor deve inativar). Os assuntos vao junto. */
    @Transactional
    public void excluir(Integer id) {
        buscarEntidade(id);
        if (questaoRepository.existsByMateriaId(id)) {
            throw new RegraNegocioException("A matéria tem questões e não pode ser excluída. Inative-a.");
        }
        assuntoRepository.soltarHierarquiaDaMateria(id);
        assuntoRepository.excluirDaMateria(id);
        materiaRepository.deleteById(id);
    }

    Materia buscarEntidade(Integer id) {
        return materiaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Matéria não encontrada."));
    }
}
