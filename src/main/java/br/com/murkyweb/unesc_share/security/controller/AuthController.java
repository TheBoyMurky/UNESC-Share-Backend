package br.com.murkyweb.unesc_share.security.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import br.com.murkyweb.unesc_share.security.dto.LoginRequest;
import br.com.murkyweb.unesc_share.security.dto.TokenResponse;
import br.com.murkyweb.unesc_share.security.service.AuthService;
import br.com.murkyweb.unesc_share.usuario.dto.CadastroRequest;
import br.com.murkyweb.unesc_share.usuario.dto.UsuarioResponse;
import br.com.murkyweb.unesc_share.usuario.service.UsuarioService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
@Tag(name = "Autenticacao", description = "Cadastro publico e emissao de JWT Bearer.")
public class AuthController {

	private final UsuarioService usuarios;
	private final AuthService auth;

	public AuthController(UsuarioService usuarios, AuthService auth) {
		this.usuarios = usuarios;
		this.auth = auth;
	}

	@PostMapping("/cadastro")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Cadastrar usuario", description = "Cadastro publico; o perfil e definido pelo servidor como USUARIO.", security = {})
	public UsuarioResponse cadastrar(@Valid @RequestBody CadastroRequest request) {
		return usuarios.cadastrar(request);
	}

	@PostMapping("/login")
	@Operation(summary = "Iniciar sessao", description = "Valida email e senha e retorna um access token JWT Bearer.", security = {})
	public TokenResponse login(@Valid @RequestBody LoginRequest request) {
		return auth.login(request);
	}
}
