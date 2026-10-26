package br.com.murkyweb.unesc_share.usuario.controller;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import br.com.murkyweb.unesc_share.usuario.dto.AlterarSenhaRequest;
import br.com.murkyweb.unesc_share.usuario.dto.PerfilAtualizacaoRequest;
import br.com.murkyweb.unesc_share.usuario.dto.UsuarioResponse;
import br.com.murkyweb.unesc_share.usuario.service.UsuarioService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/usuarios")
@Tag(name = "Usuarios", description = "Consulta e atualizacao da propria conta autenticada.")
@SecurityRequirement(name = "bearerAuth")
public class UsuarioController {

	private final UsuarioService service;

	public UsuarioController(UsuarioService service) {
		this.service = service;
	}

	@GetMapping("/me")
	public UsuarioResponse consultarAtual(@AuthenticationPrincipal Jwt jwt) {
		return service.consultarAtual(UUID.fromString(jwt.getSubject()));
	}

	@PutMapping("/me")
	public UsuarioResponse atualizarAtual(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody PerfilAtualizacaoRequest request) {
		return service.atualizarAtual(UUID.fromString(jwt.getSubject()), request);
	}

	@PutMapping("/me/senha")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void alterarSenha(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AlterarSenhaRequest request) {
		service.alterarSenha(UUID.fromString(jwt.getSubject()), request);
	}
}
