package br.com.treinoconcursos.controller;

import br.com.treinoconcursos.config.SiteConfig;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Link canonico de toda pagina publica (RF09 RN3, doc/api.md secao 2), usado no
 * {@code <link rel="canonical">} e no {@code og:url} do layout.
 *
 * A canonica e o {@code SITE_URL} mais o caminho, sem query string: parametros de
 * navegacao como {@code ?de=} nao fazem parte dela. A excecao e o cursor
 * {@code ?depoisDe=} das listagens, porque cada pagina da lista tem questoes
 * diferentes e e canonica de si mesma.
 */
@ControllerAdvice(assignableTypes = {InstitucionalController.class, ListagemQuestoesController.class,
        QuestaoPublicaController.class})
public class PaginaPublicaAdvice {

    /** Buscado so no uso: testes de fatia (@WebMvcTest) carregam o advice, mas nao o SiteConfig. */
    private final ObjectProvider<SiteConfig> site;

    public PaginaPublicaAdvice(ObjectProvider<SiteConfig> site) {
        this.site = site;
    }

    @ModelAttribute("urlCanonica")
    public String urlCanonica(HttpServletRequest request) {
        String base = site.getObject().getUrl();
        String caminho = request.getRequestURI().substring(request.getContextPath().length());
        String cursor = request.getParameter("depoisDe");
        if (cursor != null && cursor.matches("[1-9]\\d{0,9}")) {
            return base + caminho + "?depoisDe=" + cursor;
        }
        return base + caminho;
    }
}
