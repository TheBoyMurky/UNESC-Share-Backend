package br.com.murkyweb.unesc_share.categoria.dto;

import java.util.UUID;

import br.com.murkyweb.unesc_share.categoria.domain.Categoria;

public record CategoriaResponse(UUID id, String nome, String descricao) {

	public static CategoriaResponse from(Categoria categoria) {
		return new CategoriaResponse(categoria.getId(), categoria.getNome(), categoria.getDescricao());
	}
}
