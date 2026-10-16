package br.com.treinoconcursos.service;

import br.com.treinoconcursos.IntegracaoTest;
import br.com.treinoconcursos.model.Perfil;
import br.com.treinoconcursos.model.Usuario;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RF01 RN5: primeiro ADMIN criado a partir das variaveis de ambiente quando
 * nao existe nenhum ADMIN.
 */
class AdminInicialServiceTest extends IntegracaoTest {

    private AdminInicialService servico(String email, String senha) {
        return new AdminInicialService(usuarioRepository, passwordEncoder, email, senha);
    }

    @Test
    void criaOPrimeiroAdminQuandoNaoHaUsuarios() {
        boolean criou = servico(" Dono@Exemplo.com ", "senha-inicial-forte").criarSeNaoHouverUsuarios();

        assertThat(criou).isTrue();
        Usuario admin = usuarioRepository.findByEmail("dono@exemplo.com").orElseThrow();
        assertThat(admin.getPerfil()).isEqualTo(Perfil.ADMIN);
        assertThat(admin.isAtivo()).isTrue();
        assertThat(passwordEncoder.matches("senha-inicial-forte", admin.getSenhaHash())).isTrue();
    }

    @Test
    void naoCriaQuandoJaExisteAdmin() {
        criarUsuario("admin@exemplo.com", Perfil.ADMIN);

        boolean criou = servico("dono@exemplo.com", "senha-inicial-forte").criarSeNaoHouverUsuarios();

        assertThat(criou).isFalse();
        assertThat(usuarioRepository.count()).isEqualTo(1);
    }

    /** O autor inativo do seed de desenvolvimento nao impede o primeiro ADMIN. */
    @Test
    void criaMesmoQueJaExistaUmEditor() {
        criarUsuario("editor@exemplo.com", Perfil.EDITOR);

        assertThat(servico("dono@exemplo.com", "senha-inicial-forte").criarSeNaoHouverUsuarios()).isTrue();
    }

    @Test
    void semVariaveisApenasAvisa() {
        assertThat(servico("", "").criarSeNaoHouverUsuarios()).isFalse();
        assertThat(usuarioRepository.count()).isZero();
    }

    @Test
    void senhaInicialCurtaImpedeASubida() {
        assertThatThrownBy(() -> servico("dono@exemplo.com", "curta").criarSeNaoHouverUsuarios())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_INICIAL_SENHA");
    }
}
