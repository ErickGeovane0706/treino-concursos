package br.com.treinoconcursos.dto;

import br.com.treinoconcursos.model.NivelEscolaridade;
import br.com.treinoconcursos.model.TipoQuestao;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Representa a combinacao de filtros ativos nas listagens, na API de IDs e no parametro
 * {@code ?de=} da pagina da questao (RF07 RN1 e RN4).
 */
public record FiltroQuestoes(
        String slugMateria,
        String slugAssunto,
        String slugBanca,
        String slugOrgao,
        Integer ano,
        NivelEscolaridade nivel,
        TipoQuestao tipo,
        int quantidadeInformada,
        boolean invalido) {

    public static final int ANO_MINIMO = 1990;
    public static final int ANO_MAXIMO = 2100;

    private static final Pattern SLUG_VALIDO = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    public static final FiltroQuestoes VAZIO = new FiltroQuestoes(
            null, null, null, null, null, null, null, 0, false);

    public static FiltroQuestoes de(
            String slugMateria,
            String slugAssunto,
            String slugBanca,
            String slugOrgao,
            Integer ano,
            NivelEscolaridade nivel,
            TipoQuestao tipo) {
        String mat = limparSlug(slugMateria);
        String ass = limparSlug(slugAssunto);
        String ban = limparSlug(slugBanca);
        String org = limparSlug(slugOrgao);
        int qtd = contarNaoNulos(mat, ass, ban, org, ano, nivel, tipo);
        return new FiltroQuestoes(mat, ass, ban, org, ano, nivel, tipo, qtd, false);
    }

    public static FiltroQuestoes deParametros(
            String materia,
            String assunto,
            String banca,
            String orgao,
            String anoTexto,
            String nivelTexto,
            String tipoTexto) {
        String mat = limparTexto(materia);
        String ass = limparTexto(assunto);
        String ban = limparTexto(banca);
        String org = limparTexto(orgao);
        String anoLimpo = limparTexto(anoTexto);
        String nivelLimpo = limparTexto(nivelTexto);
        String tipoLimpo = limparTexto(tipoTexto);

        int qtdInformada = contarNaoNulos(mat, ass, ban, org, anoLimpo, nivelLimpo, tipoLimpo);
        boolean invalido = false;

        if (mat != null && !slugValido(mat)) {
            invalido = true;
        }
        if (ass != null && !slugValido(ass)) {
            invalido = true;
        }
        if (ban != null && !slugValido(ban)) {
            invalido = true;
        }
        if (org != null && !slugValido(org)) {
            invalido = true;
        }

        Integer ano = null;
        if (anoLimpo != null) {
            ano = converterAno(anoLimpo);
            if (ano == null) {
                invalido = true;
            }
        }

        NivelEscolaridade nivel = null;
        if (nivelLimpo != null) {
            nivel = converterNivel(nivelLimpo);
            if (nivel == null) {
                invalido = true;
            }
        }

        TipoQuestao tipo = null;
        if (tipoLimpo != null) {
            tipo = converterTipo(tipoLimpo);
            if (tipo == null) {
                invalido = true;
            }
        }

        return new FiltroQuestoes(mat, ass, ban, org, ano, nivel, tipo, qtdInformada, invalido);
    }

    /**
     * Interpreta o parametro {@code ?de=} (ou {@code data-filtro}) em um {@link FiltroQuestoes}.
     * Devolve {@code null} quando o valor e vazio ou invalido.
     */
    public static FiltroQuestoes deChaveFiltro(String de) {
        if (de == null) {
            return null;
        }
        String limpo = de.trim().replaceAll("^/+|/+$", "");
        if (limpo.isEmpty() || "todas".equals(limpo)) {
            return null;
        }
        if (limpo.contains("=")) {
            String mat = null;
            String ass = null;
            String ban = null;
            String org = null;
            String ano = null;
            String nivel = null;
            String tipo = null;
            for (String par : limpo.split("[,&]")) {
                String[] kv = par.split("=", 2);
                if (kv.length != 2 || kv[1].isBlank()) {
                    return null;
                }
                String chave = kv[0].trim();
                String valor = kv[1].trim();
                switch (chave) {
                    case "materia" -> mat = valor;
                    case "assunto" -> ass = valor;
                    case "banca" -> ban = valor;
                    case "orgao" -> org = valor;
                    case "ano" -> ano = valor;
                    case "nivel" -> nivel = valor;
                    case "tipo" -> tipo = valor;
                    default -> {
                        return null;
                    }
                }
            }
            FiltroQuestoes filtro = deParametros(mat, ass, ban, org, ano, nivel, tipo);
            return filtro.invalido() || filtro.vazio() ? null : filtro;
        }
        if (limpo.startsWith("banca/")) {
            String slug = limpo.substring("banca/".length());
            return slugValido(slug) ? de(null, null, slug, null, null, null, null) : null;
        }
        if (limpo.startsWith("orgao/")) {
            String slug = limpo.substring("orgao/".length());
            return slugValido(slug) ? de(null, null, null, slug, null, null, null) : null;
        }
        if (limpo.startsWith("ano/")) {
            Integer ano = converterAno(limpo.substring("ano/".length()));
            return ano != null ? de(null, null, null, null, ano, null, null) : null;
        }
        if (limpo.startsWith("nivel/")) {
            NivelEscolaridade nivel = converterNivel(limpo.substring("nivel/".length()));
            return nivel != null ? de(null, null, null, null, null, nivel, null) : null;
        }
        if (limpo.startsWith("tipo/")) {
            TipoQuestao tipo = converterTipo(limpo.substring("tipo/".length()));
            return tipo != null ? de(null, null, null, null, null, null, tipo) : null;
        }
        String[] partes = limpo.split("/", 2);
        if (!slugValido(partes[0])) {
            return null;
        }
        if (partes.length == 2) {
            return slugValido(partes[1]) ? de(partes[0], partes[1], null, null, null, null, null) : null;
        }
        return de(partes[0], null, null, null, null, null, null);
    }

    public boolean vazio() {
        return quantidadeInformada == 0 && quantidadeReconhecida() == 0;
    }

    public int quantidadeFiltros() {
        return Math.max(quantidadeInformada, quantidadeReconhecida());
    }

    public boolean temFiltroDeProva() {
        return slugBanca != null || slugOrgao != null || ano != null || nivel != null;
    }

    public FiltroQuestoes comMateria(String novaMateria) {
        return de(novaMateria, null, slugBanca, slugOrgao, ano, nivel, tipo);
    }

    public FiltroQuestoes comAssunto(String novoAssunto) {
        return de(slugMateria, novoAssunto, slugBanca, slugOrgao, ano, nivel, tipo);
    }

    public FiltroQuestoes comBanca(String novaBanca) {
        return de(slugMateria, slugAssunto, novaBanca, slugOrgao, ano, nivel, tipo);
    }

    public FiltroQuestoes comOrgao(String novoOrgao) {
        return de(slugMateria, slugAssunto, slugBanca, novoOrgao, ano, nivel, tipo);
    }

    public FiltroQuestoes comAno(Integer novoAno) {
        return de(slugMateria, slugAssunto, slugBanca, slugOrgao, novoAno, nivel, tipo);
    }

    public FiltroQuestoes comNivel(NivelEscolaridade novoNivel) {
        return de(slugMateria, slugAssunto, slugBanca, slugOrgao, ano, novoNivel, tipo);
    }

    public FiltroQuestoes comTipo(TipoQuestao novoTipo) {
        return de(slugMateria, slugAssunto, slugBanca, slugOrgao, ano, nivel, novoTipo);
    }

    /**
     * Identificador compacto do filtro para {@code ?de=} e {@code data-filtro}.
     */
    public String chaveFiltro() {
        int qtd = quantidadeReconhecida();
        if (qtd == 0) {
            return null;
        }
        if (qtd == 1) {
            if (slugMateria != null) {
                return slugMateria;
            }
            if (slugBanca != null) {
                return "banca/" + slugBanca;
            }
            if (slugOrgao != null) {
                return "orgao/" + slugOrgao;
            }
            if (ano != null) {
                return "ano/" + ano;
            }
            if (nivel != null) {
                return "nivel/" + nivel.name();
            }
            if (tipo != null) {
                return "tipo/" + tipo.name();
            }
        }
        if (qtd == 2 && slugMateria != null && slugAssunto != null) {
            return slugMateria + "/" + slugAssunto;
        }
        List<String> pares = paresParametros();
        return pares.isEmpty() ? null : String.join(",", pares);
    }

    /**
     * URL canonica da listagem correspondente ao filtro (rota dedicada quando aplicavel,
     * ou {@code /questoes?...} para combinacoes), incluindo {@code depoisDe} quando maior que zero.
     */
    public String urlListagem(Integer depoisDe) {
        String base = caminhoBaseOuQuery();
        if (depoisDe != null && depoisDe > 0) {
            return base + (base.contains("?") ? "&" : "?") + "depoisDe=" + depoisDe;
        }
        return base;
    }

    private String caminhoBaseOuQuery() {
        int qtd = quantidadeReconhecida();
        if (qtd == 0) {
            return "/questoes";
        }
        if (qtd == 1) {
            if (slugMateria != null) {
                return "/questoes/" + slugMateria;
            }
            if (slugBanca != null) {
                return "/questoes/banca/" + slugBanca;
            }
            if (slugOrgao != null) {
                return "/questoes/orgao/" + slugOrgao;
            }
            if (ano != null) {
                return "/questoes/ano/" + ano;
            }
        }
        if (qtd == 2 && slugMateria != null && slugAssunto != null) {
            return "/questoes/" + slugMateria + "/" + slugAssunto;
        }
        List<String> pares = paresParametros();
        return pares.isEmpty() ? "/questoes" : "/questoes?" + String.join("&", pares);
    }

    private List<String> paresParametros() {
        List<String> pares = new ArrayList<>(7);
        if (slugMateria != null) {
            pares.add("materia=" + slugMateria);
        }
        if (slugAssunto != null) {
            pares.add("assunto=" + slugAssunto);
        }
        if (slugBanca != null) {
            pares.add("banca=" + slugBanca);
        }
        if (slugOrgao != null) {
            pares.add("orgao=" + slugOrgao);
        }
        if (ano != null) {
            pares.add("ano=" + ano);
        }
        if (nivel != null) {
            pares.add("nivel=" + nivel.name());
        }
        if (tipo != null) {
            pares.add("tipo=" + tipo.name());
        }
        return pares;
    }

    private int quantidadeReconhecida() {
        return contarNaoNulos(slugMateria, slugAssunto, slugBanca, slugOrgao, ano, nivel, tipo);
    }

    public static Integer converterAno(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            int valor = Integer.parseInt(texto.trim());
            return valor >= ANO_MINIMO && valor <= ANO_MAXIMO ? valor : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static NivelEscolaridade converterNivel(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String normalizado = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT);
        return switch (normalizado) {
            case "FUNDAMENTAL" -> NivelEscolaridade.FUNDAMENTAL;
            case "MEDIO" -> NivelEscolaridade.MEDIO;
            case "SUPERIOR" -> NivelEscolaridade.SUPERIOR;
            default -> null;
        };
    }

    public static TipoQuestao converterTipo(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String normalizado = texto.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (normalizado) {
            case "MULTIPLA_ESCOLHA" -> TipoQuestao.MULTIPLA_ESCOLHA;
            case "CERTO_ERRADO" -> TipoQuestao.CERTO_ERRADO;
            default -> null;
        };
    }

    public static String rotuloNivel(NivelEscolaridade nivel) {
        if (nivel == null) {
            return null;
        }
        return switch (nivel) {
            case FUNDAMENTAL -> "Fundamental";
            case MEDIO -> "Médio";
            case SUPERIOR -> "Superior";
        };
    }

    public static String rotuloTipo(TipoQuestao tipo) {
        if (tipo == null) {
            return null;
        }
        return switch (tipo) {
            case MULTIPLA_ESCOLHA -> "Múltipla escolha";
            case CERTO_ERRADO -> "Certo ou errado";
        };
    }

    public static boolean slugValido(String slug) {
        return slug != null && SLUG_VALIDO.matcher(slug).matches();
    }

    private static String limparSlug(String valor) {
        String limpo = limparTexto(valor);
        return limpo != null && slugValido(limpo) ? limpo : null;
    }

    private static String limparTexto(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }

    private static int contarNaoNulos(Object... valores) {
        int total = 0;
        for (Object v : valores) {
            if (v != null) {
                total++;
            }
        }
        return total;
    }
}
