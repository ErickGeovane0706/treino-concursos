package br.com.treinoconcursos.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.dialect.AbstractProcessorDialect;
import org.thymeleaf.dialect.IExpressionObjectDialect;
import org.thymeleaf.expression.IExpressionObjectFactory;
import org.thymeleaf.processor.IProcessor;
import org.thymeleaf.standard.StandardDialect;
import org.thymeleaf.standard.processor.AbstractStandardAttributeModifierTagProcessor;
import org.thymeleaf.templatemode.TemplateMode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Expoe {@code #site} (SiteConfig) e {@code #vite} (ViteManifest) nos templates
 * e registra processadores de atributos {@code th:data-*} com
 * {@code AttributeDefinition} pre-resolvido na inicializacao (RNF03, issue #16).
 *
 * Por que pre-resolver {@code th:data-*}: no Thymeleaf 3.1, {@code th:attr} e o
 * fallback {@code StandardDefaultAttributesTagProcessor} consultam
 * {@code AttributeDefinitions.forName(...)} a cada requisicao, disputando um
 * {@code ReentrantReadWriteLock(true)} global entre as threads do servidor.
 * Com {@link AbstractStandardAttributeModifierTagProcessor}, a definicao de cada
 * atributo {@code data-*} e resolvida uma unica vez na subida da aplicacao.
 */
@Component
public class SiteDialect extends AbstractProcessorDialect implements IExpressionObjectDialect {

    private static final List<String> ATRIBUTOS_DATA = List.of(
            "data-id",
            "data-correta",
            "data-rotulo-correta",
            "data-tipo",
            "data-proxima-url",
            "data-listagem-url",
            "data-filtro",
            "data-letra",
            "data-id-questao",
            "data-materia",
            "data-assunto",
            "data-ads-enabled",
            "data-ad-client",
            "data-ad-slot",
            "data-consentimento-atual",
            "data-npa",
            "data-consentimento-aplicado",
            "data-adsense-src"
    );

    private final ObjectProvider<SiteConfig> site;
    private final ObjectProvider<ViteManifest> vite;

    public SiteDialect(ObjectProvider<SiteConfig> site, ObjectProvider<ViteManifest> vite) {
        super("treino-concursos", "th", StandardDialect.PROCESSOR_PRECEDENCE);
        this.site = site;
        this.vite = vite;
    }

    @Override
    public Set<IProcessor> getProcessors(String dialectPrefix) {
        Set<IProcessor> processadores = new LinkedHashSet<>();
        for (String atributo : ATRIBUTOS_DATA) {
            processadores.add(new DataAttributeTagProcessor(dialectPrefix, atributo));
        }
        return processadores;
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

    private static final class DataAttributeTagProcessor extends AbstractStandardAttributeModifierTagProcessor {

        DataAttributeTagProcessor(String dialectPrefix, String attrName) {
            super(TemplateMode.HTML, dialectPrefix, attrName, attrName, 1000, true, false);
        }
    }
}
