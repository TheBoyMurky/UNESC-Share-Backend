package br.com.murkyweb.unesc_share.usuario.service;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.murkyweb.unesc_share.usuario.domain.PerfilUsuario;
import br.com.murkyweb.unesc_share.usuario.domain.Usuario;
import br.com.murkyweb.unesc_share.usuario.dto.CadastroRequest;
import br.com.murkyweb.unesc_share.usuario.repository.UsuarioRepository;
import jakarta.validation.Validator;

@Service
@Profile("dev")
public class AdministradorInicialService {

	private final UsuarioRepository repository;
	private final PasswordEncoder encoder;
	private final Validator validator;

	public AdministradorInicialService(UsuarioRepository repository, PasswordEncoder encoder, Validator validator) {
		this.repository = repository;
		this.encoder = encoder;
		this.validator = validator;
	}

	@Transactional
	public boolean provisionar(CadastroRequest request) {
		// Reutiliza Bean Validation sem anexar valores rejeitados ao erro de inicializacao.
		if (request == null || !validator.validate(request).isEmpty()) {
			throw new IllegalStateException("Configuracao do administrador inicial invalida. Verifique ADMIN_NAME, ADMIN_EMAIL e ADMIN_PASSWORD.");
		}

		var existente = repository.findByEmailIgnoreCase(request.email());
		if (existente.isPresent()) {
			if (existente.get().getPerfil() != PerfilUsuario.ADMINISTRADOR) {
				throw new IllegalStateException("O email configurado ja pertence a uma conta sem perfil ADMINISTRADOR. Nenhum perfil ou senha foi alterado.");
			}
			return false;
		}

		var administrador = new Usuario(request.nome(), request.email(), encoder.encode(request.senha()), PerfilUsuario.ADMINISTRADOR);
		try {
			repository.saveAndFlush(administrador);
		} catch (DataIntegrityViolationException exception) {
			// Nao consultar novamente nesta transacao: ela pode estar marcada para rollback.
			throw new IllegalStateException("Conflito ao criar o administrador inicial. Verifique a conta configurada antes de reiniciar.");
		}
		return true;
	}
}
