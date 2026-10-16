package br.com.treinoconcursos.dto;

import br.com.treinoconcursos.model.Perfil;
import br.com.treinoconcursos.model.Usuario;

import java.time.LocalDateTime;

/** Dados publicos de um usuario do painel. Nunca inclui o hash da senha. */
public record UsuarioResponse(
        Integer id,
        String nome,
        String email,
        Perfil perfil,
        boolean ativo,
        LocalDateTime bloqueadoAte,
        LocalDateTime criadoEm) {

    public static UsuarioResponse de(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getPerfil(),
                usuario.isAtivo(), usuario.getBloqueadoAte(), usuario.getCriadoEm());
    }
}
