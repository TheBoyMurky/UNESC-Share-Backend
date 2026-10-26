package br.com.murkyweb.unesc_share.interacao.comentario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ComentarioRequest(
		@NotBlank @Size(max = 4000)
		@Pattern(regexp = "[^\\x00]*", message = "Conteudo nao pode conter caractere nulo.") String conteudo) {
	public ComentarioRequest {
		conteudo = conteudo == null ? null : conteudo.strip();
	}

	@Override public String toString() { return "ComentarioRequest[conteudo=REDACTED]"; }
}
