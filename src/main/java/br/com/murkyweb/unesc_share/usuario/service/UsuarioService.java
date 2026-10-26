package br.com.murkyweb.unesc_share.usuario.service;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.murkyweb.unesc_share.usuario.domain.PerfilUsuario;
import br.com.murkyweb.unesc_share.usuario.domain.Usuario;
import br.com.murkyweb.unesc_share.usuario.dto.AlterarSenhaRequest;
import br.com.murkyweb.unesc_share.usuario.dto.CadastroRequest;
import br.com.murkyweb.unesc_share.usuario.dto.PerfilAtualizacaoRequest;
import br.com.murkyweb.unesc_share.usuario.dto.UsuarioResponse;
import br.com.murkyweb.unesc_share.usuario.repository.UsuarioRepository;

@Service
public class UsuarioService {

	private final UsuarioRepository repository;
	private final PasswordEncoder encoder;
	private final AuthenticationManager authenticationManager;

	public UsuarioService(UsuarioRepository repository, PasswordEncoder encoder, AuthenticationManager authenticationManager) {
		this.repository = repository;
		this.encoder = encoder;
		this.authenticationManager = authenticationManager;
	}

	@Transactional
	public UsuarioResponse cadastrar(CadastroRequest request) {
		if (repository.existsByEmailIgnoreCase(request.email())) {
			throw emailDuplicado();
		}
		var usuario = new Usuario(request.nome(), request.email(), encoder.encode(request.senha()), PerfilUsuario.USUARIO);
		try {
			return UsuarioResponse.from(repository.saveAndFlush(usuario));
		} catch (DataIntegrityViolationException exception) {
			// A constraint tambem protege dois cadastros concorrentes do mesmo email normalizado.
			throw emailDuplicado();
		}
	}

	@Transactional(readOnly = true)
	public UsuarioResponse consultarAtual(UUID id) {
		return repository.findById(id).map(UsuarioResponse::from)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario nao disponivel."));
	}

	@Transactional
	public UsuarioResponse atualizarAtual(UUID id, PerfilAtualizacaoRequest request) {
		var usuario = usuario(id);
		boolean alterouEmail = !usuario.getEmail().equals(request.email());
		if (alterouEmail) {
			confirmarSenhaAtual(usuario, request.senhaAtual());
			if (repository.existsByEmailIgnoreCaseAndIdNot(request.email(), id)) {
				throw emailDuplicado();
			}
		}
		usuario.atualizarPerfil(request.nome(), request.email());
		try {
			return UsuarioResponse.from(repository.saveAndFlush(usuario));
		} catch (DataIntegrityViolationException exception) {
			throw emailDuplicado();
		}
	}

	@Transactional
	public void alterarSenha(UUID id, AlterarSenhaRequest request) {
		var usuario = usuario(id);
		confirmarSenhaAtual(usuario, request.senhaAtual());
		if (encoder.matches(request.novaSenha(), usuario.getSenha())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A nova senha deve ser diferente da senha atual.");
		}
		usuario.alterarSenha(encoder.encode(request.novaSenha()));
		repository.saveAndFlush(usuario);
	}

	private Usuario usuario(UUID id) {
		return repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario nao disponivel."));
	}

	private void confirmarSenhaAtual(Usuario usuario, String senhaAtual) {
		if (senhaAtual == null || senhaAtual.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a senha atual para alterar email ou senha.");
		}
		try {
			authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(usuario.getEmail(), senhaAtual));
		} catch (AuthenticationException exception) {
			// Falha de reautenticacao nao invalida o JWT que autenticou esta requisicao.
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A senha atual nao confere.");
		}
	}

	private ResponseStatusException emailDuplicado() {
		return new ResponseStatusException(HttpStatus.CONFLICT, "Email ja cadastrado.");
	}
}
