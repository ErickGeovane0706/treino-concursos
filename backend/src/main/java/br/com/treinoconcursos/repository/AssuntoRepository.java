package br.com.treinoconcursos.repository;

import br.com.treinoconcursos.model.Assunto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssuntoRepository extends JpaRepository<Assunto, Integer> {
}
