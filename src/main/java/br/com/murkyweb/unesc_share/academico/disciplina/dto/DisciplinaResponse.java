package br.com.murkyweb.unesc_share.academico.disciplina.dto;

import java.util.UUID;

import br.com.murkyweb.unesc_share.academico.disciplina.domain.Disciplina;

public record DisciplinaResponse(UUID id, String nome, String descricao, String codigo, UUID cursoId) {

	public static DisciplinaResponse from(Disciplina disciplina) {
		return new DisciplinaResponse(disciplina.getId(), disciplina.getNome(), disciplina.getDescricao(),
				disciplina.getCodigo(), disciplina.getCurso().getId());
	}
}
