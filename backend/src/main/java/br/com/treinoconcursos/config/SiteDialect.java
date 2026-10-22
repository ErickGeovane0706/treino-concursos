package br.com.treinoconcursos.config;

import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.dialect.AbstractDialect;
import org.thymeleaf.dialect.IExpressionObjectDialect;
import org.thymeleaf.expression.IExpressionObjectFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Expoe {@code #site} (SiteConfig) e {@code #vite} (ViteManifest) nos templates.
 *
 * Por que nao {@code @site}: o Thymeleaf 3.1 recusa referencias a bean em
 * contextos restritos (parametros de fragmento, th:utext, links), e o layout
 * usa esses valores justamente ai. O Spring Boot registra todo IDialect.
 * Os beans sao buscados so no uso: testes de fatia (@WebMvcTest) carregam o
 * dialeto mas nao os componentes do site.
 */
@Component
public class SiteDialect extends AbstractDialect implements IExpressionObjectDialect {

    private final ObjectProvider<SiteConfig> site;
    private final ObjectProvider<ViteManifest> vite;

    public SiteDialect(ObjectProvider<SiteConfig> site, ObjectProvider<ViteManifest> vite) {
        super("treino-concursos");
        this.site = site;
        this.vite = vite;
    }

    @Override
    public IExpressionObjectFactory getExpressionObjectFactory() {
        return new IExpressionObjectFactory() {

            @Override
            public Set<String> getAllExpressionObjectNames() {
                return Set.of("site", "vite");
            }

            @Override
            public Object buildObject(IExpressionContext context, String nome) {
                return "site".equals(nome) ? site.getObject() : vite.getObject();
            }

            @Override
            public boolean isCacheable(String nome) {
                return true;
            }
        };
    }
}
