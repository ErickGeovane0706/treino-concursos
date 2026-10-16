package br.com.treinoconcursos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Orgao que realiza o concurso (RF03). */
@Entity
@Table(name = "orgaos")
@Getter
@Setter
@NoArgsConstructor
public class Orgao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_orgao")
    private Integer id;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(nullable = false, length = 30)
    private String sigla;

    @Column(nullable = false, length = 120, unique = true)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EsferaOrgao esfera;

    /** Obrigatoria fora da esfera FEDERAL (CHECK no banco). */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 2)
    private String uf;
}
