package br.com.murkyweb.unesc_share.usuario.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.murkyweb.unesc_share.usuario.domain.PerfilUsuario;
import br.com.murkyweb.unesc_share.usuario.domain.Usuario;

public record UsuarioResponse(UUID id, String nome, String email, PerfilUsuario perfil, Instant dataCadastro) {

	public static UsuarioResponse from(Usuario usuario) {
		return new UsuarioResponse(usuario.getId(), usuario.getNome(), usuario.getEmail(),
				usuario.getPerfil(), usuario.getDataCadastro());
	}
}
