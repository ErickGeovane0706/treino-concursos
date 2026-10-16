package br.com.treinoconcursos.repository;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prova que o PROPRIO BANCO recusa dados invalidos (V2 e V3), mesmo que um
 * service erre ou alguem grave direto no banco (RF02-RF05).
 */
class RestricoesBancoTest extends IntegracaoTest {

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
    private Materia materia;

    @BeforeEach
    void dadosBase() {
        autor = criarUsuario("autor@exemplo.com", Perfil.EDITOR);
        materia = materiaRepository.save(materia("Português", "portugues"));
    }

    // ---------- questoes e alternativas (RF04, RF05) ----------

    @Test
    void aceitaQuestaoAutoralValidaComAlternativasEmOrdem() {
        Questao salva = questaoRepository.saveAndFlush(questaoAutoral(StatusQuestao.PUBLICADA, "Comentário."));

        Questao lida = questaoRepository.findById(salva.getId()).orElseThrow();
        assertThat(lida.getCriadoEm()).isNotNull();
        assertThat(lida.getAtualizadoEm()).isNotNull();
        assertThat(lida.getTotalRespostas()).isZero();
    }

    /** RF04 RN2: indice unico parcial uk_alternativas_correta. */
    @Test
    void recusaDuasAlternativasCorretas() {
        Questao questao = questaoAutoral(StatusQuestao.RASCUNHO, null);
        questao.getAlternativas().get(1).setCorreta(true);

        assertViolacao(() -> questaoRepository.saveAndFlush(questao), "uk_alternativas_correta");
    }

    /** RF04 RN3: origem PROVA exige prova. */
    @Test
    void recusaQuestaoDeProvaSemProva() {
        Questao questao = questaoAutoral(StatusQuestao.RASCUNHO, null);
        questao.setOrigem(OrigemQuestao.PROVA);

        assertViolacao(() -> questaoRepository.saveAndFlush(questao), "ck_questoes_origem_prova");
    }

    /** RF04 RN3: questao autoral nao tem prova. */
    @Test
    void recusaQuestaoAutoralComProva() {
        Questao questao = questaoAutoral(StatusQuestao.RASCUNHO, null);
        questao.setProva(novaProva(2025, "Analista"));

        assertViolacao(() -> questaoRepository.saveAndFlush(questao), "ck_questoes_origem_prova");
    }

    @Test
    void aceitaQuestaoDeProvaComProva() {
        Questao questao = questaoAutoral(StatusQuestao.RASCUNHO, null);
        questao.setOrigem(OrigemQuestao.PROVA);
        questao.setProva(novaProva(2025, "Analista"));

        assertThat(questaoRepository.saveAndFlush(questao).getId()).isNotNull();
    }

    /** RF04 RN1: comentario obrigatorio para publicar. */
    @Test
    void recusaQuestaoPublicadaSemComentario() {
        assertViolacao(() -> questaoRepository.saveAndFlush(questaoAutoral(StatusQuestao.PUBLICADA, null)),
                "ck_questoes_comentario_publicada");
    }

    @Test
    void recusaQuestaoPublicadaComComentarioEmBranco() {
        assertViolacao(() -> questaoRepository.saveAndFlush(questaoAutoral(StatusQuestao.PUBLICADA, "   ")),
                "ck_questoes_comentario_publicada");
    }

    /** RF04 RN1: rascunho pode ficar sem comentario. */
    @Test
    void aceitaRascunhoSemComentario() {
        assertThat(questaoRepository.saveAndFlush(questaoAutoral(StatusQuestao.RASCUNHO, null)).getId()).isNotNull();
    }

    @Test
    void recusaQuestaoPublicadaSemDataDePublicacao() {
        Questao questao = questaoAutoral(StatusQuestao.PUBLICADA, "Comentário.");
        questao.setPublicadoEm(null);

        assertViolacao(() -> questaoRepository.saveAndFlush(questao), "ck_questoes_publicado_em");
    }

    @Test
    void recusaLetraForaDeAaE() {
        Questao questao = questaoAutoral(StatusQuestao.RASCUNHO, null);
        questao.adicionarAlternativa(new Alternativa("F", "sexta", false));

        assertViolacao(() -> questaoRepository.saveAndFlush(questao), "ck_alternativas_letra");
    }

    @Test
    void recusaLetraRepetidaNaMesmaQuestao() {
        Questao questao = questaoAutoral(StatusQuestao.RASCUNHO, null);
        questao.adicionarAlternativa(new Alternativa("A", "de novo", false));

        assertViolacao(() -> questaoRepository.saveAndFlush(questao), "uk_alternativas_questao_letra");
    }

    @Test
    void recusaSlugForaDoPadrao() {
        Questao questao = questaoAutoral(StatusQuestao.RASCUNHO, null);
        questao.setSlug("Com Maiúscula");

        assertViolacao(() -> questaoRepository.saveAndFlush(questao), "ck_questoes_slug");
    }

    @Test
    void excluirQuestaoExcluiAsAlternativas() {
        Questao questao = questaoRepository.saveAndFlush(questaoAutoral(StatusQuestao.RASCUNHO, null));

        jdbc.update("DELETE FROM questoes WHERE id_questao = ?", questao.getId());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM alternativas", Integer.class)).isZero();
    }

    /** Contadores so mudam por UPDATE atomico: um save da entidade nao os sobrescreve. */
    @Test
    void salvarAQuestaoNaoSobrescreveOsContadores() {
        Questao questao = questaoRepository.saveAndFlush(questaoAutoral(StatusQuestao.RASCUNHO, null));
        jdbc.update("UPDATE questoes SET total_respostas = total_respostas + 3, total_acertos = total_acertos + 1 "
                + "WHERE id_questao = ?", questao.getId());

        questao.setEnunciado("Enunciado corrigido.");
        questaoRepository.saveAndFlush(questao);

        assertThat(jdbc.queryForObject("SELECT total_respostas FROM questoes WHERE id_questao = ?",
                Integer.class, questao.getId())).isEqualTo(3);
    }

    @Test
    void recusaMaisAcertosQueRespostas() {
        Questao questao = questaoRepository.saveAndFlush(questaoAutoral(StatusQuestao.RASCUNHO, null));

        assertViolacao(() -> jdbc.update("UPDATE questoes SET total_acertos = 1 WHERE id_questao = ?",
                questao.getId()), "ck_questoes_totais");
    }

    // ---------- classificacoes (RF02, RF03) ----------

    /** RF02 RN1 */
    @Test
    void recusaMateriaComSlugRepetido() {
        assertViolacao(() -> materiaRepository.saveAndFlush(materia("Língua Portuguesa", "portugues")),
                "uk_materias_slug");
    }

    /** RF02 RN3: materia com questao nao pode ser excluida. */
    @Test
    void recusaExcluirMateriaComQuestao() {
        questaoRepository.saveAndFlush(questaoAutoral(StatusQuestao.RASCUNHO, null));

        assertViolacao(() -> jdbc.update("DELETE FROM materias WHERE id_materia = ?", materia.getId()),
                "fk_questoes_materia");
    }

    /** uk_assuntos_materia_slug: o slug do assunto e unico dentro da materia. */
    @Test
    void slugDeAssuntoEhUnicoSoDentroDaMateria() {
        assuntoRepository.saveAndFlush(assunto(materia, "Crase", "crase"));
        Materia outra = materiaRepository.save(materia("Redação", "redacao"));

        assertThat(assuntoRepository.saveAndFlush(assunto(outra, "Crase", "crase")).getId()).isNotNull();
        assertViolacao(() -> assuntoRepository.saveAndFlush(assunto(materia, "Crase de novo", "crase")),
                "uk_assuntos_materia_slug");
    }

    /** RF03 RN1: fora da esfera federal, UF obrigatoria. */
    @Test
    void recusaOrgaoEstadualSemUf() {
        Orgao orgao = orgao("Tribunal de Contas do Estado de Goiás", "tce-go", EsferaOrgao.ESTADUAL, null);

        assertViolacao(() -> orgaoRepository.saveAndFlush(orgao), "ck_orgaos_uf");
    }

    @Test
    void aceitaOrgaoFederalSemUf() {
        Orgao orgao = orgao("Receita Federal", "receita-federal", EsferaOrgao.FEDERAL, null);

        assertThat(orgaoRepository.saveAndFlush(orgao).getId()).isNotNull();
    }

    /** RF03 RN2: banca + orgao + ano + cargo unicos. */
    @Test
    void recusaProvaRepetida() {
        Prova primeira = novaProva(2025, "Analista");
        Prova repetida = new Prova();
        repetida.setBanca(primeira.getBanca());
        repetida.setOrgao(primeira.getOrgao());
        repetida.setAno(2025);
        repetida.setCargo("Analista");
        repetida.setNivel(NivelEscolaridade.SUPERIOR);
        repetida.setSlug("outro-slug");

        assertViolacao(() -> provaRepository.saveAndFlush(repetida), "uk_provas_banca_orgao_ano_cargo");
    }

    @Test
    void recusaAnoDeProvaForaDoIntervalo() {
        Prova prova = novaProva(2025, "Analista");
        prova.setAno(1989);

        assertViolacao(() -> provaRepository.saveAndFlush(prova), "ck_provas_ano");
    }

    /** RF03 RN3: prova com questao nao pode ser excluida. */
    @Test
    void recusaExcluirProvaComQuestao() {
        Questao questao = questaoAutoral(StatusQuestao.RASCUNHO, null);
        questao.setOrigem(OrigemQuestao.PROVA);
        questao.setProva(novaProva(2025, "Analista"));
        questaoRepository.saveAndFlush(questao);

        assertViolacao(() -> jdbc.update("DELETE FROM provas"), "fk_questoes_prova");
    }

    // ---------- indices (doc/banco-de-dados.md, secao 3) ----------

    @Test
    void indicesParciaisDeNavegacaoExistem() {
        List<String> definicoes = jdbc.queryForList(
                "SELECT indexname || ' ' || indexdef FROM pg_indexes WHERE schemaname = 'public'", String.class);

        assertThat(definicoes)
                .anyMatch(d -> d.startsWith("uk_alternativas_correta ") && d.contains("UNIQUE") && d.contains("WHERE correta"))
                .anyMatch(d -> d.startsWith("ix_questoes_pub_materia ") && d.contains("'PUBLICADA'"))
                .anyMatch(d -> d.startsWith("ix_questoes_pub_assunto ") && d.contains("'PUBLICADA'"))
                .anyMatch(d -> d.startsWith("ix_questoes_pub_prova ") && d.contains("'PUBLICADA'"))
                .anyMatch(d -> d.startsWith("ix_questoes_pub_atualizado ") && d.contains("'PUBLICADA'"))
                .anyMatch(d -> d.startsWith("ix_assuntos_materia "))
                .anyMatch(d -> d.startsWith("ix_provas_banca "))
                .anyMatch(d -> d.startsWith("ix_provas_orgao "))
                .anyMatch(d -> d.startsWith("ix_alternativas_questao "));
    }

    // ---------- apoio ----------

    private static void assertViolacao(Runnable acao, String constraint) {
        assertThatThrownBy(acao::run)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(constraint);
    }

    private Questao questaoAutoral(StatusQuestao status, String comentario) {
        Questao questao = new Questao();
        questao.setMateria(materia);
        questao.setAutor(autor);
        questao.setTipo(TipoQuestao.MULTIPLA_ESCOLHA);
        questao.setOrigem(OrigemQuestao.AUTORAL);
        questao.setEnunciado("Assinale a alternativa correta.");
        questao.setComentario(comentario);
        questao.setSlug("assinale-a-alternativa-correta");
        questao.setStatus(status);
        if (status == StatusQuestao.PUBLICADA) {
            questao.setPublicadoEm(LocalDateTime.now(relogio));
        }
        questao.adicionarAlternativa(new Alternativa("A", "primeira", true));
        questao.adicionarAlternativa(new Alternativa("B", "segunda", false));
        questao.adicionarAlternativa(new Alternativa("C", "terceira", false));
        questao.adicionarAlternativa(new Alternativa("D", "quarta", false));
        return questao;
    }

    private Prova novaProva(int ano, String cargo) {
        Banca banca = new Banca();
        banca.setNome("Fundação Getulio Vargas");
        banca.setSigla("FGV");
        banca.setSlug("fgv");
        bancaRepository.save(banca);
        Orgao orgao = orgaoRepository.save(orgao("Tribunal de Contas do Estado de Goiás", "tce-go", EsferaOrgao.ESTADUAL, "GO"));

        Prova prova = new Prova();
        prova.setBanca(banca);
        prova.setOrgao(orgao);
        prova.setAno(ano);
        prova.setCargo(cargo);
        prova.setNivel(NivelEscolaridade.SUPERIOR);
        prova.setSlug("fgv-tce-go-" + ano + "-analista");
        return provaRepository.saveAndFlush(prova);
    }

    private static Materia materia(String nome, String slug) {
        Materia materia = new Materia();
        materia.setNome(nome);
        materia.setSlug(slug);
        return materia;
    }

    private static Assunto assunto(Materia materia, String nome, String slug) {
        Assunto assunto = new Assunto();
        assunto.setMateria(materia);
        assunto.setNome(nome);
        assunto.setSlug(slug);
        return assunto;
    }

    private static Orgao orgao(String nome, String slug, EsferaOrgao esfera, String uf) {
        Orgao orgao = new Orgao();
        orgao.setNome(nome);
        orgao.setSigla(slug.toUpperCase());
        orgao.setSlug(slug);
        orgao.setEsfera(esfera);
        orgao.setUf(uf);
        return orgao;
    }
}
