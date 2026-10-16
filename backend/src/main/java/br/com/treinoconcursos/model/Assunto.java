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

/**
 * Assunto de uma materia, opcionalmente filho de outro assunto (RF02 RN2).
 * Profundidade maxima e pai da mesma materia sao validados no service.
 */
@Entity
@Table(name = "assuntos")
@Getter
@Setter
@NoArgsConstructor
public class Assunto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_assunto")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_materia", nullable = false)
    private Materia materia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_assunto_pai")
    private Assunto assuntoPai;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(nullable = false, length = 170)
    private String slug;

    @Column(nullable = false)
    private boolean ativo = true;
}
