package br.com.murkyweb.unesc_share.usuario.dto;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CadastroRequest(
		@NotBlank @Size(max = 120) String nome,
		@NotBlank @Email @Size(max = 254) String email,
		@NotBlank @Size(min = 8, max = 64) String senha) {

	public CadastroRequest {
		nome = nome == null ? null : nome.strip();
		email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
	}

	@JsonIgnore
	@AssertTrue(message = "A senha deve ter no maximo 72 bytes em UTF-8 para BCrypt.")
	public boolean isSenhaCompativelComBCrypt() {
		return senha == null || senha.getBytes(StandardCharsets.UTF_8).length <= 72;
	}

	@Override
	public String toString() {
		return "CadastroRequest[senha=REDACTED]";
	}
}
