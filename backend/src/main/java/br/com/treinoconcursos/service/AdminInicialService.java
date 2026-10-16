package br.com.treinoconcursos.service;

import br.com.treinoconcursos.model.Perfil;
import br.com.treinoconcursos.model.Usuario;
import br.com.treinoconcursos.repository.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF01 RN5: na subida, se nao ha nenhum ADMIN, cria o primeiro com
 * ADMIN_INICIAL_EMAIL e ADMIN_INICIAL_SENHA (do .env, nunca do codigo).
 *
 * Olha so para ADMIN (e nao para "nenhum usuario") porque o seed de
 * desenvolvimento cria um autor EDITOR inativo antes desta etapa.
 */
@Slf4j
@Service
public class AdminInicialService implements ApplicationRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String senha;

    public AdminInicialService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
            @Value("${app.admin-inicial.email:}") String email,
            @Value("${app.admin-inicial.senha:}") String senha) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.senha = senha;
    }

    @Override
    public void run(ApplicationArguments args) {
        criarSeNaoHouverUsuarios();
    }

    /** @return true se criou o administrador */
    @Transactional
    public boolean criarSeNaoHouverUsuarios() {
        if (usuarioRepository.existsByPerfil(Perfil.ADMIN)) {
            return false;
        }
        if (email.isBlank() || senha.isBlank()) {
            log.warn("Nenhum usuário no painel e ADMIN_INICIAL_EMAIL/ADMIN_INICIAL_SENHA não definidos no .env.");
            return false;
        }
        if (senha.length() < 8 || senha.length() > 72) {
            throw new IllegalStateException("ADMIN_INICIAL_SENHA deve ter de 8 a 72 caracteres.");
        }
        Usuario admin = new Usuario();
        admin.setNome("Administrador");
        admin.setEmail(AuthService.normalizarEmail(email));
        admin.setSenhaHash(passwordEncoder.encode(senha));
        admin.setPerfil(Perfil.ADMIN);
        usuarioRepository.save(admin);
        log.info("Primeiro administrador criado a partir de ADMIN_INICIAL_EMAIL.");
        return true;
    }
}
