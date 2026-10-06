package br.com.murkyweb.unesc_share.security.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.murkyweb.unesc_share.security.dto.LoginRequest;
import br.com.murkyweb.unesc_share.security.dto.TokenResponse;
import br.com.murkyweb.unesc_share.security.service.AuthService;
import br.com.murkyweb.unesc_share.usuario.dto.CadastroRequest;
import br.com.murkyweb.unesc_share.usuario.dto.UsuarioResponse;
import br.com.murkyweb.unesc_share.usuario.service.UsuarioService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

	private final UsuarioService usuarios;
	private final AuthService auth;

	public AuthController(UsuarioService usuarios, AuthService auth) {
		this.usuarios = usuarios;
		this.auth = auth;
	}

	@PostMapping("/cadastro")
	@ResponseStatus(HttpStatus.CREATED)
	public UsuarioResponse cadastrar(@Valid @RequestBody CadastroRequest request) {
		return usuarios.cadastrar(request);
	}

	@PostMapping("/login")
	public TokenResponse login(@Valid @RequestBody LoginRequest request) {
		return auth.login(request);
	}
}
