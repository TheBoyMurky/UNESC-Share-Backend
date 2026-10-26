package br.com.murkyweb.unesc_share.material.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MaterialEdicaoRequest(
		@NotBlank @Size(max = 200) String titulo,
		@NotBlank @Size(max = 4000) String descricao,
		@NotNull UUID disciplinaId,
		@NotNull UUID categoriaId) {

	public MaterialEdicaoRequest {
		titulo = titulo == null ? null : titulo.strip();
		descricao = descricao == null ? null : descricao.strip();
	}
}
