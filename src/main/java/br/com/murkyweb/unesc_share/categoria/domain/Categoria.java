package br.com.murkyweb.unesc_share.categoria.domain;

import br.com.murkyweb.unesc_share.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "categorias")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Categoria extends BaseEntity {

	@Column(nullable = false, unique = true, length = 80)
	private String nome;

	@Column(length = 500)
	private String descricao;

	public Categoria(String nome, String descricao) {
		this.nome = nome;
		this.descricao = descricao;
	}

	public void atualizar(String nome, String descricao) {
		this.nome = nome;
		this.descricao = descricao;
	}
}
