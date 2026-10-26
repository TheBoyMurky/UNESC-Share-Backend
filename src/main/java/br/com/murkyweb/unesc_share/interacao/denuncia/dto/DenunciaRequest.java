package br.com.murkyweb.unesc_share.interacao.denuncia.dto;

import br.com.murkyweb.unesc_share.interacao.denuncia.domain.MotivoDenuncia;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DenunciaRequest(@NotNull MotivoDenuncia motivo,
		@NotBlank @Size(max = 2000)
		@Pattern(regexp = "[^\\x00]*", message = "Descricao nao pode conter caractere nulo.") String descricao) {
	public DenunciaRequest {
		descricao = descricao == null ? null : descricao.strip();
	}

	@Override public String toString() { return "DenunciaRequest[descricao=REDACTED]"; }
}
