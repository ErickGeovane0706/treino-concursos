package br.com.treinoconcursos.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

/**
 * Cache HTTP de recursos estaticos publicos (RNF03, doc/ambiente-de-desenvolvimento.md 10.3):
 * - /assets/** e /fontes/**: 1 ano (immutable), pois os bundles do Vite levam hash no nome
 *   e a fonte WOFF2 e fixa.
 * - /css/**: 7 dias para a folha de estilos publica (/css/site.css).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        CacheControl cacheImutavelUmAno = CacheControl.maxAge(Duration.ofDays(365))
                .cachePublic()
                .immutable();

        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(cacheImutavelUmAno);

        registry.addResourceHandler("/fontes/**")
                .addResourceLocations("classpath:/static/fontes/")
                .setCacheControl(cacheImutavelUmAno);

        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic());
    }
}
