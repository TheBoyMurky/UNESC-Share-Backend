package br.com.murkyweb.unesc_share.academico.curso.dto;

import java.util.UUID;

import br.com.murkyweb.unesc_share.academico.curso.domain.Curso;

public record CursoResponse(UUID id, String nome, String descricao, UUID instituicaoId) {

	public static CursoResponse from(Curso curso) {
		return new CursoResponse(curso.getId(), curso.getNome(), curso.getDescricao(), curso.getInstituicao().getId());
	}
}
