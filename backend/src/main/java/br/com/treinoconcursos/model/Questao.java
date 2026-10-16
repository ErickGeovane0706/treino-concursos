package br.com.treinoconcursos.model;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Questao do acervo (RF04, RF05). O id define a ordem fixa de navegacao
 * (RF07 RN2). As regras (alternativas, ciclo de vida) ficam no service; o
 * banco garante as principais com CHECKs e o indice unico parcial.
 */
@Entity
@Table(name = "questoes")
@Getter
@Setter
@NoArgsConstructor
public class Questao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_questao")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_materia", nullable = false)
    private Materia materia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_assunto")
    private Assunto assunto;

    /** Obrigatoria se e somente se a origem for PROVA (RF04 RN3). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_prova")
    private Prova prova;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_autor", nullable = false)
    private Usuario autor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoQuestao tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrigemQuestao origem;

    /** Markdown cru; so vira HTML pelo MarkdownService. */
    @Column(nullable = false, columnDefinition = "text")
    private String enunciado;

    /** Markdown cru; obrigatorio para publicar (RF04 RN1). */
    @Column(columnDefinition = "text")
    private String comentario;

    @Column(nullable = false, length = 120)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusQuestao status = StatusQuestao.RASCUNHO;

    @Column(nullable = false)
    private boolean anulada;

    @Column(nullable = false)
    private boolean desatualizada;

    /**
     * Contadores de RF17: so mudam por UPDATE ... SET x = x + 1 no repositorio.
     * Nunca sao gravados pela entidade, para um save nao sobrescrever
     * incrementos feitos por outras requisicoes.
     */
    @Column(name = "total_respostas", nullable = false, insertable = false, updatable = false)
    private int totalRespostas;

    @Column(name = "total_acertos", nullable = false, insertable = false, updatable = false)
    private int totalAcertos;

    @Generated(event = EventType.INSERT)
    @Column(name = "criado_em", nullable = false, insertable = false, updatable = false)
    private LocalDateTime criadoEm;

    /** Preenchido pelo banco no INSERT; nas alteracoes, o service atualiza (lastmod do sitemap). */
    @Generated(event = EventType.INSERT)
    @Column(name = "atualizado_em", nullable = false, insertable = false)
    private LocalDateTime atualizadoEm;

    @Column(name = "publicado_em")
    private LocalDateTime publicadoEm;

    @OneToMany(mappedBy = "questao", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("letra")
    private List<Alternativa> alternativas = new ArrayList<>();

    public void adicionarAlternativa(Alternativa alternativa) {
        alternativa.setQuestao(this);
        alternativas.add(alternativa);
    }
}
