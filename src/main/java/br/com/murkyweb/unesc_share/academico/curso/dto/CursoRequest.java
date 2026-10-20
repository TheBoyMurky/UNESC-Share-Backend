package br.com.murkyweb.unesc_share.academico.curso.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CursoRequest(
		@NotBlank @Size(max = 150) String nome,
		@Size(max = 1000) String descricao,
		@NotNull UUID instituicaoId) {

	public CursoRequest {
		nome = nome == null ? null : nome.strip();
		descricao = descricao == null || descricao.isBlank() ? null : descricao.strip();
	}
}
