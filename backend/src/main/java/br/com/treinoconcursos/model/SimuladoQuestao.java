package br.com.treinoconcursos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Questao de um simulado, na ordem definida pelo editor (RF14 RN1). A PK
 * (simulado, questao) impede repeticao (RN2). So e lida por JPQL: as gravacoes
 * vao pelo repositorio, que apaga e insere a lista inteira de uma vez.
 */
@Entity
@Table(name = "simulado_questoes")
@IdClass(SimuladoQuestao.Chave.class)
@Getter
@NoArgsConstructor
public class SimuladoQuestao {

    @Id
    @Column(name = "id_simulado")
    private Integer idSimulado;

    @Id
    @Column(name = "id_questao")
    private Integer idQuestao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_questao", insertable = false, updatable = false)
    private Questao questao;

    @Column(nullable = false)
    private int ordem;

    /** Chave composta (id_simulado, id_questao). */
    public static class Chave implements Serializable {

        private Integer idSimulado;
        private Integer idQuestao;

        public Chave() {
        }

        public Chave(Integer idSimulado, Integer idQuestao) {
            this.idSimulado = idSimulado;
            this.idQuestao = idQuestao;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Chave c && java.util.Objects.equals(idSimulado, c.idSimulado)
                    && java.util.Objects.equals(idQuestao, c.idQuestao);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(idSimulado, idQuestao);
        }
    }
}
