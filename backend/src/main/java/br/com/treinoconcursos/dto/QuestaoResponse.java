package br.com.treinoconcursos.dto;

import br.com.treinoconcursos.model.OrigemQuestao;
import br.com.treinoconcursos.model.Prova;
import br.com.treinoconcursos.model.Questao;
import br.com.treinoconcursos.model.StatusQuestao;
import br.com.treinoconcursos.model.TipoQuestao;

import java.time.LocalDateTime;
import java.util.List;

/** Questao completa para o formulario do painel; textos em Markdown cru. */
public record QuestaoResponse(Integer id, Integer idMateria, String nomeMateria, Integer idAssunto,
        String nomeAssunto, Integer idProva, String descricaoProva, String nomeAutor, TipoQuestao tipo,
        OrigemQuestao origem, String enunciado, String comentario, String slug, StatusQuestao status,
        boolean anulada, boolean desatualizada, int totalRespostas, int totalAcertos, LocalDateTime criadoEm,
        LocalDateTime atualizadoEm, LocalDateTime publicadoEm, List<AlternativaResponse> alternativas) {

    public static QuestaoResponse de(Questao q) {
        return new QuestaoResponse(q.getId(), q.getMateria().getId(), q.getMateria().getNome(),
                q.getAssunto() == null ? null : q.getAssunto().getId(),
                q.getAssunto() == null ? null : q.getAssunto().getNome(),
                q.getProva() == null ? null : q.getProva().getId(), descrever(q.getProva()),
                q.getAutor().getNome(), q.getTipo(), q.getOrigem(), q.getEnunciado(), q.getComentario(),
                q.getSlug(), q.getStatus(), q.isAnulada(), q.isDesatualizada(), q.getTotalRespostas(),
                q.getTotalAcertos(), q.getCriadoEm(), q.getAtualizadoEm(), q.getPublicadoEm(),
                q.getAlternativas().stream().map(AlternativaResponse::de).toList());
    }

    /** Ex.: "FGV · TCE-GO · 2026 · Técnico de Controle Externo" */
    static String descrever(Prova prova) {
        if (prova == null) {
            return null;
        }
        return prova.getBanca().getSigla() + " · " + prova.getOrgao().getSigla() + " · " + prova.getAno() + " · "
                + prova.getCargo();
    }
}
