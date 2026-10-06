package br.com.murkyweb.unesc_share.usuario.service;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.murkyweb.unesc_share.usuario.domain.PerfilUsuario;
import br.com.murkyweb.unesc_share.usuario.domain.Usuario;
import br.com.murkyweb.unesc_share.usuario.dto.CadastroRequest;
import br.com.murkyweb.unesc_share.usuario.dto.UsuarioResponse;
import br.com.murkyweb.unesc_share.usuario.repository.UsuarioRepository;

@Service
public class UsuarioService {

	private final UsuarioRepository repository;
	private final PasswordEncoder encoder;

	public UsuarioService(UsuarioRepository repository, PasswordEncoder encoder) {
		this.repository = repository;
		this.encoder = encoder;
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

	private ResponseStatusException emailDuplicado() {
		return new ResponseStatusException(HttpStatus.CONFLICT, "Email ja cadastrado.");
	}
}
