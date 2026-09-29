package br.com.murkyweb.unesc_share.academico.instituicao.domain;

import br.com.murkyweb.unesc_share.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "instituicoes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Instituicao extends BaseEntity {

	@Column(nullable = false, unique = true, length = 180)
	private String nome;

	@Column(nullable = false, unique = true, length = 30)
	private String sigla;

	public Instituicao(String nome, String sigla) {
		this.nome = nome;
		this.sigla = sigla;
	}

	public void atualizar(String nome, String sigla) {
		this.nome = nome;
		this.sigla = sigla;
	}
}
