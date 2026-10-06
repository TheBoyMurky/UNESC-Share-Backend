package br.com.murkyweb.unesc_share.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.murkyweb.unesc_share.usuario.domain.PerfilUsuario;
import br.com.murkyweb.unesc_share.usuario.domain.Usuario;
import br.com.murkyweb.unesc_share.usuario.repository.UsuarioRepository;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(AutenticacaoTests.Routes.class)
class AutenticacaoTests {

	private static final String SENHA = "SenhaTeste-123";
	@Autowired private MockMvc mvc;
	@Autowired private ObjectMapper mapper;
	@Autowired private UsuarioRepository usuarios;
	@Autowired private PasswordEncoder passwords;
	@Autowired private JwtDecoder decoder;
	@Autowired private JwtEncoder encoder;
	@Autowired private JwtProperties properties;

	@Test
	void cadastraNormalizaEmailProtegeSenhaEFixaPerfil() throws Exception {
		String email = novoEmail();
		var result = mvc.perform(postApi("/auth/cadastro").content(mapper.writeValueAsString(Map.of(
				"nome", "  Estudante Teste  ", "email", " " + email.toUpperCase() + " ",
				"senha", SENHA, "perfil", "ADMINISTRADOR"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.nome").value("Estudante Teste"))
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.perfil").value("USUARIO"))
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.dataCadastro").isNotEmpty())
				.andExpect(jsonPath("$.senha").doesNotExist())
				.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, max-age=0, must-revalidate"))
				.andReturn();
		assertNull(result.getRequest().getSession(false));
		var usuario = usuarios.findByEmailIgnoreCase(email).orElseThrow();
		assertEquals(PerfilUsuario.USUARIO, usuario.getPerfil());
		assertNotEquals(SENHA, usuario.getSenha());
		assertTrue(usuario.getSenha().startsWith("{bcrypt}"));
		assertTrue(passwords.matches(SENHA, usuario.getSenha()));
	}

	@Test
	void rejeitaEmailDuplicadoIgnorandoMaiusculas() throws Exception {
		var usuario = criarUsuario(PerfilUsuario.USUARIO);
		mvc.perform(postApi("/auth/cadastro").content(mapper.writeValueAsString(Map.of(
				"nome", "Outro Estudante", "email", usuario.getEmail().toUpperCase(), "senha", SENHA))))
				.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").value("Email ja cadastrado."));
	}

	@Test
	void rejeitaCamposInvalidosSemRevelarSenha() throws Exception {
		var result = mvc.perform(postApi("/auth/cadastro").content(mapper.writeValueAsString(Map.of(
				"nome", " ", "email", "email-invalido", "senha", "curta"))))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.errors").isArray()).andReturn();
		assertFalse(result.getResponse().getContentAsString().contains("curta"));
	}

	@Test
	void rejeitaSenhaAcimaDoLimiteDeBytesDoBCrypt() throws Exception {
		mvc.perform(postApi("/auth/cadastro").content(mapper.writeValueAsString(Map.of(
				"nome", "Estudante", "email", novoEmail(), "senha", "á".repeat(40)))))
				.andExpect(status().isBadRequest());
		mvc.perform(postApi("/auth/login").content(mapper.writeValueAsString(Map.of(
				"email", novoEmail(), "senha", "á".repeat(40)))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejeitaJsonMalformado() throws Exception {
		mvc.perform(postApi("/auth/cadastro").content("{invalido"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void loginEmiteTokenDeQuinzeMinutosEPermiteConsultarProprioUsuario() throws Exception {
		var usuario = criarUsuario(PerfilUsuario.USUARIO);
		String token = login(usuario.getEmail().toUpperCase(), SENHA);
		var jwt = decoder.decode(token);
		assertEquals(usuario.getId().toString(), jwt.getSubject());
		assertEquals(properties.issuer(), jwt.getClaimAsString("iss"));
		assertEquals(List.of(properties.audience()), jwt.getAudience());
		assertEquals(List.of("USUARIO"), jwt.getClaimAsStringList("roles"));
		assertEquals(Duration.ofMinutes(15), Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()));
		assertFalse(jwt.hasClaim("senha"));
		assertFalse(jwt.hasClaim("email"));
		var result = mvc.perform(getApi("/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(usuario.getId().toString()))
				.andExpect(jsonPath("$.email").value(usuario.getEmail()))
				.andExpect(jsonPath("$.senha").doesNotExist()).andReturn();
		assertNull(result.getRequest().getSession(false));
	}

	@Test
	void usuarioCadastradoPelaApiConsegueFazerLogin() throws Exception {
		String email = novoEmail();
		mvc.perform(postApi("/auth/cadastro").content(mapper.writeValueAsString(Map.of(
				"nome", "Estudante", "email", email, "senha", SENHA))))
				.andExpect(status().isCreated());
		String token = login(email, SENHA);
		mvc.perform(getApi("/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
	}

	@Test
	void senhaIncorretaEUsuarioInexistenteRetornamMesmaMensagem() throws Exception {
		var usuario = criarUsuario(PerfilUsuario.USUARIO);
		for (String email : List.of(usuario.getEmail(), novoEmail())) {
			mvc.perform(postApi("/auth/login").content(mapper.writeValueAsString(Map.of(
					"email", email, "senha", "SenhaIncorreta"))))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.detail").value("Credenciais invalidas."));
		}
	}

	@Test
	void rejeitaAcessoSemTokenEComTokenMalformado() throws Exception {
		mvc.perform(getApi("/usuarios/me")).andExpect(status().isUnauthorized());
		mvc.perform(getApi("/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer invalido"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void rejeitaAssinaturaAdulterada() throws Exception {
		var usuario = criarUsuario(PerfilUsuario.USUARIO);
		String token = login(usuario.getEmail(), SENHA);
		int inicioAssinatura = token.lastIndexOf('.') + 1;
		char substituto = token.charAt(inicioAssinatura) == 'A' ? 'B' : 'A';
		String adulterado = token.substring(0, inicioAssinatura) + substituto + token.substring(inicioAssinatura + 1);
		mvc.perform(getApi("/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + adulterado))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void rejeitaTokenExpiradoSemToleranciaAdicionalDeUmMinuto() throws Exception {
		String token = assinar(claims(UUID.randomUUID().toString())
				.issuedAt(Instant.now().minusSeconds(901)).notBefore(Instant.now().minusSeconds(901))
				.expiresAt(Instant.now().minusSeconds(1)));
		mvc.perform(getApi("/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void rejeitaEmissorAudienciaEIdentidadeInvalidos() throws Exception {
		for (var claims : List.of(
				claims(UUID.randomUUID().toString()).issuer("outro-emissor"),
				claims(UUID.randomUUID().toString()).audience(List.of("outra-api")),
				claims("nao-e-uuid"))) {
			mvc.perform(getApi("/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + assinar(claims)))
					.andExpect(status().isUnauthorized());
		}
	}

	@Test
	void rejeitaTokenSemExpiracaoOuAindaNaoValido() throws Exception {
		var semExpiracao = JwtClaimsSet.builder().issuer(properties.issuer())
				.audience(List.of(properties.audience())).subject(UUID.randomUUID().toString());
		for (var claims : List.of(semExpiracao,
				claims(UUID.randomUUID().toString()).notBefore(Instant.now().plusSeconds(60)))) {
			mvc.perform(getApi("/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + assinar(claims)))
					.andExpect(status().isUnauthorized());
		}
	}

	@Test
	void naoAceitaTokenNaQueryOuCookieNemCredenciaisBasic() throws Exception {
		var usuario = criarUsuario(PerfilUsuario.USUARIO);
		String token = login(usuario.getEmail(), SENHA);
		mvc.perform(getApi("/usuarios/me").param("access_token", token))
				.andExpect(status().isUnauthorized());
		mvc.perform(getApi("/usuarios/me").cookie(new jakarta.servlet.http.Cookie("access_token", token)))
				.andExpect(status().isUnauthorized());
		mvc.perform(getApi("/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Basic dGVzdGU6dGVzdGU="))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void perfilUsuarioNaoAcessaRotaAdministrativaMasAdministradorAcessa() throws Exception {
		var usuario = criarUsuario(PerfilUsuario.USUARIO);
		mvc.perform(getApi("/__test/admin").header(HttpHeaders.AUTHORIZATION,
				"Bearer " + login(usuario.getEmail(), SENHA))).andExpect(status().isForbidden());
		var administrador = criarUsuario(PerfilUsuario.ADMINISTRADOR);
		mvc.perform(getApi("/__test/admin").header(HttpHeaders.AUTHORIZATION,
				"Bearer " + login(administrador.getEmail(), SENHA))).andExpect(status().isOk());
	}

	@Test
	void meNaoPermiteEscolherOutroUsuarioPelaQuery() throws Exception {
		var usuario = criarUsuario(PerfilUsuario.USUARIO);
		var outro = criarUsuario(PerfilUsuario.USUARIO);
		mvc.perform(getApi("/usuarios/me").param("usuarioId", outro.getId().toString())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + login(usuario.getEmail(), SENHA)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(usuario.getId().toString()));
	}

	@Test
	void corsPermiteFrontendConhecidoERejeitaOrigemDesconhecida() throws Exception {
		mvc.perform(options("/api/v1/usuarios/me").contextPath("/api/v1").servletPath("/usuarios/me")
				.header(HttpHeaders.ORIGIN, "http://localhost:5173")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
		mvc.perform(options("/api/v1/usuarios/me").contextPath("/api/v1").servletPath("/usuarios/me")
				.header(HttpHeaders.ORIGIN, "https://origem-desconhecida.example")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
				.andExpect(status().isForbidden());
	}

	private String login(String email, String senha) throws Exception {
		var response = mvc.perform(postApi("/auth/login").content(mapper.writeValueAsString(Map.of(
				"email", email, "senha", senha))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresIn").value(900))
				.andExpect(jsonPath("$.expiresAt").isNotEmpty())
				.andExpect(jsonPath("$.refreshToken").doesNotExist()).andReturn();
		return mapper.readTree(response.getResponse().getContentAsString()).get("accessToken").asText();
	}

	private Usuario criarUsuario(PerfilUsuario perfil) {
		return usuarios.saveAndFlush(new Usuario("Estudante Teste", novoEmail(), passwords.encode(SENHA), perfil));
	}

	private String novoEmail() {
		return "auth-" + UUID.randomUUID() + "@example.com";
	}

	private JwtClaimsSet.Builder claims(String subject) {
		return JwtClaimsSet.builder().issuer(properties.issuer()).audience(List.of(properties.audience()))
				.subject(subject).issuedAt(Instant.now()).notBefore(Instant.now().minusSeconds(1))
				.expiresAt(Instant.now().plusSeconds(900)).claim("roles", List.of("USUARIO"));
	}

	private String assinar(JwtClaimsSet.Builder claims) {
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
				.getTokenValue();
	}

	private MockHttpServletRequestBuilder postApi(String path) {
		return post("/api/v1" + path).contextPath("/api/v1").servletPath(path).contentType(MediaType.APPLICATION_JSON);
	}

	private MockHttpServletRequestBuilder getApi(String path) {
		return get("/api/v1" + path).contextPath("/api/v1").servletPath(path);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class Routes {
		@Bean
		AdminProbe adminProbe() { return new AdminProbe(); }
	}

	@RestController
	static class AdminProbe {
		@GetMapping("/__test/admin")
		@PreAuthorize("hasRole('ADMINISTRADOR')")
		public Map<String, String> admin() { return Map.of("status", "ok"); }
	}
}
