package br.com.treinoconcursos.service;

import br.com.treinoconcursos.dto.UsuarioAtualizarRequest;
import br.com.treinoconcursos.dto.UsuarioRequest;
import br.com.treinoconcursos.dto.UsuarioResponse;
import br.com.treinoconcursos.exception.RecursoNaoEncontradoException;
import br.com.treinoconcursos.exception.RegraNegocioException;
import br.com.treinoconcursos.model.Perfil;
import br.com.treinoconcursos.model.Usuario;
import br.com.treinoconcursos.repository.UsuarioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manutencao dos usuarios do painel pelo ADMIN (US13, RF01 RN1, RN2 e RN4).
 */
@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public Page<UsuarioResponse> listar(Pageable pageable) {
        return usuarioRepository.findAll(pageable).map(UsuarioResponse::de);
    }

    @Transactional(readOnly = true)
    public UsuarioResponse buscar(Integer id) {
        return UsuarioResponse.de(buscarEntidade(id));
    }

    @Transactional
    public UsuarioResponse criar(UsuarioRequest request) {
        String email = AuthService.normalizarEmail(request.email());
        if (usuarioRepository.existsByEmail(email)) {
            throw new RegraNegocioException("Já existe um usuário com este e-mail.");
        }
        Usuario usuario = new Usuario();
        usuario.setNome(request.nome().trim());
        usuario.setEmail(email);
        usuario.setSenhaHash(passwordEncoder.encode(request.senha()));
        usuario.setPerfil(request.perfil());
        return UsuarioResponse.de(usuarioRepository.saveAndFlush(usuario));
    }

    @Transactional
    public UsuarioResponse atualizar(Integer id, UsuarioAtualizarRequest request) {
        Usuario usuario = buscarEntidade(id);
        String email = AuthService.normalizarEmail(request.email());
        if (usuarioRepository.existsByEmailAndIdNot(email, id)) {
            throw new RegraNegocioException("Já existe um usuário com este e-mail.");
        }
        if (usuario.getPerfil() == Perfil.ADMIN && request.perfil() != Perfil.ADMIN) {
            garantirQueNaoEhOUltimoAdmin(usuario, "O último administrador ativo não pode ter o perfil rebaixado.");
        }
        usuario.setNome(request.nome().trim());
        usuario.setEmail(email);
        usuario.setPerfil(request.perfil());
        return UsuarioResponse.de(usuario);
    }

    @Transactional
    public UsuarioResponse inativar(Integer id) {
        Usuario usuario = buscarEntidade(id);
        if (usuario.getPerfil() == Perfil.ADMIN) {
            garantirQueNaoEhOUltimoAdmin(usuario, "O último administrador ativo não pode ser inativado.");
        }
        usuario.setAtivo(false);
        return UsuarioResponse.de(usuario);
    }

    @Transactional
    public UsuarioResponse reativar(Integer id) {
        Usuario usuario = buscarEntidade(id);
        usuario.setAtivo(true);
        return UsuarioResponse.de(usuario);
    }

    @Transactional
    public UsuarioResponse desbloquear(Integer id) {
        buscarEntidade(id);
        usuarioRepository.zerarTentativas(id);
        return UsuarioResponse.de(buscarEntidade(id));
    }

    @Transactional
    public UsuarioResponse redefinirSenha(Integer id, String novaSenha) {
        Usuario usuario = buscarEntidade(id);
        usuario.setSenhaHash(passwordEncoder.encode(novaSenha));
        return UsuarioResponse.de(usuario);
    }

    private Usuario buscarEntidade(Integer id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
    }

    /** RF01 RN4. Trava os ADMIN ativos para dois pedidos simultaneos nao passarem juntos. */
    private void garantirQueNaoEhOUltimoAdmin(Usuario usuario, String mensagem) {
        if (!usuario.isAtivo()) {
            return;
        }
        boolean outroAdminAtivo = usuarioRepository.travarAdminsAtivos().stream()
                .anyMatch(admin -> !admin.getId().equals(usuario.getId()));
        if (!outroAdminAtivo) {
            throw new RegraNegocioException(mensagem);
        }
    }
}
