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
}
