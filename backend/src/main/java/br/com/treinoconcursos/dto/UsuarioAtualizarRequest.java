package br.com.treinoconcursos.dto;

import br.com.treinoconcursos.model.Perfil;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Alteracao de dados e perfil. A senha muda so por /redefinir-senha. */
public record UsuarioAtualizarRequest(
        @NotBlank @Size(max = 100) String nome,
        @NotBlank @Email @Size(max = 150) String email,
        @NotNull Perfil perfil) {
}
