package br.com.murkyweb.unesc_share.material.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MaterialCadastroRequest(
		@NotBlank @Size(max = 200) String titulo,
		@NotBlank @Size(max = 4000) String descricao,
		@NotNull UUID disciplinaId,
		@NotNull UUID categoriaId,
		@NotNull @Size(min = 1, max = 10) List<@NotBlank @Size(max = 4000) String> magnetUris) {

	public MaterialCadastroRequest {
		titulo = titulo == null ? null : titulo.strip();
		descricao = descricao == null ? null : descricao.strip();
	}

	@Override public String toString() { return "MaterialCadastroRequest[magnetUris=REDACTED]"; }
}
