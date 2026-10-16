package br.com.treinoconcursos.repository;

import br.com.treinoconcursos.model.Perfil;
import br.com.treinoconcursos.model.Usuario;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByPerfil(Perfil perfil);

    boolean existsByEmailAndIdNot(String email, Integer id);

    /**
     * Trava (SELECT ... FOR UPDATE) os ADMIN ativos. Usado antes de inativar ou
     * rebaixar alguem, para dois pedidos simultaneos nao deixarem o painel sem
     * nenhum ADMIN (RF01 RN4).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.perfil = br.com.treinoconcursos.model.Perfil.ADMIN and u.ativo = true")
    List<Usuario> travarAdminsAtivos();

    /**
     * Conta uma senha errada de forma atomica (RF01 RN3). Na tentativa de numero
     * {@code limite} a conta fica bloqueada ate {@code bloquearAte} e o contador
     * volta a zero, para valer de novo depois do bloqueio.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE usuarios SET
                bloqueado_ate    = CASE WHEN tentativas_login + 1 >= :limite THEN :bloquearAte ELSE bloqueado_ate END,
                tentativas_login = CASE WHEN tentativas_login + 1 >= :limite THEN 0 ELSE tentativas_login + 1 END
            WHERE id_usuario = :id
            """, nativeQuery = true)
    void registrarSenhaErrada(@Param("id") Integer id, @Param("limite") int limite,
            @Param("bloquearAte") LocalDateTime bloquearAte);

    /** Login correto ou desbloqueio pelo ADMIN: zera o contador e o bloqueio. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Usuario u set u.tentativasLogin = 0, u.bloqueadoAte = null where u.id = :id")
    void zerarTentativas(@Param("id") Integer id);
}
