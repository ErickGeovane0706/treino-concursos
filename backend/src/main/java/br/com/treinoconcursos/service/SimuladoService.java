package br.com.treinoconcursos.service;

import br.com.treinoconcursos.dto.QuestaoDoSimuladoResponse;
import br.com.treinoconcursos.dto.SimuladoRequest;
import br.com.treinoconcursos.dto.SimuladoResponse;
import br.com.treinoconcursos.dto.SimuladoResumoResponse;
import br.com.treinoconcursos.exception.RecursoNaoEncontradoException;
import br.com.treinoconcursos.exception.RegraNegocioException;
import br.com.treinoconcursos.model.Questao;
import br.com.treinoconcursos.model.Simulado;
import br.com.treinoconcursos.model.SimuladoQuestao;
import br.com.treinoconcursos.model.StatusQuestao;
import br.com.treinoconcursos.model.StatusSimulado;
import br.com.treinoconcursos.repository.QuestaoRepository;
import br.com.treinoconcursos.repository.SimuladoQuestaoRepository;
import br.com.treinoconcursos.repository.SimuladoRepository;
import br.com.treinoconcursos.repository.UsuarioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Montagem e ciclo de vida dos simulados (RF14 RN1-RN4, US11). */
@Service
public class SimuladoService {

    public static final int MINIMO_QUESTOES = 5;
    public static final int MAXIMO_QUESTOES = 100;
    static final int LIMITE_SLUG = 150;

    private final SimuladoRepository simuladoRepository;
    private final SimuladoQuestaoRepository simuladoQuestaoRepository;
    private final QuestaoRepository questaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final Clock relogio;

    public SimuladoService(SimuladoRepository simuladoRepository, SimuladoQuestaoRepository simuladoQuestaoRepository,
            QuestaoRepository questaoRepository, UsuarioRepository usuarioRepository, Clock relogio) {
        this.simuladoRepository = simuladoRepository;
        this.simuladoQuestaoRepository = simuladoQuestaoRepository;
        this.questaoRepository = questaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public Page<SimuladoResumoResponse> listar(StatusSimulado status, String busca, Pageable pageable) {
        return simuladoRepository.buscar(status, busca == null ? "" : busca.trim(), pageable);
    }

    @Transactional(readOnly = true)
    public SimuladoResponse buscar(Integer id) {
        return resposta(buscarEntidade(id));
    }

    /** Cria como RASCUNHO; o rascunho pode ter menos de 5 questoes enquanto e montado. */
    @Transactional
    public SimuladoResponse criar(SimuladoRequest request, Integer idAutor) {
        String slug = Slugs.informadoOuGerado(request.slug(), request.titulo(), LIMITE_SLUG);
        if (simuladoRepository.existsBySlug(slug)) {
            throw slugEmUso(slug);
        }
        List<Integer> ids = validarQuestoes(request.idsQuestoes(), false);

        Simulado simulado = new Simulado();
        simulado.setAutor(usuarioRepository.findById(idAutor)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado.")));
        simulado.setSlug(slug);
        preencher(simulado, request);
        simulado = simuladoRepository.save(simulado);
        gravarQuestoes(simulado.getId(), ids);
        return buscar(simulado.getId());
    }

    /**
     * Altera dados e questoes. Um simulado publicado continua com 5 a 100 questoes elegiveis;
     * como todas sao validadas aqui, a sinalizacao de revisao (RN4) e limpa.
     */
    @Transactional
    public SimuladoResponse atualizar(Integer id, SimuladoRequest request) {
        Simulado simulado = buscarEntidade(id);
        if (request.slug() != null && !request.slug().isBlank()) {
            String slug = Slugs.informadoOuGerado(request.slug(), request.titulo(), LIMITE_SLUG);
            if (!slug.equals(simulado.getSlug())) {
                if (simulado.getPublicadoEm() != null) {
                    throw new RegraNegocioException(
                            "O endereço (slug) não pode mudar depois que o simulado foi publicado.");
                }
                if (simuladoRepository.existsBySlugAndIdNot(slug, id)) {
                    throw slugEmUso(slug);
                }
                simulado.setSlug(slug);
            }
        }
        List<Integer> ids = validarQuestoes(request.idsQuestoes(), simulado.getStatus() == StatusSimulado.PUBLICADO);
        preencher(simulado, request);
        simulado.setPrecisaRevisao(false);
        gravarQuestoes(id, ids);
        return buscar(id);
    }

    /** So rascunho nunca publicado: o endereco de um simulado publicado pode estar indexado. */
    @Transactional
    public void excluir(Integer id) {
        Simulado simulado = buscarEntidade(id);
        if (simulado.getPublicadoEm() != null) {
            throw new RegraNegocioException("Um simulado que já foi publicado não pode ser excluído. Arquive-o.");
        }
        simuladoRepository.delete(simulado);
    }

    /** RF14 RN1 e RN3: de RASCUNHO ou ARQUIVADO para PUBLICADO, com 5 a 100 questoes elegiveis. */
    @Transactional
    public SimuladoResponse publicar(Integer id) {
        Simulado simulado = buscarEntidade(id);
        if (simulado.getStatus() == StatusSimulado.PUBLICADO) {
            throw new RegraNegocioException("O simulado já está publicado.");
        }
        List<Integer> ids = simuladoQuestaoRepository.listarPorSimulado(id).stream()
                .map(SimuladoQuestao::getIdQuestao).toList();
        validarQuestoes(ids, true);
        simulado.setStatus(StatusSimulado.PUBLICADO);
        simulado.setPrecisaRevisao(false);
        if (simulado.getPublicadoEm() == null) {
            simulado.setPublicadoEm(LocalDateTime.now(relogio));
        }
        return resposta(simulado);
    }

    /** RF14 RN3: arquivado sai do site. */
    @Transactional
    public SimuladoResponse arquivar(Integer id) {
        Simulado simulado = buscarEntidade(id);
        if (simulado.getStatus() == StatusSimulado.ARQUIVADO) {
            throw new RegraNegocioException("O simulado já está arquivado.");
        }
        simulado.setStatus(StatusSimulado.ARQUIVADO);
        return resposta(simulado);
    }

    /**
     * RF14 RN4: chamado pelo QuestaoService, na mesma transacao, quando a questao e
     * arquivada, anulada ou marcada como desatualizada.
     */
    @Transactional
    public void questaoDeixouDeSerElegivel(Integer idQuestao) {
        simuladoRepository.marcarRevisaoPorQuestao(idQuestao);
    }

    Simulado buscarEntidade(Integer id) {
        return simuladoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Simulado não encontrado."));
    }

    private void preencher(Simulado simulado, SimuladoRequest request) {
        simulado.setTitulo(request.titulo().strip());
        simulado.setDescricao(request.descricao() == null || request.descricao().isBlank()
                ? null : request.descricao().strip());
        simulado.setTempoSugeridoMin(request.tempoSugeridoMin());
    }

    /**
     * RF14 RN1 e RN2: sem repeticao, no maximo 100, todas existentes e elegiveis.
     * O minimo de 5 vale para publicar e para alterar um simulado publicado.
     */
    private List<Integer> validarQuestoes(List<Integer> ids, boolean exigirMinimo) {
        Set<Integer> vistos = new HashSet<>();
        for (Integer id : ids) {
            if (!vistos.add(id)) {
                throw new RegraNegocioException("A questão #" + id + " aparece mais de uma vez no simulado.");
            }
        }
        if (ids.size() > MAXIMO_QUESTOES) {
            throw new RegraNegocioException("O simulado pode ter no máximo " + MAXIMO_QUESTOES + " questões.");
        }
        if (exigirMinimo && ids.size() < MINIMO_QUESTOES) {
            throw new RegraNegocioException("O simulado precisa de pelo menos " + MINIMO_QUESTOES
                    + " questões para ser publicado (tem " + ids.size() + ").");
        }
        Map<Integer, Questao> porId = questaoRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Questao::getId, Function.identity()));
        for (Integer id : ids) {
            Questao questao = porId.get(id);
            if (questao == null) {
                throw new RegraNegocioException("A questão #" + id + " não existe.");
            }
            if (!QuestaoDoSimuladoResponse.elegivel(questao)) {
                throw new RegraNegocioException("A questão #" + id + " não pode entrar no simulado: "
                        + motivoInelegivel(questao) + ".");
            }
        }
        return ids;
    }

    private static String motivoInelegivel(Questao questao) {
        if (questao.getStatus() != StatusQuestao.PUBLICADA) {
            return questao.getStatus() == StatusQuestao.ARQUIVADA ? "está arquivada" : "ainda não foi publicada";
        }
        return questao.isAnulada() ? "foi anulada pela banca" : "está desatualizada";
    }

    /** Apaga e insere a lista inteira, na ordem recebida (ordem comeca em 1). */
    private void gravarQuestoes(Integer idSimulado, List<Integer> ids) {
        simuladoQuestaoRepository.apagarPorSimulado(idSimulado);
        for (int i = 0; i < ids.size(); i++) {
            simuladoQuestaoRepository.inserir(idSimulado, ids.get(i), i + 1);
        }
    }

    private SimuladoResponse resposta(Simulado simulado) {
        List<QuestaoDoSimuladoResponse> questoes = simuladoQuestaoRepository.listarPorSimulado(simulado.getId())
                .stream().map(sq -> QuestaoDoSimuladoResponse.de(sq.getOrdem(), sq.getQuestao())).toList();
        return SimuladoResponse.de(simulado, questoes);
    }

    private static RegraNegocioException slugEmUso(String slug) {
        return new RegraNegocioException("Já existe um simulado com o endereço (slug) \"" + slug + "\".");
    }
}
