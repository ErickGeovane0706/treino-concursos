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
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Conta do painel (tabela usuarios, V1__usuarios.sql). As regras (bloqueio,
 * ultimo ADMIN) ficam nos services, nao aqui.
 */
@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Integer id;

    @Column(nullable = false, length = 100)
    private String nome;

    /** Sempre em minusculas (o banco tem CHECK). */
    @Column(nullable = false, length = 150, unique = true)
    private String email;

    @Column(name = "senha_hash", nullable = false, length = 100)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Perfil perfil;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "tentativas_login", nullable = false)
    private int tentativasLogin;

    @Column(name = "bloqueado_ate")
    private LocalDateTime bloqueadoAte;

    /** Preenchido pelo banco (DEFAULT now()); o Hibernate rele o valor apos o INSERT. */
    @Generated(event = EventType.INSERT)
    @Column(name = "criado_em", nullable = false, insertable = false, updatable = false)
    private LocalDateTime criadoEm;
}
