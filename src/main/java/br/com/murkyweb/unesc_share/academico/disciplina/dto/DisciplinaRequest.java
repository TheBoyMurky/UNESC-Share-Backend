package br.com.murkyweb.unesc_share.academico.disciplina.dto;

import java.util.Locale;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DisciplinaRequest(
		@NotBlank @Size(max = 150) String nome,
		@Size(max = 1000) String descricao,
		@NotBlank @Size(max = 30) String codigo,
		@NotNull UUID cursoId) {

	public DisciplinaRequest {
		nome = nome == null ? null : nome.strip();
		descricao = descricao == null || descricao.isBlank() ? null : descricao.strip();
		codigo = codigo == null ? null : codigo.strip().toUpperCase(Locale.ROOT);
	}
}
