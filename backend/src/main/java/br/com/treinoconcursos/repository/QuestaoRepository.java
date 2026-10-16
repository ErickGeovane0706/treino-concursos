package br.com.treinoconcursos.repository;

import br.com.treinoconcursos.model.Questao;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestaoRepository extends JpaRepository<Questao, Integer> {
}
