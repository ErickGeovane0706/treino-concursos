package br.com.treinoconcursos.repository;

import br.com.treinoconcursos.model.Questao;
import br.com.treinoconcursos.model.StatusQuestao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface QuestaoRepository extends JpaRepository<Questao, Integer> {

    boolean existsByMateriaId(Integer idMateria);

    /** RF02 RN4: ja publicada alguma vez (o endereco pode estar indexado). */
    boolean existsByMateriaIdAndPublicadoEmIsNotNull(Integer idMateria);

    boolean existsByAssuntoId(Integer idAssunto);

    boolean existsByAssuntoIdAndPublicadoEmIsNotNull(Integer idAssunto);

    boolean existsByProvaId(Integer idProva);

    /** Carrega a questao com todas as associacoes usadas na pagina publica (open-in-view=false). */
    @EntityGraph(attributePaths = {"materia", "assunto", "prova", "prova.banca", "prova.orgao", "alternativas"})
    Optional<Questao> findComDetalhesById(Integer id);

    /** RF06 RN3 / RF08 RN4: proxima e anterior publicadas em ordem crescente de id. */
    @Query("""
            select q from Questao q
            where q.status = br.com.treinoconcursos.model.StatusQuestao.PUBLICADA
              and q.id > :id
            order by q.id asc
            limit 1
            """)
    Optional<Questao> proximaPublicada(@Param("id") Integer id);

    @Query("""
            select q from Questao q
            where q.status = br.com.treinoconcursos.model.StatusQuestao.PUBLICADA
              and q.id < :id
            order by q.id desc
            limit 1
            """)
    Optional<Questao> anteriorPublicada(@Param("id") Integer id);

    @Query("""
            select q from Questao q
            where q.status = br.com.treinoconcursos.model.StatusQuestao.PUBLICADA
              and q.materia.id = :idMateria
              and q.id > :id
            order by q.id asc
            limit 1
            """)
    Optional<Questao> proximaPublicadaPorMateria(@Param("idMateria") Integer idMateria, @Param("id") Integer id);

    @Query("""
            select q from Questao q
            where q.status = br.com.treinoconcursos.model.StatusQuestao.PUBLICADA
              and q.materia.id = :idMateria
              and q.id < :id
            order by q.id desc
            limit 1
            """)
    Optional<Questao> anteriorPublicadaPorMateria(@Param("idMateria") Integer idMateria, @Param("id") Integer id);

    @Query("""
            select q from Questao q
            where q.status = br.com.treinoconcursos.model.StatusQuestao.PUBLICADA
              and q.assunto.id = :idAssunto
              and q.id > :id
            order by q.id asc
            limit 1
            """)
    Optional<Questao> proximaPublicadaPorAssunto(@Param("idAssunto") Integer idAssunto, @Param("id") Integer id);

    @Query("""
            select q from Questao q
            where q.status = br.com.treinoconcursos.model.StatusQuestao.PUBLICADA
              and q.assunto.id = :idAssunto
              and q.id < :id
            order by q.id desc
            limit 1
            """)
    Optional<Questao> anteriorPublicadaPorAssunto(@Param("idAssunto") Integer idAssunto, @Param("id") Integer id);

    @Query("""
            select q from Questao q
            where q.status = br.com.treinoconcursos.model.StatusQuestao.PUBLICADA
              and q.assunto.id in :idsAssunto
              and q.id > :id
            order by q.id asc
            limit 1
            """)
    Optional<Questao> proximaPublicadaPorAssuntos(@Param("idsAssunto") Collection<Integer> idsAssunto,
            @Param("id") Integer id);

    @Query("""
            select q from Questao q
            where q.status = br.com.treinoconcursos.model.StatusQuestao.PUBLICADA
              and q.assunto.id in :idsAssunto
              and q.id < :id
            order by q.id desc
            limit 1
            """)
    Optional<Questao> anteriorPublicadaPorAssuntos(@Param("idsAssunto") Collection<Integer> idsAssunto,
            @Param("id") Integer id);

    /** RF07 RN2-RN3: listagem publica por cursor em ordem crescente de id. */
    @EntityGraph(attributePaths = {"materia", "assunto", "prova", "prova.banca", "prova.orgao"})
    @Query("""
            select q from Questao q
            where q.status = br.com.treinoconcursos.model.StatusQuestao.PUBLICADA
              and q.id > :depoisDe
            order by q.id asc
            """)
    java.util.List<Questao> listarPublicadasDepoisDe(@Param("depoisDe") Integer depoisDe, Pageable pageable);

    @EntityGraph(attributePaths = {"materia", "assunto", "prova", "prova.banca", "prova.orgao"})
    @Query("""
            select q from Questao q
            where q.status = br.com.treinoconcursos.model.StatusQuestao.PUBLICADA
              and q.materia.id = :idMateria
              and q.id > :depoisDe
            order by q.id asc
            """)
    java.util.List<Questao> listarPublicadasPorMateriaDepoisDe(@Param("idMateria") Integer idMateria,
            @Param("depoisDe") Integer depoisDe, Pageable pageable);

    @EntityGraph(attributePaths = {"materia", "assunto", "prova", "prova.banca", "prova.orgao"})
    @Query("""
            select q from Questao q
            where q.status = br.com.treinoconcursos.model.StatusQuestao.PUBLICADA
              and q.assunto.id = :idAssunto
              and q.id > :depoisDe
            order by q.id asc
            """)
    java.util.List<Questao> listarPublicadasPorAssuntoDepoisDe(@Param("idAssunto") Integer idAssunto,
            @Param("depoisDe") Integer depoisDe, Pageable pageable);

    @EntityGraph(attributePaths = {"materia", "assunto", "prova", "prova.banca", "prova.orgao"})
    @Query("""
            select q from Questao q
            where q.status = br.com.treinoconcursos.model.StatusQuestao.PUBLICADA
              and q.assunto.id in :idsAssunto
              and q.id > :depoisDe
            order by q.id asc
            """)
    java.util.List<Questao> listarPublicadasPorAssuntosDepoisDe(@Param("idsAssunto") Collection<Integer> idsAssunto,
            @Param("depoisDe") Integer depoisDe, Pageable pageable);

    /** RF11 RN1-RN2, US04: dados minimos das questoes publicadas para o caderno de erros em ordem crescente de id. */
    @EntityGraph(attributePaths = {"materia", "assunto", "prova", "prova.banca", "prova.orgao"})
    @Query("""
            select q from Questao q
            where q.status = br.com.treinoconcursos.model.StatusQuestao.PUBLICADA
              and q.id in :ids
            order by q.id asc
            """)
    java.util.List<Questao> listarPublicadasPorIds(@Param("ids") Collection<Integer> ids);

    /** RF07 / RF10: apenas os ids publicados do filtro em ordem crescente (GET /api/questoes/ids). */
    @Query("""
            select q.id from Questao q
            where q.status = br.com.treinoconcursos.model.StatusQuestao.PUBLICADA
              and q.id > :depoisDe
            order by q.id asc
            """)
    java.util.List<Integer> buscarIdsPublicadasDepoisDe(@Param("depoisDe") Integer depoisDe, Pageable pageable);

    @Query("""
            select q.id from Questao q
            where q.status = br.com.treinoconcursos.model.StatusQuestao.PUBLICADA
              and q.materia.id = :idMateria
              and q.id > :depoisDe
            order by q.id asc
            """)
    java.util.List<Integer> buscarIdsPublicadasPorMateriaDepoisDe(@Param("idMateria") Integer idMateria,
            @Param("depoisDe") Integer depoisDe, Pageable pageable);

    @Query("""
            select q.id from Questao q
            where q.status = br.com.treinoconcursos.model.StatusQuestao.PUBLICADA
              and q.assunto.id = :idAssunto
              and q.id > :depoisDe
            order by q.id asc
            """)
    java.util.List<Integer> buscarIdsPublicadasPorAssuntoDepoisDe(@Param("idAssunto") Integer idAssunto,
            @Param("depoisDe") Integer depoisDe, Pageable pageable);

    @Query("""
            select q.id from Questao q
            where q.status = br.com.treinoconcursos.model.StatusQuestao.PUBLICADA
              and q.assunto.id in :idsAssunto
              and q.id > :depoisDe
            order by q.id asc
            """)
    java.util.List<Integer> buscarIdsPublicadasPorAssuntosDepoisDe(@Param("idsAssunto") Collection<Integer> idsAssunto,
            @Param("depoisDe") Integer depoisDe, Pageable pageable);

    /** Lista do painel: filtros opcionais (null ou busca vazia = sem filtro); busca no enunciado. */
    @EntityGraph(attributePaths = "materia")
    @Query("""
            select q from Questao q
            where (:status is null or q.status = :status)
              and (:idMateria is null or q.materia.id = :idMateria)
              and (:busca = '' or lower(q.enunciado) like lower(concat('%', :busca, '%')))
            """)
    Page<Questao> buscar(@Param("status") StatusQuestao status, @Param("idMateria") Integer idMateria,
            @Param("busca") String busca, Pageable pageable);

    /** RF04 RN5: gabarito mudou, as estatisticas (RF17) deixam de valer. */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE questoes SET total_respostas = 0, total_acertos = 0 WHERE id_questao = :id",
            nativeQuery = true)
    void zerarTotais(@Param("id") Integer idQuestao);

    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE alternativas SET total_marcacoes = 0 WHERE id_questao = :id", nativeQuery = true)
    void zerarMarcacoes(@Param("id") Integer idQuestao);

    @Query("select q.status from Questao q where q.id = :id")
    Optional<StatusQuestao> buscarStatusPorId(@Param("id") Integer id);

    @EntityGraph(attributePaths = "alternativas")
    Optional<Questao> findComAlternativasById(Integer id);

    /**
     * RF17 RN1: incremento atomico dos contadores de respostas e acertos.
     * Nunca ler, somar e salvar pela entidade.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE questoes
            SET total_respostas = total_respostas + 1,
                total_acertos   = total_acertos + CASE WHEN :correta THEN 1 ELSE 0 END
            WHERE id_questao = :id AND status = 'PUBLICADA'
            """, nativeQuery = true)
    int incrementarRespostas(@Param("id") Integer idQuestao, @Param("correta") boolean correta);
}
