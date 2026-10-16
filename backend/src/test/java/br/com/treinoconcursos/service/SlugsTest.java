package br.com.treinoconcursos.service;

import br.com.treinoconcursos.exception.RegraNegocioException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RF02 RN4: minusculas, sem acentos, palavras separadas por hifen. */
class SlugsTest {

    @Test
    void tiraAcentosEUsaHifen() {
        assertThat(Slugs.gerar("Direito Constitucional", 120)).isEqualTo("direito-constitucional");
        assertThat(Slugs.gerar("Raciocínio Lógico", 120)).isEqualTo("raciocinio-logico");
        assertThat(Slugs.gerar("Ação, Coerção & Função", 120)).isEqualTo("acao-coercao-funcao");
    }

    @Test
    void juntaSeparadoresEApagaHifensDasPontas() {
        assertThat(Slugs.gerar("  --TCE / GO--  ", 120)).isEqualTo("tce-go");
    }

    @Test
    void cortaNoLimiteSemDeixarHifenNoFim() {
        assertThat(Slugs.gerar("abc def ghi", 4)).isEqualTo("abc");
        assertThat(Slugs.gerar("abcdefghij", 5)).isEqualTo("abcde");
    }

    @Test
    void textoSemLetrasNemNumerosViraVazio() {
        assertThat(Slugs.gerar("!!! ---", 120)).isEmpty();
        assertThat(Slugs.gerar(null, 120)).isEmpty();
    }

    @Test
    void informadoTemPrioridadeEVazioUsaAOrigem() {
        assertThat(Slugs.informadoOuGerado("Meu Slug", "Origem", 120)).isEqualTo("meu-slug");
        assertThat(Slugs.informadoOuGerado("  ", "Língua Portuguesa", 120)).isEqualTo("lingua-portuguesa");
    }

    @Test
    void recusaQuandoNaoSobraNada() {
        assertThatThrownBy(() -> Slugs.informadoOuGerado(null, "???", 120))
                .isInstanceOf(RegraNegocioException.class);
    }
}
