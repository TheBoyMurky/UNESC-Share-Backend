package br.com.murkyweb.unesc_share.academico.curso.domain;

import br.com.murkyweb.unesc_share.academico.instituicao.domain.Instituicao;
import br.com.murkyweb.unesc_share.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
		name = "cursos",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_curso_instituicao_nome",
				columnNames = { "instituicao_id", "nome" }
		)
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Curso extends BaseEntity {

	@Column(nullable = false, length = 150)
	private String nome;

	@Column(length = 1000)
	private String descricao;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(
			name = "instituicao_id",
			nullable = false,
			foreignKey = @ForeignKey(name = "fk_curso_instituicao")
	)
	private Instituicao instituicao;

	public Curso(String nome, String descricao, Instituicao instituicao) {
		this.nome = nome;
		this.descricao = descricao;
		this.instituicao = instituicao;
	}

	public void atualizar(String nome, String descricao, Instituicao instituicao) {
		this.nome = nome;
		this.descricao = descricao;
		this.instituicao = instituicao;
	}
}
