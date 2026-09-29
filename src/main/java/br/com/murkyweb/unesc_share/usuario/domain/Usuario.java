package br.com.murkyweb.unesc_share.usuario.domain;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import br.com.murkyweb.unesc_share.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "usuarios")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Usuario extends BaseEntity {

	@Column(nullable = false, length = 120)
	private String nome;

	@Column(nullable = false, unique = true, length = 254)
	private String email;

	@Column(nullable = false, length = 255)
	private String senha;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PerfilUsuario perfil;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant dataCadastro;

	public Usuario(String nome, String email, String senha, PerfilUsuario perfil) {
		this.nome = nome;
		this.email = email;
		this.senha = senha;
		this.perfil = perfil;
	}

	public void atualizarPerfil(String nome, String email) {
		this.nome = nome;
		this.email = email;
	}

	public void alterarSenha(String senha) {
		this.senha = senha;
	}

	public void alterarPerfil(PerfilUsuario perfil) {
		this.perfil = perfil;
	}
}
