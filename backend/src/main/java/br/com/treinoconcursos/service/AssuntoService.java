package br.com.treinoconcursos.service;

import br.com.treinoconcursos.dto.AssuntoRequest;
import br.com.treinoconcursos.dto.AssuntoResponse;
import br.com.treinoconcursos.exception.RecursoNaoEncontradoException;
import br.com.treinoconcursos.exception.RegraNegocioException;
import br.com.treinoconcursos.model.Assunto;
import br.com.treinoconcursos.model.Materia;
import br.com.treinoconcursos.repository.AssuntoRepository;
import br.com.treinoconcursos.repository.QuestaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Assuntos de uma materia (RF02 RN2, RN3 e RN4). Cada materia tem poucos
 * assuntos, entao a arvore e montada em memoria.
 */
@Service
public class AssuntoService {

    /** RF02 RN2: no maximo 3 niveis (assunto, subassunto, sub-subassunto). */
    static final int MAXIMO_NIVEIS = 3;
    static final int LIMITE_SLUG = 170;

    private final AssuntoRepository assuntoRepository;
    private final QuestaoRepository questaoRepository;
    private final MateriaService materiaService;
    private final ClassificacaoCacheService classificacaoCacheService;

    public AssuntoService(AssuntoRepository assuntoRepository, QuestaoRepository questaoRepository,
            MateriaService materiaService, ClassificacaoCacheService classificacaoCacheService) {
        this.assuntoRepository = assuntoRepository;
        this.questaoRepository = questaoRepository;
        this.materiaService = materiaService;
        this.classificacaoCacheService = classificacaoCacheService;
    }

    @Transactional(readOnly = true)
    public List<AssuntoResponse> listar(Integer idMateria) {
        materiaService.buscarEntidade(idMateria);
        Arvore arvore = new Arvore(assuntoRepository.findByMateriaIdOrderByNome(idMateria));
        return arvore.assuntos.stream().map(arvore::resposta).toList();
    }

    @Transactional(readOnly = true)
    public AssuntoResponse buscar(Integer idMateria, Integer id) {
        Assunto assunto = buscarEntidade(idMateria, id);
        return new Arvore(assuntoRepository.findByMateriaIdOrderByNome(idMateria)).resposta(assunto);
    }

    @Transactional
    public AssuntoResponse criar(Integer idMateria, AssuntoRequest request) {
        Materia materia = materiaService.buscarEntidade(idMateria);
        Arvore arvore = new Arvore(assuntoRepository.findByMateriaIdOrderByNome(idMateria));
        String nome = request.nome().trim();
        String slug = Slugs.informadoOuGerado(request.slug(), nome, LIMITE_SLUG);
        if (assuntoRepository.existsByMateriaIdAndSlug(idMateria, slug)) {
            throw slugRepetido(slug);
        }
        Assunto pai = validarPai(arvore, request.idAssuntoPai(), null);

        Assunto assunto = new Assunto();
        assunto.setMateria(materia);
        assunto.setAssuntoPai(pai);
        assunto.setNome(nome);
        assunto.setSlug(slug);
        assunto.setAtivo(request.ativo());
        assuntoRepository.save(assunto);
        classificacaoCacheService.invalidar();
        return new Arvore(assuntoRepository.findByMateriaIdOrderByNome(idMateria)).resposta(assunto);
    }

    @Transactional
    public AssuntoResponse atualizar(Integer idMateria, Integer id, AssuntoRequest request) {
        Assunto assunto = buscarEntidade(idMateria, id);
        Arvore arvore = new Arvore(assuntoRepository.findByMateriaIdOrderByNome(idMateria));
        String nome = request.nome().trim();
        if (request.slug() != null && !request.slug().isBlank()) {
            String slug = Slugs.informadoOuGerado(request.slug(), nome, LIMITE_SLUG);
            if (!slug.equals(assunto.getSlug())) {
                if (questaoRepository.existsByAssuntoIdAndPublicadoEmIsNotNull(id)) {
                    throw new RegraNegocioException("O endereço (slug) não pode mudar: o assunto já tem questões "
                            + "publicadas e o endereço antigo pode estar no Google.");
                }
                if (assuntoRepository.existsByMateriaIdAndSlugAndIdNot(idMateria, slug, id)) {
                    throw slugRepetido(slug);
                }
                assunto.setSlug(slug);
            }
        }
        assunto.setAssuntoPai(validarPai(arvore, request.idAssuntoPai(), assunto));
        assunto.setNome(nome);
        assunto.setAtivo(request.ativo());
        classificacaoCacheService.invalidar();
        return new Arvore(assuntoRepository.findByMateriaIdOrderByNome(idMateria)).resposta(assunto);
    }

    /** RF02 RN3: com questoes, nao exclui. Com subassuntos, tambem nao (evita apagar a arvore sem querer). */
    @Transactional
    public void excluir(Integer idMateria, Integer id) {
        buscarEntidade(idMateria, id);
        if (questaoRepository.existsByAssuntoId(id)) {
            throw new RegraNegocioException("O assunto tem questões e não pode ser excluído. Inative-o.");
        }
        if (assuntoRepository.existsByAssuntoPaiId(id)) {
            throw new RegraNegocioException("O assunto tem subassuntos. Exclua-os ou mova-os antes.");
        }
        assuntoRepository.deleteById(id);
        classificacaoCacheService.invalidar();
    }

    /**
     * RF02 RN2: o pai e da mesma materia, nao cria ciclo, e o assunto (com
     * todos os seus descendentes) continua em no maximo 3 niveis.
     */
    private Assunto validarPai(Arvore arvore, Integer idPai, Assunto assunto) {
        if (idPai == null) {
            if (assunto != null && arvore.altura(assunto.getId()) > MAXIMO_NIVEIS) {
                throw niveisDemais();
            }
            return null;
        }
        Assunto pai = arvore.porId.get(idPai);
        if (pai == null) {
            throw new RegraNegocioException("O assunto pai deve ser da mesma matéria.");
        }
        if (assunto != null && (idPai.equals(assunto.getId()) || arvore.ehDescendente(idPai, assunto.getId()))) {
            throw new RegraNegocioException("Um assunto não pode ficar dentro dele mesmo ou de um subassunto seu.");
        }
        int alturaDoAssunto = assunto == null ? 1 : arvore.altura(assunto.getId());
        if (arvore.nivel(pai) + alturaDoAssunto > MAXIMO_NIVEIS) {
            throw niveisDemais();
        }
        return pai;
    }

    private Assunto buscarEntidade(Integer idMateria, Integer id) {
        Assunto assunto = assuntoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Assunto não encontrado."));
        if (!assunto.getMateria().getId().equals(idMateria)) {
            throw new RecursoNaoEncontradoException("Assunto não encontrado.");
        }
        return assunto;
    }

    private static RegraNegocioException slugRepetido(String slug) {
        return new RegraNegocioException("Já existe um assunto com o endereço (slug) \"" + slug + "\" nesta matéria.");
    }

    private static RegraNegocioException niveisDemais() {
        return new RegraNegocioException("Os assuntos podem ter no máximo " + MAXIMO_NIVEIS + " níveis.");
    }

    /** Os assuntos de uma materia, com acesso por id e pelos filhos de cada um. */
    private static final class Arvore {

        private final List<Assunto> assuntos;
        private final Map<Integer, Assunto> porId = new HashMap<>();
        private final Map<Integer, List<Assunto>> filhos = new HashMap<>();

        Arvore(List<Assunto> assuntos) {
            this.assuntos = assuntos;
            for (Assunto a : assuntos) {
                porId.put(a.getId(), a);
                if (a.getAssuntoPai() != null) {
                    filhos.computeIfAbsent(a.getAssuntoPai().getId(), k -> new ArrayList<>()).add(a);
                }
            }
        }

        /** 1 para assunto raiz. */
        int nivel(Assunto assunto) {
            int nivel = 1;
            for (Assunto pai = assunto.getAssuntoPai(); pai != null; pai = porId.get(pai.getId()).getAssuntoPai()) {
                nivel++;
            }
            return nivel;
        }

        /** 1 para assunto sem filhos; 2 se tem filhos sem netos; etc. */
        int altura(Integer id) {
            return 1 + filhos.getOrDefault(id, List.of()).stream().mapToInt(f -> altura(f.getId())).max().orElse(0);
        }

        boolean ehDescendente(Integer candidato, Integer ancestral) {
            return filhos.getOrDefault(ancestral, List.of()).stream()
                    .anyMatch(f -> f.getId().equals(candidato) || ehDescendente(candidato, f.getId()));
        }

        AssuntoResponse resposta(Assunto a) {
            Integer idPai = a.getAssuntoPai() == null ? null : a.getAssuntoPai().getId();
            return new AssuntoResponse(a.getId(), a.getMateria().getId(), idPai, a.getNome(), a.getSlug(), a.isAtivo(),
                    nivel(a));
        }
    }
}
