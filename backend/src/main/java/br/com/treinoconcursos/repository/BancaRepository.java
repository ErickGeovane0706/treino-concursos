package br.com.treinoconcursos.repository;

import br.com.treinoconcursos.model.Banca;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BancaRepository extends JpaRepository<Banca, Integer> {

    List<Banca> findAllByOrderBySiglaAsc();

    Page<Banca> findByNomeContainingIgnoreCaseOrSiglaContainingIgnoreCase(String nome, String sigla,
            Pageable pageable);

    boolean existsByNomeIgnoreCase(String nome);

    boolean existsByNomeIgnoreCaseAndIdNot(String nome, Integer id);

    boolean existsBySiglaIgnoreCase(String sigla);

    boolean existsBySiglaIgnoreCaseAndIdNot(String sigla, Integer id);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Integer id);
}
