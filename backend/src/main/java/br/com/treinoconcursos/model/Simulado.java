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
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.LocalDateTime;

/**
 * Simulado montado no painel (RF14, US11). As questoes e a ordem ficam em
 * {@link SimuladoQuestao}; as regras (RN1-RN4) ficam no SimuladoService.
 */
@Entity
@Table(name = "simulados")
@Getter
@Setter
@NoArgsConstructor
public class Simulado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_simulado")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_autor", nullable = false)
    private Usuario autor;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(nullable = false, length = 150)
    private String slug;

    /** Texto simples de apresentacao. */
    @Column(columnDefinition = "text")
    private String descricao;

    /** Opcional: com valor, a pagina do simulado mostra um cronometro (RF13 RN5). */
    @Column(name = "tempo_sugerido_min")
    private Integer tempoSugeridoMin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusSimulado status = StatusSimulado.RASCUNHO;

    /** RF14 RN4: alguma questao deixou de ser elegivel depois da publicacao. */
    @Column(name = "precisa_revisao", nullable = false)
    private boolean precisaRevisao;

    @Generated(event = EventType.INSERT)
    @Column(name = "criado_em", nullable = false, insertable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "publicado_em")
    private LocalDateTime publicadoEm;
}
