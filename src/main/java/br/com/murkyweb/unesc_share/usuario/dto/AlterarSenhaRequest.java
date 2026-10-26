package br.com.murkyweb.unesc_share.usuario.dto;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AlterarSenhaRequest(
		@NotBlank @Size(max = 64) String senhaAtual,
		@NotBlank @Size(min = 8, max = 64) String novaSenha) {

	@JsonIgnore
	@AssertTrue(message = "A nova senha deve ter no maximo 72 bytes em UTF-8 para BCrypt.")
	public boolean isNovaSenhaCompativelComBCrypt() {
		return novaSenha == null || novaSenha.getBytes(StandardCharsets.UTF_8).length <= 72;
	}

	@JsonIgnore
	@AssertTrue(message = "A nova senha deve ser diferente da senha atual.")
	public boolean isNovaSenhaDiferente() {
		return senhaAtual == null || novaSenha == null || !senhaAtual.equals(novaSenha);
	}

	@Override
	public String toString() {
		return "AlterarSenhaRequest[senhaAtual=REDACTED, novaSenha=REDACTED]";
	}
}
