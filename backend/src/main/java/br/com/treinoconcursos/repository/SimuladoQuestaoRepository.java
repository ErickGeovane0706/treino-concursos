package br.com.treinoconcursos.repository;

import br.com.treinoconcursos.model.SimuladoQuestao;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Questoes de cada simulado (RF14). A lista e sempre gravada inteira: apaga e insere. */
public interface SimuladoQuestaoRepository extends Repository<SimuladoQuestao, SimuladoQuestao.Chave> {

    @Query("""
            select sq from SimuladoQuestao sq
            join fetch sq.questao q
            join fetch q.materia
            where sq.idSimulado = :idSimulado
            order by sq.ordem
            """)
    List<SimuladoQuestao> listarPorSimulado(@Param("idSimulado") Integer idSimulado);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM simulado_questoes WHERE id_simulado = :idSimulado", nativeQuery = true)
    void apagarPorSimulado(@Param("idSimulado") Integer idSimulado);

    @Modifying
    @Query(value = """
            INSERT INTO simulado_questoes (id_simulado, id_questao, ordem)
            VALUES (:idSimulado, :idQuestao, :ordem)
            """, nativeQuery = true)
    void inserir(@Param("idSimulado") Integer idSimulado, @Param("idQuestao") Integer idQuestao,
            @Param("ordem") int ordem);
}
