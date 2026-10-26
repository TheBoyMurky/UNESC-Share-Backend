package br.com.murkyweb.unesc_share.usuario;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import br.com.murkyweb.unesc_share.security.JwtProperties;
import br.com.murkyweb.unesc_share.usuario.domain.PerfilUsuario;
import br.com.murkyweb.unesc_share.usuario.domain.Usuario;
import br.com.murkyweb.unesc_share.usuario.repository.UsuarioRepository;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UsuarioPerfilApiTests {

	private static final String SENHA_ATUAL = "SenhaAtual-123";
	@Autowired private MockMvc mvc;
	@Autowired private ObjectMapper mapper;
	@Autowired private UsuarioRepository usuarios;
	@Autowired private PasswordEncoder passwords;
	@Autowired private JwtEncoder encoder;
	@Autowired private JwtProperties properties;

	@Test
	void atualizaNomeEEmailNormalizaEmailEExigeSenhaAtualParaTroca() throws Exception {
		var usuario = criarUsuario(PerfilUsuario.USUARIO);
		String token = token(usuario);
		String novoEmail = "novo-" + UUID.randomUUID() + "@example.com";
		var request = Map.of("nome", "  Nome Atualizado  ", "email", " " + novoEmail.toUpperCase() + " ", "senhaAtual", SENHA_ATUAL);
		mvc.perform(putApi("/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.content(mapper.writeValueAsString(request)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(usuario.getId().toString()))
				.andExpect(jsonPath("$.nome").value("Nome Atualizado"))
				.andExpect(jsonPath("$.email").value(novoEmail))
				.andExpect(jsonPath("$.perfil").value("USUARIO"))
				.andExpect(jsonPath("$.senha").doesNotExist());
		assertEquals(novoEmail, usuarios.findById(usuario.getId()).orElseThrow().getEmail());
		// O token continua autenticado, pois identidade e perfil não mudaram.
		mvc.perform(getApi("/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.email").value(novoEmail));
		mvc.perform(putApi("/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.content(mapper.writeValueAsString(Map.of("nome", "Outro Nome", "email", "alguem@example.com"))))
				.andExpect(status().isBadRequest());
		assertEquals(novoEmail, usuarios.findById(usuario.getId()).orElseThrow().getEmail());
	}

	@Test
	void rejeitaSenhaAtualIncorretaEEmailJaCadastradoSemAlterarPerfil() throws Exception {
		var usuario = criarUsuario(PerfilUsuario.MODERADOR);
		var outro = criarUsuario(PerfilUsuario.USUARIO);
		String token = token(usuario);
		mvc.perform(putApi("/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.content(json(Map.of("nome", "Novo Nome", "email", "novo@example.com", "senhaAtual", "SenhaErrada-123"))))
				.andExpect(status().isBadRequest());
		mvc.perform(putApi("/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.content(json(Map.of("nome", "Novo Nome", "email", outro.getEmail().toUpperCase(), "senhaAtual", SENHA_ATUAL))))
				.andExpect(status().isConflict());
		var persistido = usuarios.findById(usuario.getId()).orElseThrow();
		assertEquals(usuario.getNome(), persistido.getNome());
		assertEquals(usuario.getEmail(), persistido.getEmail());
		assertEquals(PerfilUsuario.MODERADOR, persistido.getPerfil());
	}

	@Test
	void alteraSenhaComSenhaAtualMantemHashEJwtAtivoAteExpirar() throws Exception {
		var usuario = criarUsuario(PerfilUsuario.ADMINISTRADOR);
		String token = token(usuario), novaSenha = "NovaSenha-Segura456";
		mvc.perform(putApi("/usuarios/me/senha").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.content(json(Map.of("senhaAtual", "SenhaErrada-123", "novaSenha", novaSenha))))
				.andExpect(status().isBadRequest());
		mvc.perform(putApi("/usuarios/me/senha").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.content(json(Map.of("senhaAtual", SENHA_ATUAL, "novaSenha", novaSenha))))
				.andExpect(status().isNoContent()).andExpect(content().string(""));
		var persistido = usuarios.findById(usuario.getId()).orElseThrow();
		assertNotEquals(novaSenha, persistido.getSenha());
		assertTrue(persistido.getSenha().startsWith("{bcrypt}"));
		assertTrue(passwords.matches(novaSenha, persistido.getSenha()));
		assertFalse(passwords.matches(SENHA_ATUAL, persistido.getSenha()));
		mvc.perform(getApi("/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.perfil").value("ADMINISTRADOR"))
				.andExpect(jsonPath("$.senha").doesNotExist());
		mvc.perform(postApi("/auth/login").content(json(Map.of("email", usuario.getEmail(), "senha", SENHA_ATUAL))))
				.andExpect(status().isUnauthorized());
		mvc.perform(postApi("/auth/login").content(json(Map.of("email", usuario.getEmail(), "senha", novaSenha))))
				.andExpect(status().isOk());
	}

	@Test
	void endpointsExigemJwtEValidamLimitesDoNovoCadastro() throws Exception {
		mvc.perform(putApi("/usuarios/me").content(json(Map.of("nome", "Nome", "email", "email@example.com"))))
				.andExpect(status().isUnauthorized());
		mvc.perform(putApi("/usuarios/me/senha").content(json(Map.of("senhaAtual", SENHA_ATUAL, "novaSenha", "curta"))))
				.andExpect(status().isUnauthorized());
		var usuario = criarUsuario(PerfilUsuario.USUARIO);
		String token = token(usuario);
		mvc.perform(putApi("/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.content(json(Map.of("nome", "N".repeat(121), "email", "invalido", "senhaAtual", SENHA_ATUAL))))
				.andExpect(status().isBadRequest());
		mvc.perform(putApi("/usuarios/me/senha").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.content(json(Map.of("senhaAtual", SENHA_ATUAL, "novaSenha", "á".repeat(40)))))
				.andExpect(status().isBadRequest());
	}

	private Usuario criarUsuario(PerfilUsuario perfil) {
		return usuarios.saveAndFlush(new Usuario("Usuario Teste", "perfil-" + UUID.randomUUID() + "@example.com",
				passwords.encode(SENHA_ATUAL), perfil));
	}

	private String token(Usuario usuario) {
		var now = Instant.now();
		var claims = JwtClaimsSet.builder().issuer(properties.issuer()).audience(List.of(properties.audience()))
				.subject(usuario.getId().toString()).issuedAt(now).notBefore(now.minusSeconds(1))
				.expiresAt(now.plusSeconds(900)).claim("roles", List.of(usuario.getPerfil().name())).build();
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private String json(Object value) throws Exception { return mapper.writeValueAsString(value); }

	private MockHttpServletRequestBuilder putApi(String path) {
		return put("/api/v1" + path).contextPath("/api/v1").servletPath(path).contentType(MediaType.APPLICATION_JSON);
	}

	private MockHttpServletRequestBuilder getApi(String path) {
		return get("/api/v1" + path).contextPath("/api/v1").servletPath(path);
	}

	private MockHttpServletRequestBuilder postApi(String path) {
		return post("/api/v1" + path).contextPath("/api/v1").servletPath(path).contentType(MediaType.APPLICATION_JSON);
	}
}
