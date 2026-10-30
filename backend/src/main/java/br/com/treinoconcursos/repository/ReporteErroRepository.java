package br.com.treinoconcursos.repository;

import br.com.treinoconcursos.model.MotivoReporte;
import br.com.treinoconcursos.model.ReporteErro;
import br.com.treinoconcursos.model.StatusReporte;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReporteErroRepository extends JpaRepository<ReporteErro, Integer> {

    interface ContagemPorQuestao {
        Integer getIdQuestao();

        long getTotal();
    }

    @EntityGraph(attributePaths = {"questao", "questao.materia", "resolvidoPor"})
    Optional<ReporteErro> findComDetalhesById(Integer id);

    /**
     * RF16 RN5 e US12: fila de reportes do painel.
     * Questoes que acumulam 3 ou mais reportes ABERTOS de GABARITO_ERRADO
     * aparecem no topo da fila como prioridade.
     */
    @EntityGraph(attributePaths = {"questao", "questao.materia", "resolvidoPor"})
    @Query(
            value = """
                    select r from ReporteErro r
                    where (:status is null or r.status = :status)
                      and (:motivo is null or r.motivo = :motivo)
                      and (:idQuestao is null or r.questao.id = :idQuestao)
                    order by
                      case when r.status = br.com.treinoconcursos.model.StatusReporte.ABERTO then 0 else 1 end asc,
                      case when (
                        select count(r2) from ReporteErro r2
                        where r2.questao = r.questao
                          and r2.status = br.com.treinoconcursos.model.StatusReporte.ABERTO
                          and r2.motivo = br.com.treinoconcursos.model.MotivoReporte.GABARITO_ERRADO
                      ) >= 3 then 0 else 1 end asc,
                      r.criadoEm asc,
                      r.id asc
                    """,
            countQuery = """
                    select count(r) from ReporteErro r
                    where (:status is null or r.status = :status)
                      and (:motivo is null or r.motivo = :motivo)
                      and (:idQuestao is null or r.questao.id = :idQuestao)
                    """
    )
    Page<ReporteErro> listarFila(
            @Param("status") StatusReporte status,
            @Param("motivo") MotivoReporte motivo,
            @Param("idQuestao") Integer idQuestao,
            Pageable pageable);

    @Query("""
            select r.questao.id as idQuestao, count(r) as total
            from ReporteErro r
            where r.questao.id in :idsQuestao
              and r.status = br.com.treinoconcursos.model.StatusReporte.ABERTO
              and r.motivo = br.com.treinoconcursos.model.MotivoReporte.GABARITO_ERRADO
            group by r.questao.id
            """)
    List<ContagemPorQuestao> contarGabaritoErradoAbertosPorQuestoes(
            @Param("idsQuestao") Collection<Integer> idsQuestao);

    long countByQuestaoIdAndStatusAndMotivo(Integer idQuestao, StatusReporte status, MotivoReporte motivo);
}
