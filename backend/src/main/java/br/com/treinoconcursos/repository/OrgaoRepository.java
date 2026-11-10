package br.com.treinoconcursos.repository;

import br.com.treinoconcursos.model.Orgao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrgaoRepository extends JpaRepository<Orgao, Integer> {

    List<Orgao> findAllByOrderBySiglaAsc();

    Page<Orgao> findByNomeContainingIgnoreCaseOrSiglaContainingIgnoreCase(String nome, String sigla,
            Pageable pageable);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Integer id);
}
