package br.com.treinoconcursos.repository;

import br.com.treinoconcursos.model.Banca;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BancaRepository extends JpaRepository<Banca, Integer> {
}
