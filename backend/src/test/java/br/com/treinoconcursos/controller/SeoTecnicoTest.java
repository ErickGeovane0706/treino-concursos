package br.com.treinoconcursos.controller;

import br.com.treinoconcursos.IntegracaoTest;
import br.com.treinoconcursos.model.Alternativa;
import br.com.treinoconcursos.model.Assunto;
import br.com.treinoconcursos.model.Materia;
import br.com.treinoconcursos.model.OrigemQuestao;
import br.com.treinoconcursos.model.Perfil;
import br.com.treinoconcursos.model.Questao;
import br.com.treinoconcursos.model.StatusQuestao;
import br.com.treinoconcursos.model.TipoQuestao;
import br.com.treinoconcursos.model.Usuario;
import br.com.treinoconcursos.repository.AssuntoRepository;
import br.com.treinoconcursos.repository.MateriaRepository;
import br.com.treinoconcursos.repository.QuestaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SEO tecnico: sitemap, robots.txt, canonical, noindex, dados estruturados e cache (RF09 RN1-RN4, US14). */
class SeoTecnicoTest extends IntegracaoTest {

    private static final String SITE = "http://localhost:8080";

    @Autowired
    private MateriaRepository materiaRepository;

    @Autowired
    private AssuntoRepository assuntoRepository;

    @Autowired
    private QuestaoRepository questaoRepository;

    private Usuario autor;
    private Materia portugues;
    private Materia semQuestoes;
    private Assunto sintaxe;
    private Assunto concordancia;

    @BeforeEach
    void prepararDados() {
        autor = criarUsuario("editor@teste.com.br", Perfil.EDITOR);
        portugues = novaMateria("Português", "portugues", true);
        semQuestoes = novaMateria("Matemática", "matematica", true);

        sintaxe = new Assunto();
        sintaxe.setMateria(portugues);
        sintaxe.setNome("Sintaxe");
        sintaxe.setSlug("sintaxe");
        sintaxe = assuntoRepository.save(sintaxe);

        concordancia = new Assunto();
        concordancia.setMateria(portugues);
        concordancia.setAssuntoPai(sintaxe);
        concordancia.setNome("Concordância verbal");
        concordancia.setSlug("concordancia-verbal");
        concordancia = assuntoRepository.save(concordancia);
    }

    /** RF09 RN2: bloqueia so /admin e /api e aponta o sitemap. */
    @Test
    void robotsTxtBloqueiaSoAdminEApiEInformaOSitemap() throws Exception {
        mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("public")))
                .andExpect(content().string("User-agent: *\n"
                        + "Disallow: /admin\n"
                        + "Disallow: /api\n"
                        + "\n"
                        + "Sitemap: " + SITE + "/sitemap.xml\n"));
    }

    /** RF09 RN1 e RF05 RN1/RN3: so PUBLICADAS, lastmod = atualizado_em, blocos de 50.000 ids. */
    @Test
    void sitemapListaSoPublicadasComLastmodEmBlocosDe50MilIds() throws Exception {
        Questao publicada = novaQuestao(concordancia, StatusQuestao.PUBLICADA);
        Questao rascunho = novaQuestao(sintaxe, StatusQuestao.RASCUNHO);
        Questao arquivada = novaQuestao(sintaxe, StatusQuestao.ARQUIVADA);
        jdbc.execute("ALTER TABLE questoes ALTER COLUMN id_questao RESTART WITH 50001");
        Questao segundoBloco = novaQuestao(sintaxe, StatusQuestao.PUBLICADA);
        assertThat(segundoBloco.getId()).isEqualTo(50_001);
        jdbc.update("UPDATE questoes SET atualizado_em = ? WHERE id_questao = ?",
                LocalDateTime.of(2026, 10, 1, 9, 30, 15), publicada.getId());

        String indice = xml("/sitemap.xml");
        assertThat(locs(indice, "sitemap")).containsExactly(
                SITE + "/sitemaps/paginas.xml",
                SITE + "/sitemaps/questoes-1.xml",
                SITE + "/sitemaps/questoes-2.xml");

        String bloco1 = xml("/sitemaps/questoes-1.xml");
        assertThat(locs(bloco1, "url")).containsExactly(
                SITE + "/questao/" + publicada.getId() + "/" + publicada.getSlug());
        assertThat(bloco1).contains("<lastmod>2026-10-01T09:30:15-03:00</lastmod>")
                .doesNotContain(rascunho.getSlug())
                .doesNotContain(arquivada.getSlug());

        assertThat(locs(xml("/sitemaps/questoes-2.xml"), "url")).containsExactly(
                SITE + "/questao/50001/" + segundoBloco.getSlug());

        mockMvc.perform(get("/sitemaps/questoes-3.xml")).andExpect(status().isNotFound());
        mockMvc.perform(get("/sitemaps/questoes-0.xml")).andExpect(status().isNotFound());
    }

    /** Listagens so com questao publicada (as vazias tem noindex); o subassunto conta para o pai. */
    @Test
    void sitemapDePaginasTemListagensComQuestoesEInstitucionais() throws Exception {
        Materia inativa = novaMateria("Inativa", "inativa", false);
        Questao naInativa = novaQuestao(null, StatusQuestao.PUBLICADA);
        naInativa.setMateria(inativa);
        questaoRepository.save(naInativa);
        novaQuestao(concordancia, StatusQuestao.PUBLICADA);

        String paginas = xml("/sitemaps/paginas.xml");

        assertThat(locs(paginas, "url")).containsExactly(
                SITE + "/",
                SITE + "/questoes",
                SITE + "/questoes/portugues",
                SITE + "/questoes/portugues/sintaxe",
                SITE + "/questoes/portugues/concordancia-verbal",
                SITE + "/sobre",
                SITE + "/contato",
                SITE + "/privacidade",
                SITE + "/termos",
                SITE + "/cookies");
        assertThat(paginas).doesNotContain(semQuestoes.getSlug()).doesNotContain("/questoes/inativa");
    }

    /** RF09 RN3: canonica sem ?de=, mas com o cursor da listagem; Open Graph basico. */
    @Test
    void paginasTemLinkCanonicoEOpenGraph() throws Exception {
        Questao q = novaQuestao(sintaxe, StatusQuestao.PUBLICADA);
        String canonica = SITE + "/questao/" + q.getId() + "/" + q.getSlug();

        mockMvc.perform(get("/questao/" + q.getId() + "/" + q.getSlug()).param("de", "portugues"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("<link rel=\"canonical\" href=\"" + canonica + "\">"),
                        containsString("<meta property=\"og:url\" content=\"" + canonica + "\">"),
                        containsString("<meta property=\"og:site_name\" content=\"Treino de Concursos\">"),
                        containsString("<meta property=\"og:description\""),
                        not(containsString("noindex")))));

        mockMvc.perform(get("/questoes/portugues").param("depoisDe", "7").param("utm_source", "x"))
                .andExpect(content().string(containsString(
                        "<link rel=\"canonical\" href=\"" + SITE + "/questoes/portugues?depoisDe=7\">")));

        mockMvc.perform(get("/sobre"))
                .andExpect(content().string(containsString("<link rel=\"canonical\" href=\"" + SITE + "/sobre\">")));
    }

    /** RF09 RN3 e doc/api.md secao 2: noindex so onde a tabela manda (e na lista vazia). */
    @Test
    void noindexNasRotasCertas() throws Exception {
        novaQuestao(sintaxe, StatusQuestao.PUBLICADA);

        mockMvc.perform(get("/meu-progresso"))
                .andExpect(content().string(containsString("<meta name=\"robots\" content=\"noindex\">")));
        mockMvc.perform(get("/questoes/matematica"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<meta name=\"robots\" content=\"noindex\">")));
        for (String indexavel : List.of("/", "/questoes", "/questoes/portugues", "/questoes/portugues/sintaxe", "/sobre")) {
            mockMvc.perform(get(indexavel))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("noindex"))));
        }
    }

    /** RF09 RN4: BreadcrumbList na questao e na listagem, WebSite no inicio, em JSON-LD escapado. */
    @Test
    void dadosEstruturadosEmJsonLd() throws Exception {
        Questao q = novaQuestao(concordancia, StatusQuestao.PUBLICADA);
        portugues.setNome("Português </script><script>alert(1)</script>");
        materiaRepository.save(portugues);

        String questao = mockMvc.perform(get("/questao/" + q.getId() + "/" + q.getSlug()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String jsonLd = jsonLd(questao);
        assertThat(jsonLd).contains("\"@type\":\"BreadcrumbList\"")
                .contains("\"position\":4")
                // o serializador do Thymeleaf escapa acentos (ã), o que continua JSON valido
                .contains("Quest\\u00E3o #" + q.getId())
                .contains("localhost:8080\\/questoes\\/portugues\\/concordancia-verbal")
                // "</" sai como "<\/": o nome nao fecha o script nem abre outro
                .doesNotContain("</");
        String foraDoJsonLd = questao.replace(jsonLd, "");
        assertThat(foraDoJsonLd).doesNotContain("<script>alert(1)");

        mockMvc.perform(get("/questoes/portugues/sintaxe"))
                .andExpect(content().string(containsString("\"@type\":\"BreadcrumbList\"")));
        mockMvc.perform(get("/questoes"))
                .andExpect(content().string(not(containsString("application/ld+json"))));
        mockMvc.perform(get("/"))
                .andExpect(content().string(allOf(
                        containsString("\"@type\":\"WebSite\""),
                        containsString("\"name\":\"Treino de Concursos\""))));
    }

    /** Cache para a Cloudflare: pagina publica curta no navegador e maior no CDN; API e erro sem cache. */
    @Test
    void cacheControlPorTipoDeResposta() throws Exception {
        Questao q = novaQuestao(sintaxe, StatusQuestao.PUBLICADA);

        for (String pagina : List.of("/", "/questoes/portugues", "/questao/" + q.getId() + "/" + q.getSlug())) {
            mockMvc.perform(get(pagina))
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, allOf(
                            containsString("max-age=300"), containsString("s-maxage=3600"), containsString("public"))))
                    .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        }
        mockMvc.perform(get("/api/questoes/ids"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
        mockMvc.perform(get("/questao/99999/nao-existe"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, not(containsString("public"))));
        mockMvc.perform(get("/questao/" + q.getId()))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, not(containsString("public"))));
    }

    /** CSS e fonte com hash do conteudo na URL e cache imutavel. */
    @Test
    void cssEFonteTemUrlComHashECacheImutavel() throws Exception {
        String html = mockMvc.perform(get("/sobre")).andReturn().getResponse().getContentAsString();
        Matcher css = Pattern.compile("href=\"(/css/site-[0-9a-f]{32}\\.css)\"").matcher(html);
        assertThat(css.find()).as("link do CSS com hash").isTrue();
        Matcher fonte = Pattern.compile("href=\"(/fontes/public-sans-latin-[0-9a-f]{32}\\.woff2)\"").matcher(html);
        assertThat(fonte.find()).as("preload da fonte com hash").isTrue();

        mockMvc.perform(get(css.group(1)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, allOf(
                        containsString("max-age=31536000"), containsString("immutable"))))
                // o url() da fonte dentro do CSS aponta para a mesma URL do preload
                .andExpect(content().string(containsString(fonte.group(1))));
        mockMvc.perform(get(fonte.group(1)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("immutable")));
    }

    /** doc/api.md secao 2: maiusculas e barra final viram 301 para a URL canonica. */
    @Test
    void maiusculasEBarraFinalRedirecionamCom301() throws Exception {
        mockMvc.perform(get("/Questoes/Portugues/?depoisDe=3"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string(HttpHeaders.LOCATION, "/questoes/portugues?depoisDe=3"));
        mockMvc.perform(get("/sobre/"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string(HttpHeaders.LOCATION, "/sobre"));
        mockMvc.perform(get("/sobre")).andExpect(status().isOk());
    }

    private String xml(String caminho) throws Exception {
        String corpo = mockMvc.perform(get(caminho))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("public")))
                .andExpect(content().string(matchesPattern("(?s)^<\\?xml version=\"1.0\" encoding=\"UTF-8\"\\?>.*")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        // XML bem formado, no namespace do protocolo
        Document doc = DocumentBuilderFactory.newDefaultNSInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(corpo.getBytes(StandardCharsets.UTF_8)));
        assertThat(doc.getDocumentElement().getNamespaceURI()).isEqualTo("http://www.sitemaps.org/schemas/sitemap/0.9");
        return corpo;
    }

    private static List<String> locs(String xml, String elemento) throws Exception {
        Document doc = DocumentBuilderFactory.newDefaultNSInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        NodeList itens = doc.getElementsByTagNameNS("*", elemento);
        List<String> locs = new ArrayList<>();
        for (int i = 0; i < itens.getLength(); i++) {
            locs.add(((org.w3c.dom.Element) itens.item(i)).getElementsByTagNameNS("*", "loc").item(0).getTextContent());
        }
        return locs;
    }

    private static String jsonLd(String html) {
        Matcher m = Pattern.compile("(?s)<script type=\"application/ld\\+json\">(.*?)</script>").matcher(html);
        assertThat(m.find()).as("script JSON-LD").isTrue();
        return m.group(1);
    }

    private Materia novaMateria(String nome, String slug, boolean ativa) {
        Materia m = new Materia();
        m.setNome(nome);
        m.setSlug(slug);
        m.setAtiva(ativa);
        return materiaRepository.save(m);
    }

    private Questao novaQuestao(Assunto assunto, StatusQuestao status) {
        Questao q = new Questao();
        q.setMateria(portugues);
        q.setAssunto(assunto);
        q.setAutor(autor);
        q.setTipo(TipoQuestao.MULTIPLA_ESCOLHA);
        q.setOrigem(OrigemQuestao.AUTORAL);
        q.setEnunciado("Assinale a alternativa correta sobre o tema.");
        q.setSlug("questao-" + System.nanoTime());
        q.setStatus(status);
        if (status != StatusQuestao.RASCUNHO) {
            q.setComentario("Comentário da questão.");
            q.setPublicadoEm(LocalDateTime.now(relogio));
        }
        q.adicionarAlternativa(new Alternativa("A", "Alternativa A", true));
        q.adicionarAlternativa(new Alternativa("B", "Alternativa B", false));
        return questaoRepository.save(q);
    }
}
