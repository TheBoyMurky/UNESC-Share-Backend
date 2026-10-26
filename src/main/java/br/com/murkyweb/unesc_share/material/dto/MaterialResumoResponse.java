package br.com.murkyweb.unesc_share.material.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.murkyweb.unesc_share.material.domain.Material;
import br.com.murkyweb.unesc_share.material.domain.StatusMaterial;

public record MaterialResumoResponse(UUID id, String titulo, StatusMaterial status, Instant dataCadastro,
		long visualizacoes, UUID usuarioId, UUID disciplinaId, UUID categoriaId) {
	public static MaterialResumoResponse from(Material material) {
		return new MaterialResumoResponse(material.getId(), material.getTitulo(), material.getStatus(), material.getDataCadastro(),
				material.getVisualizacoes(), material.getUsuario().getId(), material.getDisciplina().getId(), material.getCategoria().getId());
	}
}
