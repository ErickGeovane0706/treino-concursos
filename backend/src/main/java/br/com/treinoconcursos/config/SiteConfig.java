package br.com.treinoconcursos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Dados do site usados nos templates como {@code #site} (rodape, paginas
 * legais). Os de contato vem do .env, pois mudam de quem implanta para quem
 * implanta; vazios, as paginas mostram um aviso de pendencia.
 */
@Component
public class SiteConfig {

    public static final String NOME = "Treino de Concursos";

    private final String url;
    private final String contatoEmail;
    private final String responsavel;

    public SiteConfig(@Value("${app.site-url}") String url, @Value("${app.contato.email:}") String contatoEmail,
            @Value("${app.contato.responsavel:}") String responsavel) {
        this.url = url.replaceAll("/+$", "");
        this.contatoEmail = contatoEmail.trim();
        this.responsavel = responsavel.trim();
    }

    public String getNome() {
        return NOME;
    }

    public String getUrl() {
        return url;
    }

    public String getContatoEmail() {
        return contatoEmail;
    }

    public String getResponsavel() {
        return responsavel;
    }
}
