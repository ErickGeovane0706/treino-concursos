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

import java.time.LocalDateTime;

/**
 * Reporte de erro em questao (tabela reportes_erro, V4__reportes.sql, RF16).
 *
 * Nenhum dado pessoal ou endereco IP do visitante e armazenado (RF16 RN2, RNF08).
 */
@Entity
@Table(name = "reportes_erro")
@Getter
@Setter
@NoArgsConstructor
public class ReporteErro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_reporte")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_questao", nullable = false)
    private Questao questao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MotivoReporte motivo;

    @Column(columnDefinition = "text")
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusReporte status = StatusReporte.ABERTO;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_resolvido_por")
    private Usuario resolvidoPor;

    @Column(name = "resolvido_em")
    private LocalDateTime resolvidoEm;

    @Column(name = "observacao_resolucao", columnDefinition = "text")
    private String observacaoResolucao;
}
