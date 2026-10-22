package br.com.treinoconcursos.service;

import br.com.treinoconcursos.config.SiteConfig;
import br.com.treinoconcursos.dto.QuestaoSitemap;
import br.com.treinoconcursos.dto.UltimaAtualizacao;
import br.com.treinoconcursos.model.Assunto;
import br.com.treinoconcursos.model.Materia;
import br.com.treinoconcursos.repository.AssuntoRepository;
import br.com.treinoconcursos.repository.MateriaRepository;
import br.com.treinoconcursos.repository.QuestaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sitemaps do site (RF09 RN1, US14).
 *
 * - {@code /sitemap.xml}: indice com {@code /sitemaps/paginas.xml} e um
 *   {@code /sitemaps/questoes-{n}.xml} por bloco de ids.
 * - O bloco n tem as questoes PUBLICADAS com id em ((n-1) * 50.000, n * 50.000]:
 *   nunca passa do limite de 50.000 URLs por arquivo e cada questao fica sempre no
 *   mesmo arquivo, mesmo quando entram questoes novas.
 * - {@code lastmod} vem de {@code atualizado_em}, no fuso do relogio da aplicacao.
 * - Listagens de materia e assunto so entram se tiverem questao publicada (a lista
 *   vazia recebe noindex, RF07 RN5).
 */
@Service
public class SitemapService {

    public static final int URLS_POR_ARQUIVO = 50_000;

    /** Paginas institucionais (RF18), sem data de alteracao no banco. */
    static final List<String> PAGINAS_FIXAS = List.of("/sobre", "/contato", "/privacidade", "/termos", "/cookies");

    private static final String CABECALHO = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";
    private static final String NAMESPACE = "http://www.sitemaps.org/schemas/sitemap/0.9";

    private final QuestaoRepository questaoRepository;
    private final MateriaRepository materiaRepository;
    private final AssuntoRepository assuntoRepository;
    private final SiteConfig site;
    private final Clock relogio;

    public SitemapService(QuestaoRepository questaoRepository, MateriaRepository materiaRepository,
            AssuntoRepository assuntoRepository, SiteConfig site, Clock relogio) {
        this.questaoRepository = questaoRepository;
        this.materiaRepository = materiaRepository;
        this.assuntoRepository = assuntoRepository;
        this.site = site;
        this.relogio = relogio;
    }

    /** {@code /sitemap.xml}: indice de sitemaps. */
    @Transactional(readOnly = true)
    public String indice() {
        StringBuilder xml = new StringBuilder(CABECALHO)
                .append("<sitemapindex xmlns=\"").append(NAMESPACE).append("\">\n");
        LocalDateTime ultimaGeral = null;
        StringBuilder blocos = new StringBuilder();
        for (Object[] linha : questaoRepository.blocosDoSitemap(URLS_POR_ARQUIVO)) {
            int bloco = ((Number) linha[0]).intValue();
            LocalDateTime ultima = paraLocalDateTime(linha[1]);
            ultimaGeral = maisRecente(ultimaGeral, ultima);
            item(blocos, "sitemap", "/sitemaps/questoes-" + bloco + ".xml", ultima);
        }
        item(xml, "sitemap", "/sitemaps/paginas.xml", ultimaGeral);
        xml.append(blocos);
        return xml.append("</sitemapindex>\n").toString();
    }

    /** {@code /sitemaps/paginas.xml}: inicio, listagens com questoes e institucionais. */
    @Transactional(readOnly = true)
    public String paginas() {
        List<Materia> materias = materiaRepository.findByAtivaTrueOrderByNomeAsc();
        Map<Integer, Materia> materiaPorId = materias.stream()
                .collect(Collectors.toMap(Materia::getId, Function.identity()));
        List<Assunto> assuntos = assuntoRepository.findAll();
        Map<Integer, LocalDateTime> porMateria = paraMapa(questaoRepository.ultimaAtualizacaoPorMateria());
        Map<Integer, LocalDateTime> porAssunto = somarSubassuntos(
                assuntos, paraMapa(questaoRepository.ultimaAtualizacaoPorAssunto()));
        LocalDateTime ultimaGeral = porMateria.values().stream().max(LocalDateTime::compareTo).orElse(null);

        StringBuilder xml = new StringBuilder(CABECALHO)
                .append("<urlset xmlns=\"").append(NAMESPACE).append("\">\n");
        item(xml, "url", "/", ultimaGeral);
        item(xml, "url", "/questoes", ultimaGeral);
        for (Materia materia : materias) {
            LocalDateTime ultima = porMateria.get(materia.getId());
            if (ultima != null) {
                item(xml, "url", "/questoes/" + materia.getSlug(), ultima);
            }
        }
        assuntos.stream()
                .filter(Assunto::isAtivo)
                .filter(a -> porAssunto.containsKey(a.getId()))
                .filter(a -> materiaPorId.containsKey(a.getMateria().getId()))
                .sorted((a, b) -> Integer.compare(a.getId(), b.getId()))
                .forEach(a -> item(xml, "url",
                        "/questoes/" + materiaPorId.get(a.getMateria().getId()).getSlug() + "/" + a.getSlug(),
                        porAssunto.get(a.getId())));
        for (String caminho : PAGINAS_FIXAS) {
            item(xml, "url", caminho, null);
        }
        return xml.append("</urlset>\n").toString();
    }

    /** {@code /sitemaps/questoes-{n}.xml}; vazio quando o bloco nao tem questao publicada. */
    @Transactional(readOnly = true)
    public Optional<String> questoes(int bloco) {
        if (bloco < 1) {
            return Optional.empty();
        }
        long inicio = (long) (bloco - 1) * URLS_POR_ARQUIVO;
        if (inicio >= Integer.MAX_VALUE) {
            return Optional.empty();
        }
        int ate = (int) Math.min((long) bloco * URLS_POR_ARQUIVO, Integer.MAX_VALUE);
        List<QuestaoSitemap> questoes = questaoRepository.publicadasParaSitemap((int) inicio, ate);
        if (questoes.isEmpty()) {
            return Optional.empty();
        }
        StringBuilder xml = new StringBuilder(CABECALHO)
                .append("<urlset xmlns=\"").append(NAMESPACE).append("\">\n");
        for (QuestaoSitemap q : questoes) {
            item(xml, "url", QuestaoPublicaService.urlCanonica(q.id(), q.slug(), null), q.atualizadoEm());
        }
        return Optional.of(xml.append("</urlset>\n").toString());
    }

    /** RF09 RN2: bloqueia so /admin e /api e informa o sitemap. */
    public String robots() {
        return "User-agent: *\n"
                + "Disallow: /admin\n"
                + "Disallow: /api\n"
                + "\n"
                + "Sitemap: " + site.getUrl() + "/sitemap.xml\n";
    }

    /**
     * A listagem do assunto inclui os subassuntos (RF07 RN1): a ultima alteracao de um
     * subassunto tambem conta para todos os assuntos acima dele.
     */
    static Map<Integer, LocalDateTime> somarSubassuntos(List<Assunto> assuntos, Map<Integer, LocalDateTime> diretos) {
        Map<Integer, Integer> paiPorId = new HashMap<>();
        for (Assunto a : assuntos) {
            if (a.getAssuntoPai() != null) {
                paiPorId.put(a.getId(), a.getAssuntoPai().getId());
            }
        }
        Map<Integer, LocalDateTime> total = new HashMap<>();
        diretos.forEach((id, em) -> {
            Integer atual = id;
            // O limite evita laco infinito se o banco tiver um ciclo (o painel impede, RF03).
            for (int nivel = 0; atual != null && nivel <= assuntos.size(); nivel++) {
                total.merge(atual, em, SitemapService::maisRecente);
                atual = paiPorId.get(atual);
            }
        });
        return total;
    }

    private void item(StringBuilder xml, String tag, String caminho, LocalDateTime lastmod) {
        xml.append("  <").append(tag).append("><loc>").append(escapar(site.getUrl() + caminho)).append("</loc>");
        if (lastmod != null) {
            xml.append("<lastmod>").append(formatar(lastmod)).append("</lastmod>");
        }
        xml.append("</").append(tag).append(">\n");
    }

    /** W3C Datetime com fuso, como pede o protocolo (ex.: 2026-10-22T17:54:32-03:00). */
    private String formatar(LocalDateTime data) {
        return data.truncatedTo(ChronoUnit.SECONDS).atZone(relogio.getZone()).toOffsetDateTime()
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private static Map<Integer, LocalDateTime> paraMapa(List<UltimaAtualizacao> linhas) {
        return linhas.stream().collect(Collectors.toMap(UltimaAtualizacao::id, UltimaAtualizacao::em));
    }

    private static LocalDateTime paraLocalDateTime(Object valor) {
        if (valor instanceof Timestamp t) {
            return t.toLocalDateTime();
        }
        return (LocalDateTime) valor;
    }

    private static LocalDateTime maisRecente(LocalDateTime a, LocalDateTime b) {
        if (a == null) {
            return b;
        }
        return b == null || a.isAfter(b) ? a : b;
    }

    static String escapar(String texto) {
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
