package br.com.treinoconcursos.controller;

import br.com.treinoconcursos.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Paginas de erro HTML 404, 410 e 500: sem anuncio e noindex (CLAUDE.md, RF05 RN3).
 *
 * Com servidor de verdade (porta aleatoria): o MockMvc nao faz o "forward"
 * para /error do container de servlets, entao nao renderiza essas paginas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.admin-inicial.email=", "app.admin-inicial.senha=", "app.vite-dev-server="})
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, PaginasErroTest.ControllerQueFalha.class})
class PaginasErroTest {

    @LocalServerPort
    private int porta;

    private final HttpClient cliente = HttpClient.newHttpClient();

    @Test
    void enderecoInexistenteMostraPagina404() throws Exception {
        HttpResponse<String> resposta = abrir("/pagina-que-nao-existe");

        assertThat(resposta.statusCode()).isEqualTo(404);
        assertPaginaDeErro(resposta.body(), "Página não encontrada");
    }

    @Test
    void conteudoRemovidoMostraPagina410() throws Exception {
        HttpResponse<String> resposta = abrir("/teste-erro/removido");

        assertThat(resposta.statusCode()).isEqualTo(410);
        assertPaginaDeErro(resposta.body(), "Este conteúdo foi removido");
    }

    @Test
    void falhaInesperadaMostraPagina500SemDetalhes() throws Exception {
        HttpResponse<String> resposta = abrir("/teste-erro/falha");

        assertThat(resposta.statusCode()).isEqualTo(500);
        assertPaginaDeErro(resposta.body(), "Algo deu errado do nosso lado");
        assertThat(resposta.body()).doesNotContain("segredo-interno").doesNotContain("IllegalStateException");
    }

    /** A API continua respondendo JSON, nao a pagina HTML. */
    @Test
    void apiInexistenteNaoRecebeHtml() throws Exception {
        HttpRequest pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/api/nao-existe"))
                .header("Accept", "application/json").build();
        HttpResponse<String> resposta = cliente.send(pedido, HttpResponse.BodyHandlers.ofString());

        assertThat(resposta.statusCode()).isEqualTo(404);
        assertThat(resposta.body()).doesNotContain("<html");
    }

    private HttpResponse<String> abrir(String caminho) throws Exception {
        HttpRequest pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho))
                .header("Accept", "text/html").build();
        return cliente.send(pedido, HttpResponse.BodyHandlers.ofString());
    }

    private static void assertPaginaDeErro(String html, String titulo) {
        assertThat(html)
                .contains(titulo)
                .contains("<meta name=\"robots\" content=\"noindex\">")
                .contains("href=\"/privacidade\"")
                .doesNotContain("adsbygoogle")
                .doesNotContain("Publicidade");
    }

    @Controller
    static class ControllerQueFalha {

        @GetMapping("/teste-erro/removido")
        String removido() {
            throw new ResponseStatusException(HttpStatus.GONE);
        }

        @GetMapping("/teste-erro/falha")
        String falha() {
            throw new IllegalStateException("segredo-interno");
        }
    }
}
