package br.com.murkyweb.unesc_share.categoria.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoriaRequest(
		@NotBlank @Size(max = 80) String nome,
		@Size(max = 500) String descricao) {

	public CategoriaRequest {
		nome = nome == null ? null : nome.strip();
		descricao = descricao == null || descricao.isBlank() ? null : descricao.strip();
	}
}
