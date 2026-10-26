package br.com.murkyweb.unesc_share.interacao.avaliacao.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import br.com.murkyweb.unesc_share.interacao.avaliacao.domain.Avaliacao;

public record AvaliacaoResponse(UUID id, BigDecimal nota, Instant dataCadastro, UUID usuarioId, UUID materialId) {
	public static AvaliacaoResponse from(Avaliacao avaliacao) {
		return new AvaliacaoResponse(avaliacao.getId(), avaliacao.getNota(), avaliacao.getDataCadastro(),
				avaliacao.getUsuario().getId(), avaliacao.getMaterial().getId());
	}

	@Override public String toString() { return "AvaliacaoResponse[nota=REDACTED]"; }
}
