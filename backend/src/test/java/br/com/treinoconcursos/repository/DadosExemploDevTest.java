package br.com.treinoconcursos.repository;

import br.com.treinoconcursos.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O seed do perfil dev (db/dev/R__dados_exemplo.sql) aplica sem erro, passa
 * por todas as restricoes do banco e nao duplica nada se rodar de novo.
 */
@SpringBootTest(properties = {"app.admin-inicial.email=", "app.admin-inicial.senha="})
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class DadosExemploDevTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void carregaAsOitoQuestoesDoPrototipoPublicadasEmOrdem() {
        assertThat(contar("SELECT count(*) FROM questoes WHERE status = 'PUBLICADA'")).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT enunciado FROM questoes ORDER BY id_questao LIMIT 1", String.class))
                .isEqualTo("Assinale a frase correta quanto à concordância verbal.");
    }

    @Test
    void cadaQuestaoTemCincoAlternativasEUmaCorreta() {
        assertThat(contar("""
                SELECT count(*) FROM questoes q
                WHERE (SELECT count(*) FROM alternativas a WHERE a.id_questao = q.id_questao) <> 5
                   OR (SELECT count(*) FROM alternativas a WHERE a.id_questao = q.id_questao AND a.correta) <> 1
                """)).isZero();
    }

    @Test
    void gabaritoConfereComOPrototipo() {
        assertThat(jdbc.queryForList("""
                SELECT a.letra FROM alternativas a JOIN questoes q ON q.id_questao = a.id_questao
                WHERE a.correta ORDER BY q.id_questao
                """, String.class)).containsExactly("C", "B", "D", "D", "D", "C", "B", "B");
    }

    @Test
    void autorDoSeedNaoConsegueEntrarEAdminInicialContinuaPossivel() {
        assertThat(contar("SELECT count(*) FROM usuarios WHERE perfil = 'ADMIN'")).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT ativo FROM usuarios WHERE email = 'exemplo@treinoconcursos.invalid'", Boolean.class)).isFalse();
    }

    @Test
    void rodarDeNovoNaoDuplica() throws Exception {
        String seed = new String(getClass().getResourceAsStream("/db/dev/R__dados_exemplo.sql").readAllBytes());
        jdbc.execute(seed);

        assertThat(contar("SELECT count(*) FROM questoes")).isEqualTo(8);
        assertThat(contar("SELECT count(*) FROM alternativas")).isEqualTo(40);
        assertThat(contar("SELECT count(*) FROM materias")).isEqualTo(5);
        assertThat(contar("SELECT count(*) FROM assuntos")).isEqualTo(9);
    }

    @Test
    void datasPadraoFicamNoHorarioDeBrasilia() {
        // A conexao usa America/Sao_Paulo (connection-init-sql), entao now() do
        // banco e o horario local de Brasilia, igual ao LocalDateTime da aplicacao.
        assertThat(jdbc.queryForObject("SHOW TIME ZONE", String.class)).isEqualTo("America/Sao_Paulo");
    }

    private int contar(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }
}
