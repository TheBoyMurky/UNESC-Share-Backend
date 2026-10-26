package br.com.murkyweb.unesc_share.material.dto;

import java.util.UUID;

import jakarta.validation.constraints.Size;

public record MaterialFiltro(@Size(max = 200) String q, UUID instituicaoId, UUID cursoId, UUID disciplinaId, UUID categoriaId) {
	public MaterialFiltro { q = q == null ? "" : q.strip(); }
}
