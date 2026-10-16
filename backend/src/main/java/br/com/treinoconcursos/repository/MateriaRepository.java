package br.com.treinoconcursos.repository;

import br.com.treinoconcursos.model.Materia;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MateriaRepository extends JpaRepository<Materia, Integer> {
}
