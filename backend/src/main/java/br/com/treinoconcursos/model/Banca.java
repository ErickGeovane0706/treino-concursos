package br.com.treinoconcursos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Banca organizadora de provas (RF03). */
@Entity
@Table(name = "bancas")
@Getter
@Setter
@NoArgsConstructor
public class Banca {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_banca")
    private Integer id;

    @Column(nullable = false, length = 100, unique = true)
    private String nome;

    @Column(nullable = false, length = 30, unique = true)
    private String sigla;

    @Column(nullable = false, length = 120, unique = true)
    private String slug;
}
