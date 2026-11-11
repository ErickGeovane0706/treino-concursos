package br.com.treinoconcursos.repository;

import br.com.treinoconcursos.IntegracaoTest;
import br.com.treinoconcursos.model.Assunto;
import br.com.treinoconcursos.model.Banca;
import br.com.treinoconcursos.model.EsferaOrgao;
import br.com.treinoconcursos.model.Materia;
import br.com.treinoconcursos.model.NivelEscolaridade;
import br.com.treinoconcursos.model.Orgao;
import br.com.treinoconcursos.model.Perfil;
import br.com.treinoconcursos.model.Prova;
import br.com.treinoconcursos.model.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprova com 50.000 questoes que as consultas de listagem e navegacao por cursor
 * usam os indices parciais {@code ix_questoes_pub_materia}, {@code ix_questoes_pub_assunto}
 * e {@code ix_questoes_pub_prova} sem varredura sequencial (Seq Scan) na tabela
 * {@code questoes} (RF07, RNF03).
 */
class IndiceNavegacaoExplainTest extends IntegracaoTest {

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

    @Test
    void explainAnalyzeCom50MilQuestoesUsaIndicesParciaisSemSeqScan() {
        Usuario autor = criarUsuario("autor-perf@teste.com.br", Perfil.EDITOR);

        List<Materia> materias = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            Materia m = new Materia();
            m.setNome("Matéria " + i);
            m.setSlug("materia-" + i);
            materias.add(materiaRepository.save(m));
        }
        Materia materiaRecorte = materias.get(0);
        int idBaseMateria = materias.get(1).getId();

        List<Assunto> assuntos = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            Assunto a = new Assunto();
            a.setMateria(materias.get(1 + ((i - 1) % 24)));
            a.setNome("Assunto " + i);
            a.setSlug("assunto-" + i);
            assuntos.add(assuntoRepository.save(a));
        }

        Assunto sintaxe = new Assunto();
        sintaxe.setMateria(materiaRecorte);
        sintaxe.setNome("Sintaxe");
        sintaxe.setSlug("sintaxe");
        sintaxe = assuntoRepository.save(sintaxe);

        Assunto concordancia = new Assunto();
        concordancia.setMateria(materiaRecorte);
        concordancia.setAssuntoPai(sintaxe);
        concordancia.setNome("Concordância verbal");
        concordancia.setSlug("concordancia-verbal");
        concordancia = assuntoRepository.save(concordancia);

        int idBaseAssunto = assuntos.get(0).getId();

        Banca bancaAlvo = new Banca();
        bancaAlvo.setNome("Fundação Getulio Vargas");
        bancaAlvo.setSigla("FGV");
        bancaAlvo.setSlug("fgv");
        bancaAlvo = bancaRepository.save(bancaAlvo);

        Banca outraBanca = new Banca();
        outraBanca.setNome("Cebraspe");
        outraBanca.setSigla("CEBRASPE");
        outraBanca.setSlug("cebraspe");
        outraBanca = bancaRepository.save(outraBanca);

        Orgao orgaoAlvo = new Orgao();
        orgaoAlvo.setNome("Tribunal de Contas");
        orgaoAlvo.setSigla("TCE");
        orgaoAlvo.setSlug("tce");
        orgaoAlvo.setEsfera(EsferaOrgao.FEDERAL);
        orgaoAlvo = orgaoRepository.save(orgaoAlvo);

        List<Prova> provas = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            Prova p = new Prova();
            p.setBanca(i <= 2 ? bancaAlvo : outraBanca);
            p.setOrgao(orgaoAlvo);
            p.setAno(2020 + (i % 6));
            p.setCargo("Cargo " + i);
            p.setNivel(i % 2 == 0 ? NivelEscolaridade.SUPERIOR : NivelEscolaridade.MEDIO);
            p.setSlug("prova-" + i);
            provas.add(provaRepository.save(p));
        }
        Prova prova1 = provas.get(0);
        Prova prova2 = provas.get(1);
        int idBaseProva = provas.get(2).getId();

        // Insere 50.000 questoes e roda VACUUM ANALYZE para atualizar visibility map e estatisticas.
        jdbc.update("""
                INSERT INTO questoes (
                    id_materia, id_assunto, id_prova, id_autor, tipo, origem,
                    enunciado, comentario, slug, status, publicado_em
                )
                SELECT
                    CASE
                        WHEN g <= 25000 AND g % 250 = 0 THEN ?
                        ELSE ? + (g % 24)
                    END,
                    CASE
                        WHEN g <= 25000 AND g % 500 = 0 THEN ?
                        WHEN g <= 25000 AND g % 250 = 0 THEN ?
                        ELSE ? + (g % 100)
                    END,
                    CASE
                        WHEN g <= 25000 AND g % 500 = 0 THEN ?
                        WHEN g <= 25000 AND g % 250 = 0 THEN ?
                        ELSE ? + (g % 48)
                    END,
                    ?,
                    'MULTIPLA_ESCOLHA',
                    'PROVA',
                    'Enunciado de teste de índice #' || g,
                    CASE WHEN g % 7 = 0 THEN NULL ELSE 'Comentário #' || g END,
                    'questao-' || g,
                    CASE
                        WHEN g % 7 = 0 THEN 'RASCUNHO'
                        WHEN g % 13 = 0 THEN 'ARQUIVADA'
                        ELSE 'PUBLICADA'
                    END,
                    CASE WHEN g % 7 = 0 THEN NULL ELSE now() END
                FROM generate_series(1, 50000) AS g
                """,
                materiaRecorte.getId(),
                idBaseMateria,
                concordancia.getId(),
                sintaxe.getId(),
                idBaseAssunto,
                prova1.getId(),
                prova2.getId(),
                idBaseProva,
                autor.getId());

        jdbc.execute("VACUUM ANALYZE questoes");

        String planoPorMateria = explainAnalyze("""
                SELECT id_questao, slug, enunciado
                FROM questoes
                WHERE status = 'PUBLICADA'
                  AND id_materia = ?
                  AND id_questao > ?
                ORDER BY id_questao
                LIMIT 20
                """, materiaRecorte.getId(), 5000);

        String planoPorAssunto = explainAnalyze("""
                SELECT id_questao, slug, enunciado
                FROM questoes
                WHERE status = 'PUBLICADA'
                  AND id_assunto = ?
                  AND id_questao > ?
                ORDER BY id_questao
                LIMIT 20
                """, sintaxe.getId(), 5000);

        String planoPorAssuntoESubassuntos = explainAnalyze("""
                SELECT id_questao, slug, enunciado
                FROM questoes
                WHERE status = 'PUBLICADA'
                  AND id_assunto IN (?, ?)
                  AND id_questao > ?
                ORDER BY id_questao
                LIMIT 20
                """, sintaxe.getId(), concordancia.getId(), 0);

        String planoProximaQuestaoMateria = explainAnalyze("""
                SELECT id_questao, slug
                FROM questoes
                WHERE status = 'PUBLICADA'
                  AND id_materia = ?
                  AND id_questao > ?
                ORDER BY id_questao
                LIMIT 1
                """, materiaRecorte.getId(), 10000);

        String planoProximaQuestaoAssunto = explainAnalyze("""
                SELECT id_questao, slug
                FROM questoes
                WHERE status = 'PUBLICADA'
                  AND id_assunto = ?
                  AND id_questao > ?
                ORDER BY id_questao
                LIMIT 1
                """, sintaxe.getId(), 10000);

        String planoIdsPorMateria = explainAnalyze("""
                SELECT id_questao
                FROM questoes
                WHERE status = 'PUBLICADA'
                  AND id_materia = ?
                  AND id_questao > ?
                ORDER BY id_questao
                LIMIT 500
                """, materiaRecorte.getId(), 0);

        String planoIdsPorAssuntoESubassuntos = explainAnalyze("""
                SELECT id_questao
                FROM questoes
                WHERE status = 'PUBLICADA'
                  AND id_assunto IN (?, ?)
                  AND id_questao > ?
                ORDER BY id_questao
                LIMIT 500
                """, sintaxe.getId(), concordancia.getId(), 0);

        String planoPorProva = explainAnalyze("""
                SELECT id_questao, slug, enunciado
                FROM questoes
                WHERE status = 'PUBLICADA'
                  AND id_prova = ?
                  AND id_questao > ?
                ORDER BY id_questao
                LIMIT 20
                """, prova1.getId(), 5000);

        String planoPorProvasDaBancaOuOrgaoOuAno = explainAnalyze("""
                SELECT id_questao, slug, enunciado
                FROM questoes
                WHERE status = 'PUBLICADA'
                  AND id_prova IN (?, ?)
                  AND id_questao > ?
                ORDER BY id_questao
                LIMIT 20
                """, prova1.getId(), prova2.getId(), 0);

        String planoProximaQuestaoProva = explainAnalyze("""
                SELECT id_questao, slug
                FROM questoes
                WHERE status = 'PUBLICADA'
                  AND id_prova = ?
                  AND id_questao > ?
                ORDER BY id_questao
                LIMIT 1
                """, prova1.getId(), 10000);

        String planoIdsPorProvas = explainAnalyze("""
                SELECT id_questao
                FROM questoes
                WHERE status = 'PUBLICADA'
                  AND id_prova IN (?, ?)
                  AND id_questao > ?
                ORDER BY id_questao
                LIMIT 500
                """, prova1.getId(), prova2.getId(), 0);

        System.out.println("=== EXPLAIN ANALYZE: Listagem por matéria ===");
        System.out.println(planoPorMateria);
        System.out.println("=== EXPLAIN ANALYZE: Listagem por assunto ===");
        System.out.println(planoPorAssunto);
        System.out.println("=== EXPLAIN ANALYZE: Listagem por assunto + subassuntos ===");
        System.out.println(planoPorAssuntoESubassuntos);
        System.out.println("=== EXPLAIN ANALYZE: Listagem por prova ===");
        System.out.println(planoPorProva);
        System.out.println("=== EXPLAIN ANALYZE: Listagem por provas (banca/órgão/ano/nível) ===");
        System.out.println(planoPorProvasDaBancaOuOrgaoOuAno);
        System.out.println("=== EXPLAIN ANALYZE: Próxima questão por matéria (LIMIT 1) ===");
        System.out.println(planoProximaQuestaoMateria);
        System.out.println("=== EXPLAIN ANALYZE: Próxima questão por assunto (LIMIT 1) ===");
        System.out.println(planoProximaQuestaoAssunto);
        System.out.println("=== EXPLAIN ANALYZE: Próxima questão por prova (LIMIT 1) ===");
        System.out.println(planoProximaQuestaoProva);
        System.out.println("=== EXPLAIN ANALYZE: GET /api/questoes/ids por matéria ===");
        System.out.println(planoIdsPorMateria);
        System.out.println("=== EXPLAIN ANALYZE: GET /api/questoes/ids por assunto + subassuntos ===");
        System.out.println(planoIdsPorAssuntoESubassuntos);
        System.out.println("=== EXPLAIN ANALYZE: GET /api/questoes/ids por provas ===");
        System.out.println(planoIdsPorProvas);

        assertThat(planoPorMateria)
                .contains("ix_questoes_pub_materia")
                .doesNotContain("Seq Scan");

        assertThat(planoPorAssunto)
                .contains("ix_questoes_pub_assunto")
                .doesNotContain("Seq Scan");

        assertThat(planoPorAssuntoESubassuntos)
                .contains("ix_questoes_pub_assunto")
                .doesNotContain("Seq Scan");

        assertThat(planoPorProva)
                .contains("ix_questoes_pub_prova")
                .doesNotContain("Seq Scan");

        assertThat(planoPorProvasDaBancaOuOrgaoOuAno)
                .contains("ix_questoes_pub_prova")
                .doesNotContain("Seq Scan");

        assertThat(planoProximaQuestaoMateria)
                .contains("ix_questoes_pub_materia")
                .doesNotContain("Seq Scan");

        assertThat(planoProximaQuestaoAssunto)
                .contains("ix_questoes_pub_assunto")
                .doesNotContain("Seq Scan");

        assertThat(planoProximaQuestaoProva)
                .contains("ix_questoes_pub_prova")
                .doesNotContain("Seq Scan");

        assertThat(planoIdsPorMateria)
                .contains("ix_questoes_pub_materia")
                .doesNotContain("Seq Scan");

        assertThat(planoIdsPorAssuntoESubassuntos)
                .contains("ix_questoes_pub_assunto")
                .doesNotContain("Seq Scan");

        assertThat(planoIdsPorProvas)
                .contains("ix_questoes_pub_prova")
                .doesNotContain("Seq Scan");
    }

    private String explainAnalyze(String sql, Object... args) {
        List<String> linhas = jdbc.queryForList("EXPLAIN ANALYZE " + sql, String.class, args);
        return String.join("\n", linhas);
    }
}
