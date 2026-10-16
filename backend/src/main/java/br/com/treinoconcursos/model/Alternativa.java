package br.com.treinoconcursos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Alternativa de uma questao. Letras A a E; Certo/Errado usa C e E (RF04 RN2).
 * O banco aceita no maximo uma correta por questao (indice unico parcial).
 */
@Entity
@Table(name = "alternativas")
@Getter
@Setter
@NoArgsConstructor
public class Alternativa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_alternativa")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_questao", nullable = false)
    private Questao questao;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 1)
    private String letra;

    /** Markdown cru. */
    @Column(nullable = false, columnDefinition = "text")
    private String texto;

    @Column(nullable = false)
    private boolean correta;

    /** Contador de RF17: so muda por UPDATE ... SET x = x + 1. */
    @Column(name = "total_marcacoes", nullable = false, insertable = false, updatable = false)
    private int totalMarcacoes;

    public Alternativa(String letra, String texto, boolean correta) {
        this.letra = letra;
        this.texto = texto;
        this.correta = correta;
    }
}
