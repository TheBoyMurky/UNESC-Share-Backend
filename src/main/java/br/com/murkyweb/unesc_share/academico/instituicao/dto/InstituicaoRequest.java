package br.com.murkyweb.unesc_share.academico.instituicao.dto;

import java.util.Locale;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InstituicaoRequest(
		@NotBlank @Size(max = 180) String nome,
		@NotBlank @Size(max = 30) String sigla) {

	public InstituicaoRequest {
		nome = nome == null ? null : nome.strip();
		sigla = sigla == null ? null : sigla.strip().toUpperCase(Locale.ROOT);
	}
}
