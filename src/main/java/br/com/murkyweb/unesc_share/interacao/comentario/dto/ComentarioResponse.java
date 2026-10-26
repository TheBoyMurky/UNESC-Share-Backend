package br.com.murkyweb.unesc_share.interacao.comentario.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.murkyweb.unesc_share.interacao.comentario.domain.Comentario;
import br.com.murkyweb.unesc_share.interacao.comentario.domain.StatusComentario;

public record ComentarioResponse(UUID id, String conteudo, Instant dataCadastro, StatusComentario status,
		UUID usuarioId, UUID materialId) {
	public static ComentarioResponse from(Comentario comentario) {
		return new ComentarioResponse(comentario.getId(), comentario.getConteudo(), comentario.getDataCadastro(),
				comentario.getStatus(), comentario.getUsuario().getId(), comentario.getMaterial().getId());
	}

	@Override public String toString() { return "ComentarioResponse[conteudo=REDACTED]"; }
}
