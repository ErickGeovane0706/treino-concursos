package br.com.treinoconcursos.repository;

import br.com.treinoconcursos.dto.SimuladoResumoResponse;
import br.com.treinoconcursos.model.Simulado;
import br.com.treinoconcursos.model.StatusSimulado;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SimuladoRepository extends JpaRepository<Simulado, Integer> {

    boolean existsBySlug(String slug);

    Optional<Simulado> findBySlug(String slug);

    /** RF13: simulados do site, do mais recente para o mais antigo. */
    @Query("""
            select s from Simulado s
            where s.status = br.com.treinoconcursos.model.StatusSimulado.PUBLICADO
            order by s.publicadoEm desc, s.id desc
            """)
    List<Simulado> listarPublicados();

    boolean existsBySlugAndIdNot(String slug, Integer id);

    /** Lista do painel: filtros opcionais (null ou busca vazia = sem filtro); busca no titulo. */
    @Query(value = """
            select new br.com.treinoconcursos.dto.SimuladoResumoResponse(
                s.id, s.titulo, s.slug, s.status, s.precisaRevisao,
                (select count(sq) from SimuladoQuestao sq where sq.idSimulado = s.id), s.publicadoEm)
            from Simulado s
            where (:status is null or s.status = :status)
              and (:busca = '' or lower(s.titulo) like lower(concat('%', :busca, '%')))
            """,
            countQuery = """
            select count(s) from Simulado s
            where (:status is null or s.status = :status)
              and (:busca = '' or lower(s.titulo) like lower(concat('%', :busca, '%')))
            """)
    Page<SimuladoResumoResponse> buscar(@Param("status") StatusSimulado status, @Param("busca") String busca,
            Pageable pageable);

    /**
     * RF14 RN4: a questao deixou de ser elegivel (arquivada, anulada ou desatualizada),
     * entao os simulados publicados que a contem precisam de revisao.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE simulados SET precisa_revisao = TRUE
            WHERE status = 'PUBLICADO'
              AND id_simulado IN (SELECT id_simulado FROM simulado_questoes WHERE id_questao = :idQuestao)
            """, nativeQuery = true)
    int marcarRevisaoPorQuestao(@Param("idQuestao") Integer idQuestao);
}
