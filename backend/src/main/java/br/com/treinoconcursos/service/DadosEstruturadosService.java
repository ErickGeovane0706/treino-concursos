package br.com.treinoconcursos.service;

import br.com.treinoconcursos.config.SiteConfig;
import br.com.treinoconcursos.dto.ListagemQuestoesView;
import br.com.treinoconcursos.dto.QuestaoPaginaView;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dados estruturados schema.org (JSON-LD) das paginas publicas (RF09 RN4).
 *
 * Escolha (diretrizes do Google consultadas em 10/2026):
 * - "Practice problem" (Quiz de multipla escolha) foi retirado da busca em 2025/2026.
 * - "Education Q&A" (Quiz) so aceita eduQuestionType = Flashcard. Questao de
 *   concurso e de multipla escolha ou certo/errado: marcar como flashcard seria
 *   descrever a pagina de forma diferente do que ela e (risco de acao manual).
 * - Usamos o que vale para nossas paginas: BreadcrumbList (trilha) na questao e nas
 *   listagens, e WebSite (nome do site) na pagina inicial.
 *
 * O layout escreve o mapa com th:inline="javascript", que serializa em JSON e
 * escapa o conteudo para dentro de um script.
 */
@Service
public class DadosEstruturadosService {

    private static final String CONTEXTO = "https://schema.org";

    private final SiteConfig site;

    public DadosEstruturadosService(SiteConfig site) {
        this.site = site;
    }

    /** Pagina inicial: nome do site nos resultados de busca. */
    public Map<String, Object> site() {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("@context", CONTEXTO);
        dados.put("@type", "WebSite");
        dados.put("name", SiteConfig.NOME);
        dados.put("url", site.getUrl() + "/");
        return dados;
    }

    /** Questao: Questoes > Materia > Assunto > Questao #id. */
    public Map<String, Object> trilhaDaQuestao(QuestaoPaginaView questao) {
        List<ItemTrilha> itens = new ArrayList<>();
        itens.add(new ItemTrilha("Questões", "/questoes"));
        itens.add(new ItemTrilha(questao.nomeMateria(), "/questoes/" + questao.slugMateria()));
        if (questao.slugAssunto() != null) {
            itens.add(new ItemTrilha(questao.nomeAssunto(),
                    "/questoes/" + questao.slugMateria() + "/" + questao.slugAssunto()));
        }
        itens.add(new ItemTrilha("Questão #" + questao.id(), null));
        return trilha(itens);
    }

    /** Listagem de materia ou assunto; a de todas as questoes nao tem trilha. */
    public Map<String, Object> trilhaDaListagem(ListagemQuestoesView listagem) {
        if (listagem.slugMateriaAtiva() == null) {
            return null;
        }
        List<ItemTrilha> itens = new ArrayList<>();
        itens.add(new ItemTrilha("Questões", "/questoes"));
        itens.add(new ItemTrilha(listagem.nomeMateriaAtiva(), "/questoes/" + listagem.slugMateriaAtiva()));
        if (listagem.slugAssuntoAtivo() != null) {
            itens.add(new ItemTrilha(listagem.nomeAssuntoAtivo(),
                    "/questoes/" + listagem.slugMateriaAtiva() + "/" + listagem.slugAssuntoAtivo()));
        }
        return trilha(itens);
    }

    private Map<String, Object> trilha(List<ItemTrilha> itens) {
        List<Map<String, Object>> elementos = new ArrayList<>();
        for (int i = 0; i < itens.size(); i++) {
            Map<String, Object> elemento = new LinkedHashMap<>();
            elemento.put("@type", "ListItem");
            elemento.put("position", i + 1);
            elemento.put("name", itens.get(i).nome());
            // O ultimo item (a propria pagina) pode ficar sem URL.
            if (itens.get(i).caminho() != null) {
                elemento.put("item", site.getUrl() + itens.get(i).caminho());
            }
            elementos.add(elemento);
        }
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("@context", CONTEXTO);
        dados.put("@type", "BreadcrumbList");
        dados.put("itemListElement", elementos);
        return dados;
    }

    private record ItemTrilha(String nome, String caminho) {
    }
}
