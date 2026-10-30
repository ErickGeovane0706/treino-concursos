package br.com.treinoconcursos.service;

import br.com.treinoconcursos.dto.ListagemQuestoesView.ChipFiltroView;
import br.com.treinoconcursos.model.Assunto;
import br.com.treinoconcursos.model.Materia;
import br.com.treinoconcursos.repository.AssuntoRepository;
import br.com.treinoconcursos.repository.MateriaRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Cache em memoria das classificacoes publicas (materias ativas e arvore de assuntos
 * com subassuntos por materia) para evitar consultas repetidas ao banco nas listagens
 * e na navegacao da pagina da questao (RNF03, issue #16).
 *
 * Invalidado automaticamente sempre que uma materia ou assunto e criado, atualizado
 * ou excluido no painel administrativo.
 */
@Service
public class ClassificacaoCacheService {

    private final MateriaRepository materiaRepository;
    private final AssuntoRepository assuntoRepository;

    private final AtomicReference<CatalogoMaterias> catalogoMaterias = new AtomicReference<>();
    private final ConcurrentHashMap<Integer, CatalogoAssuntos> assuntosPorMateria = new ConcurrentHashMap<>();

    public ClassificacaoCacheService(MateriaRepository materiaRepository, AssuntoRepository assuntoRepository) {
        this.materiaRepository = materiaRepository;
        this.assuntoRepository = assuntoRepository;
    }

    /** Limpa todo o cache em memoria (chamado apos alteracoes em materias ou assuntos). */
    public void invalidar() {
        catalogoMaterias.set(null);
        assuntosPorMateria.clear();
    }

    public List<ChipFiltroView> listarMateriasAtivas(String slugMateriaAtiva) {
        return obterCatalogoMaterias().ativas().stream()
                .map(m -> new ChipFiltroView(
                        m.nome(),
                        m.slug(),
                        "/questoes/" + m.slug(),
                        m.slug().equals(slugMateriaAtiva)))
                .toList();
    }

    public Optional<MateriaCache> buscarMateriaPorSlug(String slug) {
        if (slug == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(obterCatalogoMaterias().porSlug().get(slug));
    }

    public Optional<AssuntoCache> buscarAssuntoPorSlug(Integer idMateria, String slugAssunto) {
        if (idMateria == null || slugAssunto == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(obterCatalogoAssuntos(idMateria).porSlug().get(slugAssunto));
    }

    public List<Integer> idsDoAssuntoESubassuntos(Integer idMateria, String slugAssunto) {
        if (idMateria == null || slugAssunto == null) {
            return List.of();
        }
        return obterCatalogoAssuntos(idMateria).idsComSubassuntosPorSlug().getOrDefault(slugAssunto, List.of());
    }

    public List<ChipFiltroView> listarAssuntosAtivosDaMateria(MateriaCache materia, String slugAssuntoAtivo) {
        return obterCatalogoAssuntos(materia.id()).ativos().stream()
                .map(a -> new ChipFiltroView(
                        a.nome(),
                        a.slug(),
                        "/questoes/" + materia.slug() + "/" + a.slug(),
                        a.slug().equals(slugAssuntoAtivo)))
                .toList();
    }

    private CatalogoMaterias obterCatalogoMaterias() {
        CatalogoMaterias atual = catalogoMaterias.get();
        if (atual != null) {
            return atual;
        }
        CatalogoMaterias carregado = carregarCatalogoMaterias();
        catalogoMaterias.compareAndSet(null, carregado);
        return catalogoMaterias.get() != null ? catalogoMaterias.get() : carregado;
    }

    private CatalogoMaterias carregarCatalogoMaterias() {
        List<Materia> todas = materiaRepository.findAll();
        Map<String, MateriaCache> porSlug = new HashMap<>(todas.size() * 2);
        for (Materia m : todas) {
            porSlug.put(m.getSlug(), new MateriaCache(m.getId(), m.getNome(), m.getSlug(), m.isAtiva()));
        }
        List<MateriaCache> ativas = materiaRepository.findByAtivaTrueOrderByNomeAsc().stream()
                .map(m -> new MateriaCache(m.getId(), m.getNome(), m.getSlug(), m.isAtiva()))
                .toList();
        return new CatalogoMaterias(List.copyOf(ativas), Map.copyOf(porSlug));
    }

    private CatalogoAssuntos obterCatalogoAssuntos(Integer idMateria) {
        return assuntosPorMateria.computeIfAbsent(idMateria, this::carregarCatalogoAssuntos);
    }

    private CatalogoAssuntos carregarCatalogoAssuntos(Integer idMateria) {
        List<Assunto> todos = assuntoRepository.findByMateriaIdOrderByNome(idMateria);
        Map<String, AssuntoCache> porSlug = new HashMap<>(todos.size() * 2);
        List<AssuntoCache> ativos = new ArrayList<>();
        Map<Integer, List<Integer>> filhosPorPai = new HashMap<>();

        for (Assunto a : todos) {
            Integer idPai = a.getAssuntoPai() != null ? a.getAssuntoPai().getId() : null;
            AssuntoCache cache = new AssuntoCache(a.getId(), idMateria, idPai, a.getNome(), a.getSlug(), a.isAtivo());
            porSlug.put(a.getSlug(), cache);
            if (a.isAtivo()) {
                ativos.add(cache);
            }
            if (idPai != null) {
                filhosPorPai.computeIfAbsent(idPai, k -> new ArrayList<>()).add(a.getId());
            }
        }

        Map<String, List<Integer>> idsPorSlug = new HashMap<>(todos.size() * 2);
        for (Assunto a : todos) {
            List<Integer> ids = new ArrayList<>();
            Deque<Integer> fila = new ArrayDeque<>();
            fila.add(a.getId());
            while (!fila.isEmpty()) {
                Integer atual = fila.removeFirst();
                ids.add(atual);
                fila.addAll(filhosPorPai.getOrDefault(atual, List.of()));
            }
            idsPorSlug.put(a.getSlug(), List.copyOf(ids));
        }

        return new CatalogoAssuntos(List.copyOf(ativos), Map.copyOf(porSlug), Map.copyOf(idsPorSlug));
    }

    public record MateriaCache(Integer id, String nome, String slug, boolean ativa) {
    }

    public record AssuntoCache(Integer id, Integer idMateria, Integer idAssuntoPai, String nome, String slug,
                               boolean ativo) {
    }

    private record CatalogoMaterias(List<MateriaCache> ativas, Map<String, MateriaCache> porSlug) {
    }

    private record CatalogoAssuntos(List<AssuntoCache> ativos, Map<String, AssuntoCache> porSlug,
                                    Map<String, List<Integer>> idsComSubassuntosPorSlug) {
    }
}
