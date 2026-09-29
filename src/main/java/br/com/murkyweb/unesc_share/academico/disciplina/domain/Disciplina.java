package br.com.murkyweb.unesc_share.academico.disciplina.domain;

import br.com.murkyweb.unesc_share.academico.curso.domain.Curso;
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
		name = "disciplinas",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_disciplina_curso_codigo",
				columnNames = { "curso_id", "codigo" }
		)
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Disciplina extends BaseEntity {

	@Column(nullable = false, length = 150)
	private String nome;

	@Column(length = 1000)
	private String descricao;

	@Column(nullable = false, length = 30)
	private String codigo;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(
			name = "curso_id",
			nullable = false,
			foreignKey = @ForeignKey(name = "fk_disciplina_curso")
	)
	private Curso curso;

	public Disciplina(String nome, String descricao, String codigo, Curso curso) {
		this.nome = nome;
		this.descricao = descricao;
		this.codigo = codigo;
		this.curso = curso;
	}

	public void atualizar(String nome, String descricao, String codigo, Curso curso) {
		this.nome = nome;
		this.descricao = descricao;
		this.codigo = codigo;
		this.curso = curso;
	}
}
