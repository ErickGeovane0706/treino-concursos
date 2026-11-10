package br.com.treinoconcursos.service;

import br.com.treinoconcursos.dto.ListagemQuestoesView.ChipFiltroView;
import br.com.treinoconcursos.model.Assunto;
import br.com.treinoconcursos.model.Banca;
import br.com.treinoconcursos.model.Materia;
import br.com.treinoconcursos.model.NivelEscolaridade;
import br.com.treinoconcursos.model.Orgao;
import br.com.treinoconcursos.model.Prova;
import br.com.treinoconcursos.repository.AssuntoRepository;
import br.com.treinoconcursos.repository.BancaRepository;
import br.com.treinoconcursos.repository.MateriaRepository;
import br.com.treinoconcursos.repository.OrgaoRepository;
import br.com.treinoconcursos.repository.ProvaRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Cache em memoria das classificacoes publicas (materias ativas, arvore de assuntos
 * com subassuntos por materia, bancas, orgaos e provas) para evitar consultas repetidas
 * ao banco nas listagens e na navegacao da pagina da questao (RNF03, issues #16 e #17).
 *
 * Invalidado automaticamente sempre que uma materia, assunto, banca, orgao ou prova
 * e criado, atualizado ou excluido no painel administrativo.
 */
@Service
public class ClassificacaoCacheService {

    private final MateriaRepository materiaRepository;
    private final AssuntoRepository assuntoRepository;
    private final BancaRepository bancaRepository;
    private final OrgaoRepository orgaoRepository;
    private final ProvaRepository provaRepository;

    private final AtomicReference<CatalogoMaterias> catalogoMaterias = new AtomicReference<>();
    private final ConcurrentHashMap<Integer, CatalogoAssuntos> assuntosPorMateria = new ConcurrentHashMap<>();
    private final AtomicReference<CatalogoProvas> catalogoProvas = new AtomicReference<>();

    public ClassificacaoCacheService(
            MateriaRepository materiaRepository,
            AssuntoRepository assuntoRepository,
            BancaRepository bancaRepository,
            OrgaoRepository orgaoRepository,
            ProvaRepository provaRepository) {
        this.materiaRepository = materiaRepository;
        this.assuntoRepository = assuntoRepository;
        this.bancaRepository = bancaRepository;
        this.orgaoRepository = orgaoRepository;
        this.provaRepository = provaRepository;
    }

    /** Limpa todo o cache em memoria (chamado apos alteracoes em classificacoes ou provas). */
    public void invalidar() {
        catalogoMaterias.set(null);
        assuntosPorMateria.clear();
        catalogoProvas.set(null);
    }

    public List<MateriaCache> listarMaterias() {
        return obterCatalogoMaterias().ativas();
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

    public List<AssuntoCache> listarAssuntosAtivos(Integer idMateria) {
        if (idMateria == null) {
            return List.of();
        }
        return obterCatalogoAssuntos(idMateria).ativos();
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

    public Optional<BancaCache> buscarBancaPorSlug(String slug) {
        if (slug == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(obterCatalogoProvas().bancasPorSlug().get(slug));
    }

    public Optional<OrgaoCache> buscarOrgaoPorSlug(String slug) {
        if (slug == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(obterCatalogoProvas().orgaosPorSlug().get(slug));
    }

    public List<BancaCache> listarBancas() {
        return obterCatalogoProvas().bancas();
    }

    public List<OrgaoCache> listarOrgaos() {
        return obterCatalogoProvas().orgaos();
    }

    public List<Integer> listarAnos() {
        return obterCatalogoProvas().anos();
    }

    /**
     * Resolve em memoria os ids de provas que atendem simultaneamente aos filtros de
     * banca, orgao, ano e nivel informados, permitindo usar o indice parcial
     * ix_questoes_pub_prova (id_prova, id_questao) sem Seq Scan (RF07).
     */
    public List<Integer> idsDeProvas(Integer idBanca, Integer idOrgao, Integer ano, NivelEscolaridade nivel) {
        List<ProvaCache> todas = obterCatalogoProvas().provas();
        List<Integer> ids = new ArrayList<>();
        for (ProvaCache p : todas) {
            if (idBanca != null && !idBanca.equals(p.idBanca())) {
                continue;
            }
            if (idOrgao != null && !idOrgao.equals(p.idOrgao())) {
                continue;
            }
            if (ano != null && !ano.equals(p.ano())) {
                continue;
            }
            if (nivel != null && nivel != p.nivel()) {
                continue;
            }
            ids.add(p.id());
        }
        return List.copyOf(ids);
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

    private CatalogoProvas obterCatalogoProvas() {
        CatalogoProvas atual = catalogoProvas.get();
        if (atual != null) {
            return atual;
        }
        CatalogoProvas carregado = carregarCatalogoProvas();
        catalogoProvas.compareAndSet(null, carregado);
        return catalogoProvas.get() != null ? catalogoProvas.get() : carregado;
    }

    private CatalogoProvas carregarCatalogoProvas() {
        List<Banca> bancasDb = bancaRepository.findAllByOrderBySiglaAsc();
        List<BancaCache> bancas = new ArrayList<>(bancasDb.size());
        Map<String, BancaCache> bancasPorSlug = new HashMap<>(bancasDb.size() * 2);
        for (Banca b : bancasDb) {
            BancaCache cache = new BancaCache(b.getId(), b.getNome(), b.getSigla(), b.getSlug());
            bancas.add(cache);
            bancasPorSlug.put(b.getSlug(), cache);
        }

        List<Orgao> orgaosDb = orgaoRepository.findAllByOrderBySiglaAsc();
        List<OrgaoCache> orgaos = new ArrayList<>(orgaosDb.size());
        Map<String, OrgaoCache> orgaosPorSlug = new HashMap<>(orgaosDb.size() * 2);
        for (Orgao o : orgaosDb) {
            OrgaoCache cache = new OrgaoCache(o.getId(), o.getNome(), o.getSigla(), o.getSlug(), o.getUf());
            orgaos.add(cache);
            orgaosPorSlug.put(o.getSlug(), cache);
        }

        List<Prova> provasDb = provaRepository.findAllByOrderByIdAsc();
        List<ProvaCache> provas = new ArrayList<>(provasDb.size());
        for (Prova p : provasDb) {
            provas.add(new ProvaCache(
                    p.getId(),
                    p.getBanca().getId(),
                    p.getOrgao().getId(),
                    p.getAno(),
                    p.getNivel()));
        }

        List<Integer> anos = provas.stream()
                .map(ProvaCache::ano)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();

        return new CatalogoProvas(
                List.copyOf(bancas),
                Map.copyOf(bancasPorSlug),
                List.copyOf(orgaos),
                Map.copyOf(orgaosPorSlug),
                List.copyOf(anos),
                List.copyOf(provas));
    }

    public record MateriaCache(Integer id, String nome, String slug, boolean ativa) {
    }

    public record AssuntoCache(Integer id, Integer idMateria, Integer idAssuntoPai, String nome, String slug,
                               boolean ativo) {
    }

    public record BancaCache(Integer id, String nome, String sigla, String slug) {
    }

    public record OrgaoCache(Integer id, String nome, String sigla, String slug, String uf) {
    }

    public record ProvaCache(Integer id, Integer idBanca, Integer idOrgao, Integer ano, NivelEscolaridade nivel) {
    }

    private record CatalogoMaterias(List<MateriaCache> ativas, Map<String, MateriaCache> porSlug) {
    }

    private record CatalogoAssuntos(List<AssuntoCache> ativos, Map<String, AssuntoCache> porSlug,
                                    Map<String, List<Integer>> idsComSubassuntosPorSlug) {
    }

    private record CatalogoProvas(
            List<BancaCache> bancas,
            Map<String, BancaCache> bancasPorSlug,
            List<OrgaoCache> orgaos,
            Map<String, OrgaoCache> orgaosPorSlug,
            List<Integer> anos,
            List<ProvaCache> provas) {
    }
}
