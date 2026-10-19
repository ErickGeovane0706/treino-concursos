package br.com.treinoconcursos.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/** Helper do manifest.json do Vite (doc/ambiente-de-desenvolvimento.md, 10.3). */
class ViteManifestTest {

    private final ViteManifest producao = new ViteManifest("", new ClassPathResource("vite/manifest.json"));

    @Test
    void producaoDevolveArquivosComHash() {
        ViteManifest.Ativos ativos = producao.ativos("src/consentimento/main.ts");

        assertThat(producao.dev()).isFalse();
        assertThat(ativos.script()).isEqualTo("/assets/consentimento-Ghi789.js");
        assertThat(ativos.estilos()).isEmpty();
        assertThat(ativos.preload()).isEmpty();
    }

    /** CSS da entrada e dos chunks importados, e os chunks para modulepreload, sem repetir (ha import circular). */
    @Test
    void producaoSegueOsImports() {
        ViteManifest.Ativos ativos = producao.ativos("src/ilhas/treino.tsx");

        assertThat(ativos.script()).isEqualTo("/assets/ilha-treino-Def456.js");
        assertThat(ativos.estilos()).containsExactly("/assets/ilha-treino-Css222.css", "/assets/montar-Css111.css");
        assertThat(ativos.preload()).containsExactly("/assets/montar-AbC123.js", "/assets/jsx-runtime-Xyz789.js");
    }

    @Test
    void entradaForaDoBuildDevolveNull() {
        assertThat(producao.ativos("src/ilhas/inexistente.tsx")).isNull();
    }

    /** Backend sem o build do frontend: as ilhas nao carregam, mas nada quebra. */
    @Test
    void semManifestNaoQuebra() {
        ViteManifest semBuild = new ViteManifest("", new ClassPathResource("vite/nao-existe.json"));

        assertThat(semBuild.ativos("src/ilhas/treino.tsx")).isNull();
    }

    @Test
    void desenvolvimentoApontaParaOServidorDoVite() {
        ViteManifest dev = new ViteManifest("http://localhost:5173/", new ClassPathResource("vite/nao-existe.json"));
        ViteManifest.Ativos ativos = dev.ativos("src/ilhas/treino.tsx");

        assertThat(dev.dev()).isTrue();
        assertThat(dev.servidorDev()).isEqualTo("http://localhost:5173");
        assertThat(ativos.script()).isEqualTo("http://localhost:5173/src/ilhas/treino.tsx");
        assertThat(ativos.estilos()).isEmpty();
        assertThat(dev.scriptsDev()).containsExactly("http://localhost:5173/src/dev/preambulo-react.ts",
                "http://localhost:5173/@vite/client");
    }
}
