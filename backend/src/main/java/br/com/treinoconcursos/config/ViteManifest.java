package br.com.treinoconcursos.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Diz ao Thymeleaf quais arquivos carregar para uma ilha React
 * (doc/ambiente-de-desenvolvimento.md, 10.3). Nos templates: {@code #vite}.
 *
 * - Desenvolvimento (VITE_DEV_SERVER preenchido): aponta direto para o
 *   servidor do Vite, com hot reload; o CSS vem pelo proprio JavaScript.
 * - Producao: le o manifest.json do build (copiado para static/ pelo Maven)
 *   e devolve os nomes com hash, o CSS da entrada e dos chunks importados e
 *   os chunks para modulepreload.
 *
 * Sem manifest (ex.: backend rodando sem o build do frontend), a ilha
 * simplesmente nao carrega: o HTML do servidor continua funcionando.
 */
@Component
public class ViteManifest {

    private static final Logger log = LoggerFactory.getLogger(ViteManifest.class);
    static final String CAMINHO_MANIFEST = "static/.vite/manifest.json";

    private final String servidorDev;
    private final JsonNode manifest;
    private final Map<String, Ativos> cacheAtivos = new ConcurrentHashMap<>();

    @Autowired
    public ViteManifest(@Value("${app.vite-dev-server:}") String servidorDev) {
        this(servidorDev, new ClassPathResource(CAMINHO_MANIFEST));
    }

    ViteManifest(String servidorDev, Resource arquivoManifest) {
        this.servidorDev = servidorDev == null ? "" : servidorDev.replaceAll("/+$", "");
        this.manifest = dev() ? null : ler(arquivoManifest);
    }

    /** Arquivos de uma entrada, ja com o caminho que vai no HTML. */
    public record Ativos(String script, List<String> estilos, List<String> preload) {
    }

    public boolean dev() {
        return !servidorDev.isEmpty();
    }

    public String servidorDev() {
        return servidorDev;
    }

    /**
     * Scripts que so existem em dev: o cliente do Vite (hot reload) e o
     * preambulo do @vitejs/plugin-react (src/dev/preambulo-react.ts). Montados
     * aqui porque o Thymeleaf recusa "@" dentro de th:src (modo restrito).
     */
    public List<String> scriptsDev() {
        return List.of(servidorDev + "/src/dev/preambulo-react.ts", servidorDev + "/@vite/client");
    }


    /**
     * @param entrada caminho da entrada no projeto do frontend (ex.: src/ilhas/treino.tsx)
     * @return os arquivos, ou {@code null} se a entrada nao estiver no build
     */
    public Ativos ativos(String entrada) {
        if (dev()) {
            return new Ativos(servidorDev + "/" + entrada, List.of(), List.of());
        }
        Ativos emCache = cacheAtivos.get(entrada);
        if (emCache != null) {
            return emCache;
        }
        JsonNode chunk = manifest == null ? null : manifest.get(entrada);
        if (chunk == null) {
            log.warn("Entrada {} não está no manifest do Vite: rode npm run build.", entrada);
            return null;
        }
        Set<String> estilos = new LinkedHashSet<>();
        Set<String> preload = new LinkedHashSet<>();
        coletar(chunk, estilos, preload, new LinkedHashSet<>());
        Ativos resolvido = new Ativos("/" + chunk.get("file").asString(), List.copyOf(estilos), List.copyOf(preload));
        cacheAtivos.put(entrada, resolvido);
        return resolvido;
    }

    /** CSS e imports estaticos, seguindo a arvore de imports sem repetir chunk. */
    private void coletar(JsonNode chunk, Set<String> estilos, Set<String> preload, Set<String> visitados) {
        for (JsonNode css : iteravel(chunk.get("css"))) {
            estilos.add("/" + css.asString());
        }
        for (JsonNode importacao : iteravel(chunk.get("imports"))) {
            String chave = importacao.asString();
            JsonNode importado = manifest.get(chave);
            if (importado == null || !visitados.add(chave)) {
                continue;
            }
            preload.add("/" + importado.get("file").asString());
            coletar(importado, estilos, preload, visitados);
        }
    }

    private static List<JsonNode> iteravel(JsonNode lista) {
        List<JsonNode> itens = new ArrayList<>();
        if (lista != null) {
            lista.forEach(itens::add);
        }
        return itens;
    }

    private static JsonNode ler(Resource arquivo) {
        if (!arquivo.exists()) {
            log.warn("Manifest do Vite não encontrado ({}): as ilhas React não serão carregadas.", arquivo);
            return null;
        }
        try (InputStream entrada = arquivo.getInputStream()) {
            return JsonMapper.builder().build().readTree(entrada);
        } catch (IOException e) {
            throw new IllegalStateException("Manifest do Vite inválido: " + arquivo, e);
        }
    }
}
