package br.com.treinoconcursos.repository;

import br.com.treinoconcursos.model.Assunto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AssuntoRepository extends JpaRepository<Assunto, Integer> {

    List<Assunto> findByMateriaIdOrderByNome(Integer idMateria);

    boolean existsByMateriaIdAndSlug(Integer idMateria, String slug);

    boolean existsByMateriaIdAndSlugAndIdNot(Integer idMateria, String slug, Integer id);

    boolean existsByAssuntoPaiId(Integer idAssuntoPai);

    /** Usado ao excluir a materia: solta os subassuntos e depois apaga todos. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Assunto a set a.assuntoPai = null where a.materia.id = :idMateria")
    void soltarHierarquiaDaMateria(@Param("idMateria") Integer idMateria);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Assunto a where a.materia.id = :idMateria")
    void excluirDaMateria(@Param("idMateria") Integer idMateria);
}
