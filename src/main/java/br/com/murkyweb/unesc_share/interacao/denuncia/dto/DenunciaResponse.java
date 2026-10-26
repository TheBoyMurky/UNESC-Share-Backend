package br.com.murkyweb.unesc_share.interacao.denuncia.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.murkyweb.unesc_share.interacao.denuncia.domain.Denuncia;
import br.com.murkyweb.unesc_share.interacao.denuncia.domain.MotivoDenuncia;
import br.com.murkyweb.unesc_share.interacao.denuncia.domain.StatusDenuncia;

public record DenunciaResponse(UUID id, MotivoDenuncia motivo, String descricao, StatusDenuncia status,
		Instant dataCadastro, UUID materialId) {
	public static DenunciaResponse from(Denuncia denuncia) {
		return new DenunciaResponse(denuncia.getId(), denuncia.getMotivo(), denuncia.getDescricao(), denuncia.getStatus(),
				denuncia.getDataCadastro(), denuncia.getMaterial().getId());
	}

	@Override public String toString() { return "DenunciaResponse[descricao=REDACTED]"; }
}
