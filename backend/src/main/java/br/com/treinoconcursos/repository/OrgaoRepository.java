package br.com.treinoconcursos.repository;

import br.com.treinoconcursos.model.Orgao;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrgaoRepository extends JpaRepository<Orgao, Integer> {
}
