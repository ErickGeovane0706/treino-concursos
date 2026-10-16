package br.com.treinoconcursos.repository;

import br.com.treinoconcursos.model.Questao;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestaoRepository extends JpaRepository<Questao, Integer> {

    boolean existsByMateriaId(Integer idMateria);

    /** RF02 RN4: ja publicada alguma vez (o endereco pode estar indexado). */
    boolean existsByMateriaIdAndPublicadoEmIsNotNull(Integer idMateria);

    boolean existsByAssuntoId(Integer idAssunto);

    boolean existsByAssuntoIdAndPublicadoEmIsNotNull(Integer idAssunto);

    boolean existsByProvaId(Integer idProva);
}
