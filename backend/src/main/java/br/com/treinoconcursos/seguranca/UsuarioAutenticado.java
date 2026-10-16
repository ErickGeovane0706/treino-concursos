package br.com.treinoconcursos.seguranca;

import br.com.treinoconcursos.model.Perfil;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.Serializable;
import java.util.List;

/**
 * O que fica guardado na sessao depois do login: so o id e o perfil.
 * O UsuarioSessaoFilter confere esses dados no banco a cada requisicao.
 */
public record UsuarioAutenticado(Integer id, Perfil perfil) implements Serializable {

    public List<SimpleGrantedAuthority> autoridades() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + perfil.name()));
    }
}
