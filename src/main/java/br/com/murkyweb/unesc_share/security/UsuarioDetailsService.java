package br.com.murkyweb.unesc_share.security;

import java.util.Locale;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.murkyweb.unesc_share.usuario.repository.UsuarioRepository;

@Service
public class UsuarioDetailsService implements UserDetailsService {

	private final UsuarioRepository repository;

	public UsuarioDetailsService(UsuarioRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String email) {
		var usuario = repository.findByEmailIgnoreCase(email.strip().toLowerCase(Locale.ROOT))
				.orElseThrow(() -> new UsernameNotFoundException("Credenciais invalidas."));
		return User.withUsername(usuario.getId().toString())
				.password(usuario.getSenha())
				.roles(usuario.getPerfil().name())
				.build();
	}
}
