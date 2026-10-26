package br.com.murkyweb.unesc_share.interacao.comentario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ComentarioModeracaoRequest(
		@NotBlank @Size(max = 1000)
		@Pattern(regexp = "[^\\x00]*", message = "Motivo nao pode conter caractere nulo.") String motivo) {
	public ComentarioModeracaoRequest {
		motivo = motivo == null ? null : motivo.strip();
	}

	@Override public String toString() { return "ComentarioModeracaoRequest[motivo=REDACTED]"; }
}
