package br.com.murkyweb.unesc_share.usuario;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import br.com.murkyweb.unesc_share.usuario.config.AdministradorInicialProperties;
import br.com.murkyweb.unesc_share.usuario.domain.PerfilUsuario;
import br.com.murkyweb.unesc_share.usuario.domain.Usuario;
import br.com.murkyweb.unesc_share.usuario.dto.CadastroRequest;
import br.com.murkyweb.unesc_share.usuario.repository.UsuarioRepository;
import br.com.murkyweb.unesc_share.usuario.service.AdministradorInicialService;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

class AdministradorInicialServiceTests {

	private final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
	private final UsuarioRepository repository = mock(UsuarioRepository.class);
	private final PasswordEncoder encoder = mock(PasswordEncoder.class);
	private AdministradorInicialService service;

	@BeforeEach
	void configurar() {
		service = new AdministradorInicialService(repository, encoder, factory.getValidator());
	}

	@AfterEach
	void fecharValidator() { factory.close(); }

	@Test
	void criaSomenteAdministradorComSenhaCodificadaEDadosNormalizados() {
		when(encoder.encode("SenhaTeste-123")).thenReturn("{bcrypt}hash-teste");
		assertTrue(service.provisionar(new CadastroRequest("  Administrador  ", " ADMIN@EXAMPLE.COM ", "SenhaTeste-123")));
		var captor = ArgumentCaptor.forClass(Usuario.class);
		verify(repository).findByEmailIgnoreCase("admin@example.com");
		verify(repository).saveAndFlush(captor.capture());
		var administrador = captor.getValue();
		assertEquals("Administrador", administrador.getNome());
		assertEquals("admin@example.com", administrador.getEmail());
		assertEquals("{bcrypt}hash-teste", administrador.getSenha());
		assertEquals(PerfilUsuario.ADMINISTRADOR, administrador.getPerfil());
	}

	@Test
	void administradorExistenteNaoTemNomeSenhaOuPerfilAlterados() {
		var existente = new Usuario("Nome original", "admin@example.com", "hash-original", PerfilUsuario.ADMINISTRADOR);
		when(repository.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(existente));
		assertFalse(service.provisionar(new CadastroRequest("Outro nome", "ADMIN@EXAMPLE.COM", "OutraSenha-456")));
		assertEquals("Nome original", existente.getNome());
		assertEquals("hash-original", existente.getSenha());
		assertEquals(PerfilUsuario.ADMINISTRADOR, existente.getPerfil());
		verify(repository, never()).saveAndFlush(any());
		verifyNoInteractions(encoder);
	}

	@ParameterizedTest
	@EnumSource(value = PerfilUsuario.class, names = { "USUARIO", "MODERADOR" })
	void naoPromoveContaExistente(PerfilUsuario perfil) {
		var existente = new Usuario("Nome original", "admin@example.com", "hash-original", perfil);
		when(repository.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(existente));
		var exception = assertThrows(IllegalStateException.class,
				() -> service.provisionar(new CadastroRequest("Administrador", "admin@example.com", "SenhaTeste-123")));
		assertTrue(exception.getMessage().contains("sem perfil ADMINISTRADOR"));
		assertFalse(exception.getMessage().contains("admin@example.com"));
		assertEquals(perfil, existente.getPerfil());
		assertEquals("hash-original", existente.getSenha());
		assertEquals("Nome original", existente.getNome());
		verify(repository, never()).saveAndFlush(any());
		verifyNoInteractions(encoder);
	}

	@Test
	void validaCamposSemAnexarValoresRejeitadosAoErro() {
		for (var request : List.of(
				new CadastroRequest(null, null, null), new CadastroRequest(" ", "admin@example.com", "SenhaTeste-123"),
				new CadastroRequest("Nome", "email-invalido", "SenhaTeste-123"),
				new CadastroRequest("Nome", "admin@example.com", "curta"),
				new CadastroRequest("Nome", "admin@example.com", "A".repeat(65)),
				new CadastroRequest("Nome", "admin@example.com", "á".repeat(40)),
				new CadastroRequest("N".repeat(121), "admin@example.com", "SenhaTeste-123"))) {
			var exception = assertThrows(IllegalStateException.class, () -> service.provisionar(request));
			assertEquals("Configuracao do administrador inicial invalida. Verifique ADMIN_NAME, ADMIN_EMAIL e ADMIN_PASSWORD.",
					exception.getMessage());
			assertNull(exception.getCause());
		}
		assertThrows(IllegalStateException.class, () -> service.provisionar(null));
		verifyNoInteractions(repository, encoder);
	}

	@Test
	void conflitoDeConstraintNaoRevelaDadosNemTentaConsultarTransacaoAbortada() {
		when(encoder.encode(anyString())).thenReturn("hash-teste");
		when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("SQL com credencial-sensivel"));
		var exception = assertThrows(IllegalStateException.class,
				() -> service.provisionar(new CadastroRequest("Administrador", "admin@example.com", "SenhaTeste-123")));
		assertEquals("Conflito ao criar o administrador inicial. Verifique a conta configurada antes de reiniciar.", exception.getMessage());
		assertNull(exception.getCause());
		verify(repository, times(1)).findByEmailIgnoreCase(anyString());
	}

	@Test
	void configuracaoNaoExibeNomeEmailOuSenhaNoToString() {
		String texto = new AdministradorInicialProperties("nome-sensivel", "email-sensivel@example.com", "senha-sensivel").toString();
		assertEquals("AdministradorInicialProperties[credenciais=REDACTED]", texto);
	}
}
