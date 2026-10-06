package br.com.murkyweb.unesc_share.security;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import br.com.murkyweb.unesc_share.security.dto.LoginRequest;
import br.com.murkyweb.unesc_share.security.dto.TokenResponse;
import br.com.murkyweb.unesc_share.usuario.dto.CadastroRequest;
import jakarta.validation.Validation;

class JwtConfigTests {

	private final SecurityConfig config = new SecurityConfig();

	@ParameterizedTest
	@ValueSource(strings = {"", "invalido!", "Y3VydGE="})
	void rejeitaChaveInvalidaOuMuitoCurta(String secret) {
		assertThrows(IllegalArgumentException.class,
				() -> config.jwtSecretKey(new JwtProperties(secret, "issuer", "audience", Duration.ofMinutes(15))));
	}

	@Test
	void aceitaChaveDePeloMenos256Bits() {
		var secret = Base64.getEncoder().encodeToString(new byte[32]);
		assertEquals(32, config.jwtSecretKey(new JwtProperties(secret, "issuer", "audience",
				Duration.ofMinutes(15))).getEncoded().length);
	}

	@Test
	void restringeConfiguracaoAValidadeCurta() {
		try (var factory = Validation.buildDefaultValidatorFactory()) {
			var validator = factory.getValidator();
			for (var ttl : new Duration[] {Duration.ZERO, Duration.ofSeconds(59), Duration.ofMinutes(31)}) {
				assertFalse(validator.validate(new JwtProperties("test-secret", "issuer", "audience", ttl)).isEmpty());
			}
			assertTrue(validator.validate(new JwtProperties("test-secret", "issuer", "audience",
					Duration.ofMinutes(15))).isEmpty());
		}
	}

	@Test
	void toStringNaoRevelaSegredosOuCredenciais() {
		assertFalse(new JwtProperties("segredo-sensivel", "issuer", "audience", Duration.ofMinutes(15))
				.toString().contains("segredo-sensivel"));
		assertFalse(new CadastroRequest("Nome", "email@example.com", "senha-sensivel").toString().contains("senha-sensivel"));
		assertFalse(new LoginRequest("email@example.com", "senha-sensivel").toString().contains("senha-sensivel"));
		assertFalse(new TokenResponse("token-sensivel", "Bearer", 900, java.time.Instant.now())
				.toString().contains("token-sensivel"));
	}
}
