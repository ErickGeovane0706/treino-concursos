package br.com.treinoconcursos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** Prova de um concurso: banca + orgao + ano + cargo, unicos juntos (RF03 RN2). */
@Entity
@Table(name = "provas")
@Getter
@Setter
@NoArgsConstructor
public class Prova {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_prova")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_banca", nullable = false)
    private Banca banca;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_orgao", nullable = false)
    private Orgao orgao;

    @Column(nullable = false)
    private Integer ano;

    @Column(nullable = false, length = 150)
    private String cargo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NivelEscolaridade nivel;

    @Column(nullable = false, length = 200, unique = true)
    private String slug;
}
