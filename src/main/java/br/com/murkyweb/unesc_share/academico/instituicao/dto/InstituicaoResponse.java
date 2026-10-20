package br.com.murkyweb.unesc_share.academico.instituicao.dto;

import java.util.UUID;

import br.com.murkyweb.unesc_share.academico.instituicao.domain.Instituicao;

public record InstituicaoResponse(UUID id, String nome, String sigla) {

	public static InstituicaoResponse from(Instituicao instituicao) {
		return new InstituicaoResponse(instituicao.getId(), instituicao.getNome(), instituicao.getSigla());
	}
}
