package br.com.murkyweb.unesc_share.interacao.denuncia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DenunciaModeracaoRequest(@NotBlank @Size(max = 1000)
		@Pattern(regexp = "[^\\x00]*", message = "Justificativa nao pode conter caractere nulo.") String justificativa) {
	public DenunciaModeracaoRequest {
		justificativa = justificativa == null ? null : justificativa.strip();
	}
	@Override public String toString() { return "DenunciaModeracaoRequest[justificativa=REDACTED]"; }
}
