package br.com.murkyweb.unesc_share.interacao.comentario.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.murkyweb.unesc_share.interacao.comentario.domain.ModeracaoComentario;
import br.com.murkyweb.unesc_share.interacao.comentario.domain.StatusComentario;

public record ComentarioModeracaoResponse(UUID id, UUID comentarioId, UUID responsavelId, String motivo,
		StatusComentario estadoAnterior, StatusComentario estadoNovo, Instant dataCadastro) {
	public static ComentarioModeracaoResponse from(ModeracaoComentario moderacao) {
		return new ComentarioModeracaoResponse(moderacao.getId(), moderacao.getComentario().getId(),
				moderacao.getResponsavel().getId(), moderacao.getMotivo(), moderacao.getEstadoAnterior(),
				moderacao.getEstadoNovo(), moderacao.getDataCadastro());
	}

	@Override public String toString() { return "ComentarioModeracaoResponse[motivo=REDACTED]"; }
}
