package br.com.murkyweb.unesc_share.usuario.dto;

import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PerfilAtualizacaoRequest(
		@NotBlank @Size(max = 120) String nome,
		@NotBlank @Email @Size(max = 254) String email,
		@Size(max = 64) String senhaAtual) {

	public PerfilAtualizacaoRequest {
		nome = nome == null ? null : nome.strip();
		email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
	}

	@Override
	public String toString() {
		return "PerfilAtualizacaoRequest[nome=%s, email=%s, senhaAtual=REDACTED]".formatted(nome, email);
	}
}
