package br.com.murkyweb.unesc_share.interacao.denuncia.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.murkyweb.unesc_share.interacao.denuncia.domain.ModeracaoDenuncia;
import br.com.murkyweb.unesc_share.interacao.denuncia.domain.StatusDenuncia;

public record DenunciaModeracaoResponse(UUID id, UUID denunciaId, UUID responsavelId, String justificativa,
		StatusDenuncia estadoAnterior, StatusDenuncia estadoNovo, Instant dataCadastro) {
	public static DenunciaModeracaoResponse from(ModeracaoDenuncia moderacao) {
		return new DenunciaModeracaoResponse(moderacao.getId(), moderacao.getDenuncia().getId(),
				moderacao.getResponsavel().getId(), moderacao.getJustificativa(), moderacao.getEstadoAnterior(),
				moderacao.getEstadoNovo(), moderacao.getDataCadastro());
	}
	@Override public String toString() { return "DenunciaModeracaoResponse[justificativa=REDACTED]"; }
}
