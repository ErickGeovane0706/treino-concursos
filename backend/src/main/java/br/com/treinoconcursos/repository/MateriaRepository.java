package br.com.treinoconcursos.repository;

import br.com.treinoconcursos.model.Materia;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MateriaRepository extends JpaRepository<Materia, Integer> {

    Page<Materia> findByNomeContainingIgnoreCase(String busca, Pageable pageable);

    Optional<Materia> findBySlug(String slug);

    boolean existsByNomeIgnoreCase(String nome);

    boolean existsByNomeIgnoreCaseAndIdNot(String nome, Integer id);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Integer id);
}
