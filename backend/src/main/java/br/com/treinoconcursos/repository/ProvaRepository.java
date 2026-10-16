package br.com.treinoconcursos.repository;

import br.com.treinoconcursos.model.Prova;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProvaRepository extends JpaRepository<Prova, Integer> {
}
