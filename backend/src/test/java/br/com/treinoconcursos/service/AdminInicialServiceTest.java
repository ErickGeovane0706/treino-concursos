package br.com.treinoconcursos.service;

import br.com.treinoconcursos.IntegracaoTest;
import br.com.treinoconcursos.model.Perfil;
import br.com.treinoconcursos.model.Usuario;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RF01 RN5: primeiro ADMIN criado a partir das variaveis de ambiente.
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
    void naoCriaQuandoJaExisteUsuario() {
        criarUsuario("editor@exemplo.com", Perfil.EDITOR);

        boolean criou = servico("dono@exemplo.com", "senha-inicial-forte").criarSeNaoHouverUsuarios();

        assertThat(criou).isFalse();
        assertThat(usuarioRepository.count()).isEqualTo(1);
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
