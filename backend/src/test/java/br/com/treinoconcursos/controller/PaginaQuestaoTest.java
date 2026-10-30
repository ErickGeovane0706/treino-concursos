package br.com.treinoconcursos.controller;

import br.com.treinoconcursos.IntegracaoTest;
import br.com.treinoconcursos.model.Alternativa;
import br.com.treinoconcursos.model.Assunto;
import br.com.treinoconcursos.model.Banca;
import br.com.treinoconcursos.model.EsferaOrgao;
import br.com.treinoconcursos.model.Materia;
import br.com.treinoconcursos.model.NivelEscolaridade;
import br.com.treinoconcursos.model.Orgao;
import br.com.treinoconcursos.model.OrigemQuestao;
import br.com.treinoconcursos.model.Perfil;
import br.com.treinoconcursos.model.Prova;
import br.com.treinoconcursos.model.Questao;
import br.com.treinoconcursos.model.StatusQuestao;
import br.com.treinoconcursos.model.TipoQuestao;
import br.com.treinoconcursos.model.Usuario;
import br.com.treinoconcursos.repository.AssuntoRepository;
import br.com.treinoconcursos.repository.BancaRepository;
import br.com.treinoconcursos.repository.MateriaRepository;
import br.com.treinoconcursos.repository.OrgaoRepository;
import br.com.treinoconcursos.repository.ProvaRepository;
import br.com.treinoconcursos.repository.QuestaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaginaQuestaoTest extends IntegracaoTest {

    @Autowired
    private MateriaRepository materiaRepository;

    @Autowired
    private AssuntoRepository assuntoRepository;

    @Autowired
    private BancaRepository bancaRepository;

    @Autowired
    private OrgaoRepository orgaoRepository;

    @Autowired
    private ProvaRepository provaRepository;

    @Autowired
    private QuestaoRepository questaoRepository;

    private Usuario autor;
    private Materia portugues;
    private Materia direito;
    private Assunto sintaxe;
    private Assunto concordancia;
    private Prova provaCespe;

    @BeforeEach
    void prepararDados() {
        autor = criarUsuario("editor@teste.com.br", Perfil.EDITOR);

        portugues = new Materia();
        portugues.setNome("Língua Portuguesa");
        portugues.setSlug("lingua-portuguesa");
        portugues = materiaRepository.save(portugues);

        direito = new Materia();
        direito.setNome("Direito Constitucional");
        direito.setSlug("direito-constitucional");
        direito = materiaRepository.save(direito);

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

        Banca banca = new Banca();
        banca.setNome("Centro Brasileiro de Pesquisa em Avaliação");
        banca.setSigla("CEBRASPE");
        banca.setSlug("cebraspe");
        banca = bancaRepository.save(banca);

        Orgao orgao = new Orgao();
        orgao.setNome("Polícia Rodoviária Federal");
        orgao.setSigla("PRF");
        orgao.setSlug("prf");
        orgao.setEsfera(EsferaOrgao.FEDERAL);
        orgao = orgaoRepository.save(orgao);

        provaCespe = new Prova();
        provaCespe.setBanca(banca);
        provaCespe.setOrgao(orgao);
        provaCespe.setAno(2024);
        provaCespe.setCargo("Policial Rodoviário Federal");
        provaCespe.setNivel(NivelEscolaridade.SUPERIOR);
        provaCespe.setSlug("cebraspe-prf-2024-policial-rodoviario-federal");
        provaCespe = provaRepository.save(provaCespe);
    }

    @Test
    void renderizaHtmlCompletoComEnunciadoAlternativasDetailsMetaEFonteDeProva() throws Exception {
        Questao q = novaQuestaoPublicada(
                portugues,
                concordancia,
                provaCespe,
                "Assinale a opção em que a **concordância verbal** respeita a norma culta da língua portuguesa em textos oficiais.",
                "slug-concordancia-norma-culta",
                "A alternativa **B** está correta porque o verbo concorda com o núcleo do sujeito.");

        MvcResult resultado = mockMvc.perform(get("/questao/" + q.getId() + "/" + q.getSlug()))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist("XSRF-TOKEN"))
                .andExpect(content().string(containsString("Língua Portuguesa")))
                .andExpect(content().string(containsString("Concordância verbal")))
                .andExpect(content().string(containsString("CEBRASPE · PRF · 2024 · Policial Rodoviário Federal")))
                .andExpect(content().string(containsString("<strong>concordância verbal</strong>")))
                .andExpect(content().string(containsString("data-ilha=\"treino\"")))
                .andExpect(content().string(containsString("<summary>Ver resposta</summary>")))
                .andExpect(content().string(containsString("Gabarito: B")))
                .andExpect(content().string(containsString("concorda com o núcleo do sujeito")))
                .andExpect(content().string(containsString("data-espaco-anuncio")))
                .andExpect(content().string(containsString("data-ilha=\"reporte\"")))
                .andExpect(content().string(containsString("Reportar erro")))
                .andExpect(content().string(containsString("/questoes/lingua-portuguesa/concordancia-verbal")))
                .andExpect(content().string(not(containsString("<form"))))
                .andReturn();

        assertThat(resultado.getRequest().getSession(false)).isNull();

        String html = resultado.getResponse().getContentAsString();
        Matcher matcherTitulo = Pattern.compile("<title>(.*?)</title>").matcher(html);
        assertThat(matcherTitulo.find()).isTrue();
        assertThat(matcherTitulo.group(1).length()).isLessThanOrEqualTo(60);
        assertThat(html).contains("<meta name=\"description\"");
    }

    @Test
    void exibeQuestaoAutoralEAvisosDeAnuladaEDesatualizada() throws Exception {
        Questao q = novaQuestaoPublicada(
                direito,
                null,
                null,
                "Julgue o item sobre direitos e garantias fundamentais.",
                "direitos-garantias-fundamentais",
                "Comentário da questão autoral.");
        q.setAnulada(true);
        q.setDesatualizada(true);
        questaoRepository.save(q);

        mockMvc.perform(get("/questao/" + q.getId() + "/" + q.getSlug()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Questão autoral")))
                .andExpect(content().string(containsString("Questão anulada pela banca")))
                .andExpect(content().string(containsString("Questão desatualizada")));
    }

    @Test
    void redirecionaCom301QuandoSlugEstiverDiferenteOuOmitidoPreservandoFiltro() throws Exception {
        Questao q = novaQuestaoPublicada(
                portugues,
                sintaxe,
                null,
                "Enunciado de sintaxe.",
                "enunciado-de-sintaxe",
                "Comentário de sintaxe.");

        mockMvc.perform(get("/questao/" + q.getId() + "/slug-antigo"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string(HttpHeaders.LOCATION, "/questao/" + q.getId() + "/enunciado-de-sintaxe"));

        mockMvc.perform(get("/questao/" + q.getId()).param("de", "lingua-portuguesa/sintaxe"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string(
                        HttpHeaders.LOCATION,
                        "/questao/" + q.getId() + "/enunciado-de-sintaxe?de=lingua-portuguesa/sintaxe"));

        mockMvc.perform(get("/questao/" + q.getId()).param("de", "caderno"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string(
                        HttpHeaders.LOCATION,
                        "/questao/" + q.getId() + "/enunciado-de-sintaxe?de=caderno"));

        mockMvc.perform(get("/questao/" + q.getId() + "/enunciado-de-sintaxe").param("de", "caderno"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-filtro=\"caderno\"")))
                .andExpect(content().string(containsString("data-listagem-url=\"/caderno-de-erros\"")))
                .andExpect(content().string(containsString("href=\"/caderno-de-erros\"")));
    }

    @Test
    void retorna410EmQuestaoArquivadaE404EmRascunhoOuInexistente() throws Exception {
        Questao arquivada = novaQuestaoPublicada(
                portugues, sintaxe, null, "Questão arquivada", "questao-arquivada", "Comentário");
        arquivada.setStatus(StatusQuestao.ARQUIVADA);
        questaoRepository.save(arquivada);

        Questao rascunho = new Questao();
        rascunho.setMateria(portugues);
        rascunho.setAssunto(sintaxe);
        rascunho.setAutor(autor);
        rascunho.setTipo(TipoQuestao.MULTIPLA_ESCOLHA);
        rascunho.setOrigem(OrigemQuestao.AUTORAL);
        rascunho.setEnunciado("Questão rascunho");
        rascunho.setSlug("questao-rascunho");
        rascunho.setStatus(StatusQuestao.RASCUNHO);
        questaoRepository.save(rascunho);

        mockMvc.perform(get("/questao/" + arquivada.getId() + "/" + arquivada.getSlug()))
                .andExpect(status().isGone());

        mockMvc.perform(get("/questao/" + rascunho.getId() + "/" + rascunho.getSlug()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/questao/99999/nao-existe"))
                .andExpect(status().isNotFound());
    }

    @Test
    void navegaEmOrdemCrescenteDeIdRespeitandoFiltroPorMateriaOuAssuntoComSubassuntos() throws Exception {
        Questao q1PortuguesSintaxe = novaQuestaoPublicada(
                portugues, sintaxe, null, "Questão 1 de sintaxe", "questao-1-sintaxe", "Comentário 1");
        Questao q2Direito = novaQuestaoPublicada(
                direito, null, null, "Questão 2 de direito", "questao-2-direito", "Comentário 2");
        Questao q3SubassuntoConcordancia = novaQuestaoPublicada(
                portugues, concordancia, null, "Questão 3 de concordância", "questao-3-concordancia", "Comentário 3");

        // Sem filtro: proxima de q1 e q2 (ordem global de id)
        mockMvc.perform(get("/questao/" + q1PortuguesSintaxe.getId() + "/" + q1PortuguesSintaxe.getSlug()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "href=\"/questao/" + q2Direito.getId() + "/" + q2Direito.getSlug() + "\"")));

        // Com filtro pelo assunto pai (sintaxe): inclui o subassunto concordancia-verbal e pula q2 (direito)
        mockMvc.perform(get("/questao/" + q1PortuguesSintaxe.getId() + "/" + q1PortuguesSintaxe.getSlug())
                        .param("de", "lingua-portuguesa/sintaxe"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "href=\"/questao/" + q3SubassuntoConcordancia.getId() + "/"
                                + q3SubassuntoConcordancia.getSlug() + "?de=lingua-portuguesa/sintaxe\"")));

        // Na q3, a anterior dentro do filtro aponta de volta para q1 mantendo ?de=
        mockMvc.perform(get("/questao/" + q3SubassuntoConcordancia.getId() + "/" + q3SubassuntoConcordancia.getSlug())
                        .param("de", "lingua-portuguesa/sintaxe"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "href=\"/questao/" + q1PortuguesSintaxe.getId() + "/"
                                + q1PortuguesSintaxe.getSlug() + "?de=lingua-portuguesa/sintaxe\"")));
    }

    @Test
    void sanitizaScriptsNoEnunciadoAlternativasEComentario() throws Exception {
        Questao q = novaQuestaoPublicada(
                portugues,
                sintaxe,
                null,
                "Texto seguro <script>alert('xss')</script>",
                "texto-seguro",
                "Comentário <img src=x onerror=alert(1)> limpo");

        mockMvc.perform(get("/questao/" + q.getId() + "/" + q.getSlug()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Texto seguro")))
                .andExpect(content().string(not(containsString("<script>alert"))))
                .andExpect(content().string(not(containsString("<img"))));
    }

    private Questao novaQuestaoPublicada(Materia materia, Assunto assunto, Prova prova,
            String enunciado, String slug, String comentario) {
        Questao q = new Questao();
        q.setMateria(materia);
        q.setAssunto(assunto);
        q.setProva(prova);
        q.setAutor(autor);
        q.setTipo(TipoQuestao.MULTIPLA_ESCOLHA);
        q.setOrigem(prova != null ? OrigemQuestao.PROVA : OrigemQuestao.AUTORAL);
        q.setEnunciado(enunciado);
        q.setComentario(comentario);
        q.setSlug(slug);
        q.setStatus(StatusQuestao.PUBLICADA);
        q.setPublicadoEm(LocalDateTime.now(relogio));
        q.adicionarAlternativa(new Alternativa("A", "Alternativa A", false));
        q.adicionarAlternativa(new Alternativa("B", "Alternativa B", true));
        return questaoRepository.save(q);
    }
}
