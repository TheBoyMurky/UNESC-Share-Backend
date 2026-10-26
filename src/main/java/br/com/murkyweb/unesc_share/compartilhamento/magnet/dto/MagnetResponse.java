package br.com.murkyweb.unesc_share.compartilhamento.magnet.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.murkyweb.unesc_share.compartilhamento.magnet.domain.Magnet;

public record MagnetResponse(UUID id, String magnetUri, String infoHash, Instant dataCadastro) {
	public static MagnetResponse from(Magnet magnet) {
		return new MagnetResponse(magnet.getId(), magnet.getMagnetUri(), magnet.getInfoHash(), magnet.getDataCadastro());
	}

	@Override public String toString() { return "MagnetResponse[magnetUri=REDACTED]"; }
}
