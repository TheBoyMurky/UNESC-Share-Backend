package br.com.murkyweb.unesc_share.security;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Le a identidade ja validada pelo Resource Server; nao decodifica nem autentica tokens. */
@Component
public class UsuarioAutenticado {

	public UUID id() {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof Jwt jwt)) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario nao autenticado.");
		}
		return UUID.fromString(jwt.getSubject());
	}

	public boolean podeModerar() {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		return authentication != null && authentication.getAuthorities().stream()
				.anyMatch(authority -> authority.getAuthority().equals("ROLE_MODERADOR")
						|| authority.getAuthority().equals("ROLE_ADMINISTRADOR"));
	}
}
