package br.com.treinoconcursos.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RF04 RN4: Markdown vira HTML, mas HTML bruto e script nunca passam. */
class MarkdownServiceTest {

    private final MarkdownService service = new MarkdownService();

    @Test
    void converteMarkdownBasico() {
        assertThat(service.paraHtml("Assinale a **correta**:\n\n- um\n- dois"))
                .contains("<strong>correta</strong>")
                .contains("<li>um</li>");
    }

    @Test
    void htmlBrutoViraTextoVisivel() {
        String html = service.paraHtml("<script>alert(1)</script> e <b onclick=\"x()\">negrito</b>");

        assertThat(html).doesNotContain("<script").doesNotContain("<b ").doesNotContain("onclick=\"");
        assertThat(html).contains("&lt;script&gt;");
    }

    @Test
    void linkJavascriptEDescartado() {
        assertThat(service.paraHtml("[clique](javascript:alert(1))")).doesNotContain("javascript:");
    }

    @Test
    void linkExternoGanhaNofollow() {
        assertThat(service.paraHtml("[lei](https://www.planalto.gov.br/)"))
                .contains("href=\"https://www.planalto.gov.br/\"")
                .contains("nofollow");
    }

    @Test
    void imagemHttpsEPermitida() {
        assertThat(service.paraHtml("![figura](https://exemplo.com/a.png)"))
                .contains("<img")
                .contains("src=\"https://exemplo.com/a.png\"");
    }

    @Test
    void tabelaEPermitida() {
        assertThat(service.paraHtml("| a | b |\n|---|---|\n| 1 | 2 |")).contains("<table>").contains("<td>1</td>");
    }

    @Test
    void textoVazioDevolveVazio() {
        assertThat(service.paraHtml(null)).isEmpty();
        assertThat(service.paraHtml("   ")).isEmpty();
    }
}
