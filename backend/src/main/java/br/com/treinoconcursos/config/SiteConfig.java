package br.com.treinoconcursos.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Dados do site usados nos templates como {@code #site} (rodape, paginas
 * legais e blocos de anuncio). Os de contato e publicidade vem do .env.
 */
@Component
public class SiteConfig {

    public static final String NOME = "Treino de Concursos";
    public static final String COOKIE_CONSENTIMENTO = "consentimento-anuncios";
    private static final String CERTIFICACAO_GOOGLE_ADS_TXT = "f08c47fec0942fa0";

    private final String url;
    private final String contatoEmail;
    private final String responsavel;
    private final boolean adsEnabled;
    private final String adsensePublisherId;
    private final String adsenseClientId;
    private final String adsenseSlotQuestao;
    private final String adsenseSlotLateral;

    public SiteConfig(String url, String contatoEmail, String responsavel) {
        this(url, contatoEmail, responsavel, false, "", "", "");
    }

    @Autowired
    public SiteConfig(
            @Value("${app.site-url}") String url,
            @Value("${app.contato.email:}") String contatoEmail,
            @Value("${app.contato.responsavel:}") String responsavel,
            @Value("${app.ads.enabled:false}") boolean adsEnabled,
            @Value("${app.ads.publisher-id:}") String adsensePublisherId,
            @Value("${app.ads.slot-questao:}") String adsenseSlotQuestao,
            @Value("${app.ads.slot-lateral:}") String adsenseSlotLateral) {
        this.url = url.replaceAll("/+$", "");
        this.contatoEmail = contatoEmail == null ? "" : contatoEmail.trim();
        this.responsavel = responsavel == null ? "" : responsavel.trim();
        this.adsEnabled = adsEnabled;
        this.adsensePublisherId = normalizarPublisherId(adsensePublisherId);
        this.adsenseClientId = this.adsensePublisherId.isEmpty() ? "" : "ca-" + this.adsensePublisherId;
        this.adsenseSlotQuestao = adsenseSlotQuestao == null ? "" : adsenseSlotQuestao.trim();
        this.adsenseSlotLateral = adsenseSlotLateral == null ? "" : adsenseSlotLateral.trim();
    }

    private static String normalizarPublisherId(String bruto) {
        if (bruto == null) {
            return "";
        }
        String limpo = bruto.trim();
        if (limpo.isEmpty()) {
            return "";
        }
        if (limpo.startsWith("ca-pub-")) {
            return limpo.substring("ca-".length());
        }
        if (limpo.startsWith("pub-")) {
            return limpo;
        }
        return "pub-" + limpo;
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

    public boolean isAdsEnabled() {
        return adsEnabled;
    }

    public String getAdsensePublisherId() {
        return adsensePublisherId;
    }

    public String getAdsenseClientId() {
        return adsenseClientId;
    }

    public String getAdsenseSlotQuestao() {
        return adsenseSlotQuestao;
    }

    public String getAdsenseSlotLateral() {
        return adsenseSlotLateral;
    }

    public String getScriptAdsenseUrl() {
        String base = "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js";
        return adsenseClientId.isEmpty() ? base : base + "?client=" + adsenseClientId;
    }

    /**
     * RF12 RN5: linha oficial do Google AdSense para /ads.txt, ou vazio quando
     * ADSENSE_PUBLISHER_ID ainda nao foi configurado.
     */
    public String getAdsTxtConteudo() {
        if (adsensePublisherId.isEmpty()) {
            return "";
        }
        return "google.com, " + adsensePublisherId + ", DIRECT, " + CERTIFICACAO_GOOGLE_ADS_TXT + "\n";
    }

    /**
     * Le a escolha do aviso de cookies (RF18 RN2) enviada na requisicao atual,
     * sem criar sessao nem gravar cookie.
     */
    public String getConsentimentoAnuncios() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
            return null;
        }
        HttpServletRequest request = servletAttrs.getRequest();
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_CONSENTIMENTO.equals(cookie.getName())) {
                String valor = cookie.getValue();
                if ("personalizados".equals(valor) || "nao-personalizados".equals(valor)) {
                    return valor;
                }
            }
        }
        return null;
    }

    public boolean isAnunciosPersonalizados() {
        return "personalizados".equals(getConsentimentoAnuncios());
    }

    public boolean isAnunciosNaoPersonalizados() {
        return !isAnunciosPersonalizados();
    }
}
