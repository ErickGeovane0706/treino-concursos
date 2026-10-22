package br.com.treinoconcursos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.ResourceUrlEncodingFilter;
import org.springframework.web.servlet.resource.VersionResourceResolver;

import java.time.Duration;

/**
 * Cache dos arquivos estaticos e das paginas (RF09, RNF03).
 *
 * - /assets/**: saida do Vite, ja com hash no nome, entao e imutavel.
 * - /css/** e /fontes/**: o nome nao muda, entao o Spring poe o hash do conteudo
 *   na URL (site-3f2a....css). O layout usa @{...} e o ResourceUrlEncodingFilter
 *   reescreve o link; o CssLinkResourceTransformer faz o mesmo com o url() da fonte
 *   dentro do CSS. Arquivo novo, URL nova: tambem pode ser imutavel.
 * - O resto (painel em /admin, API) fica com o "no-store" do Spring Security.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    static final CacheControl CACHE_IMUTAVEL = CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CACHE_IMUTAVEL);
        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/")
                .setCacheControl(CACHE_IMUTAVEL)
                .resourceChain(true)
                .addResolver(new VersionResourceResolver().addContentVersionStrategy("/**"));
        registry.addResourceHandler("/fontes/**")
                .addResourceLocations("classpath:/static/fontes/")
                .setCacheControl(CACHE_IMUTAVEL)
                .resourceChain(true)
                .addResolver(new VersionResourceResolver().addContentVersionStrategy("/**"));
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new CachePaginasInterceptor());
    }

    /** Reescreve os links @{/css/...} e @{/fontes/...} dos templates para a URL com hash. */
    @Bean
    ResourceUrlEncodingFilter resourceUrlEncodingFilter() {
        return new ResourceUrlEncodingFilter();
    }
}
