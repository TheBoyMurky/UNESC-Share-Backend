package br.com.murkyweb.unesc_share.material.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import br.com.murkyweb.unesc_share.compartilhamento.magnet.dto.MagnetResponse;
import br.com.murkyweb.unesc_share.compartilhamento.torrent.dto.TorrentResponse;
import br.com.murkyweb.unesc_share.material.domain.Material;
import br.com.murkyweb.unesc_share.material.domain.StatusMaterial;

public record MaterialDetalheResponse(UUID id, String titulo, String descricao, StatusMaterial status, Instant dataCadastro,
		long visualizacoes, UUID usuarioId, UUID disciplinaId, UUID categoriaId, List<MagnetResponse> magnets, List<TorrentResponse> torrents) {
	public static MaterialDetalheResponse from(Material material, List<MagnetResponse> magnets, List<TorrentResponse> torrents) {
		return new MaterialDetalheResponse(material.getId(), material.getTitulo(), material.getDescricao(), material.getStatus(),
				material.getDataCadastro(), material.getVisualizacoes(), material.getUsuario().getId(), material.getDisciplina().getId(),
				material.getCategoria().getId(), List.copyOf(magnets), List.copyOf(torrents));
	}
}
