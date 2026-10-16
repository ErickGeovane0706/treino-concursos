package br.com.treinoconcursos.service;

import br.com.treinoconcursos.exception.ContaBloqueadaException;
import br.com.treinoconcursos.exception.CredenciaisInvalidasException;
import br.com.treinoconcursos.model.Usuario;
import br.com.treinoconcursos.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Confere e-mail e senha do painel (RF01 RN1 e RN3).
 */
@Service
public class AuthService {

    /** RF01 RN3: 5 senhas erradas seguidas bloqueiam a conta por 15 minutos. */
    static final int LIMITE_TENTATIVAS = 5;
    static final Duration TEMPO_BLOQUEIO = Duration.ofMinutes(15);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock relogio;

    /**
     * Hash usado quando o e-mail nao existe, para a resposta demorar o mesmo
     * tempo e nao revelar quais e-mails tem conta.
     */
    private final String hashFalso;

    public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, Clock relogio) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.relogio = relogio;
        this.hashFalso = passwordEncoder.encode("senha-que-nunca-confere");
    }

    /**
     * Devolve o usuario se as credenciais conferem. As excecoes nao desfazem a
     * transacao: a senha errada precisa ficar contada.
     *
     * @throws CredenciaisInvalidasException e-mail inexistente, senha errada ou conta inativa (401)
     * @throws ContaBloqueadaException       conta bloqueada agora ou nesta tentativa (423)
     */
    @Transactional(noRollbackFor = {CredenciaisInvalidasException.class, ContaBloqueadaException.class})
    public Usuario autenticar(String email, String senha) {
        Usuario usuario = usuarioRepository.findByEmail(normalizarEmail(email)).orElse(null);
        if (usuario == null) {
            passwordEncoder.matches(senha, hashFalso);
            throw new CredenciaisInvalidasException();
        }

        LocalDateTime agora = LocalDateTime.now(relogio);
        if (estaBloqueado(usuario, agora)) {
            throw contaBloqueada(usuario.getBloqueadoAte(), agora);
        }

        boolean senhaConfere = passwordEncoder.matches(senha, usuario.getSenhaHash());
        if (!usuario.isAtivo()) {
            throw new CredenciaisInvalidasException();
        }
        if (!senhaConfere) {
            usuarioRepository.registrarSenhaErrada(usuario.getId(), LIMITE_TENTATIVAS, agora.plus(TEMPO_BLOQUEIO));
            Usuario atualizado = usuarioRepository.findById(usuario.getId()).orElseThrow();
            if (estaBloqueado(atualizado, agora)) {
                throw contaBloqueada(atualizado.getBloqueadoAte(), agora);
            }
            throw new CredenciaisInvalidasException();
        }

        if (usuario.getTentativasLogin() > 0 || usuario.getBloqueadoAte() != null) {
            usuarioRepository.zerarTentativas(usuario.getId());
        }
        return usuario;
    }

    static String normalizarEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean estaBloqueado(Usuario usuario, LocalDateTime agora) {
        return usuario.getBloqueadoAte() != null && usuario.getBloqueadoAte().isAfter(agora);
    }

    private static ContaBloqueadaException contaBloqueada(LocalDateTime bloqueadoAte, LocalDateTime agora) {
        long minutos = Math.max(1, (long) Math.ceil(Duration.between(agora, bloqueadoAte).toSeconds() / 60.0));
        String unidade = minutos == 1 ? " minuto." : " minutos.";
        return new ContaBloqueadaException(
                "Conta bloqueada por excesso de tentativas. Tente novamente em " + minutos + unidade);
    }
}
