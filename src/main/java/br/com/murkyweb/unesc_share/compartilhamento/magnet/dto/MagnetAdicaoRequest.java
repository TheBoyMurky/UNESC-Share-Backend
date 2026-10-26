package br.com.murkyweb.unesc_share.compartilhamento.magnet.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MagnetAdicaoRequest(
		@NotNull @Size(min = 1, max = 10) List<@NotBlank @Size(max = 4000) String> magnetUris) {
	@Override public String toString() { return "MagnetAdicaoRequest[magnetUris=REDACTED]"; }
}
