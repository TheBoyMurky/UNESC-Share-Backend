package br.com.murkyweb.unesc_share.usuario.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("unesc-share.admin-inicial")
public record AdministradorInicialProperties(String nome, String email, String senha) {

	@Override
	public String toString() {
		return "AdministradorInicialProperties[credenciais=REDACTED]";
	}
}
