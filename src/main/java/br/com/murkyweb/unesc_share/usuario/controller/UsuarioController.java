package br.com.murkyweb.unesc_share.usuario.controller;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.murkyweb.unesc_share.usuario.dto.UsuarioResponse;
import br.com.murkyweb.unesc_share.usuario.service.UsuarioService;

@RestController
@RequestMapping("/usuarios")
public class UsuarioController {

	private final UsuarioService service;

	public UsuarioController(UsuarioService service) {
		this.service = service;
	}

	@GetMapping("/me")
	public UsuarioResponse consultarAtual(@AuthenticationPrincipal Jwt jwt) {
		return service.consultarAtual(UUID.fromString(jwt.getSubject()));
	}
}
