package br.com.treinoconcursos.service;

import br.com.treinoconcursos.exception.RegraNegocioException;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Gera slugs para as URLs (RF02 RN4): minusculas, sem acentos, palavras
 * separadas por hifen. Ex.: "Direito Constitucional" -> "direito-constitucional".
 */
public final class Slugs {

    private Slugs() {
    }

    /**
     * @param texto  texto de origem (nome, sigla, enunciado)
     * @param limite tamanho maximo; o corte nunca deixa hifen no fim
     * @return o slug, ou "" se o texto nao tiver letras nem numeros
     */
    public static String gerar(String texto, int limite) {
        if (texto == null) {
            return "";
        }
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = semAcento.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.length() > limite) {
            slug = slug.substring(0, limite).replaceAll("-+$", "");
        }
        return slug;
    }

    /**
     * Slug informado pelo editor (normalizado) ou, se vazio, gerado da origem.
     *
     * @throws RegraNegocioException se nao sobrar nenhuma letra ou numero
     */
    static String informadoOuGerado(String informado, String origem, int limite) {
        String slug = gerar(informado == null || informado.isBlank() ? origem : informado, limite);
        if (slug.isEmpty()) {
            throw new RegraNegocioException("Não foi possível gerar o endereço (slug): use letras ou números.");
        }
        return slug;
    }
}
