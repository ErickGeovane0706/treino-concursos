package br.com.treinoconcursos.repository;

import br.com.treinoconcursos.model.Alternativa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AlternativaRepository extends JpaRepository<Alternativa, Integer> {

    /** Gabarito de varias questoes de uma vez (resultado do simulado, RF13 RN3). Linhas: [id_questao, letra]. */
    @Query("select a.questao.id, a.letra from Alternativa a where a.correta = true and a.questao.id in :ids")
    List<Object[]> buscarLetrasCorretas(@Param("ids") Collection<Integer> ids);

    @Query("select a.correta from Alternativa a where a.questao.id = :idQuestao and a.letra = :letra")
    Optional<Boolean> buscarCorretaPorQuestaoELetra(@Param("idQuestao") Integer idQuestao,
            @Param("letra") String letra);

    /**
     * RF17 RN1: incremento atomico do contador da alternativa marcada.
     * Nunca ler, somar e salvar pela entidade.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE alternativas
            SET total_marcacoes = total_marcacoes + 1
            WHERE id_questao = :id AND letra = :letra
            """, nativeQuery = true)
    int incrementarMarcacoes(@Param("id") Integer idQuestao, @Param("letra") String letra);
}
