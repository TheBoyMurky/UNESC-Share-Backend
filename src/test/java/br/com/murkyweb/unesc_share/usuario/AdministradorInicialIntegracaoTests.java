package br.com.murkyweb.unesc_share.usuario;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import br.com.murkyweb.unesc_share.config.seed.DadosIniciaisRunner;
import br.com.murkyweb.unesc_share.usuario.config.AdministradorInicialRunner;
import br.com.murkyweb.unesc_share.usuario.domain.PerfilUsuario;
import br.com.murkyweb.unesc_share.usuario.domain.Usuario;
import br.com.murkyweb.unesc_share.usuario.dto.CadastroRequest;
import br.com.murkyweb.unesc_share.usuario.repository.UsuarioRepository;
import br.com.murkyweb.unesc_share.usuario.service.AdministradorInicialService;
import jakarta.persistence.EntityManager;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = { "unesc-share.admin-inicial.enabled=false", "unesc-share.seed.enabled=false", "spring.jpa.show-sql=false",
		"logging.level.org.hibernate.SQL=OFF" })
@AutoConfigureMockMvc
@ActiveProfiles({ "dev", "test" })
@Transactional
class AdministradorInicialIntegracaoTests {

	@Autowired private AdministradorInicialService service;
	@Autowired private UsuarioRepository usuarios;
	@Autowired private PasswordEncoder passwords;
	@Autowired private EntityManager entityManager;
	@Autowired private MockMvc mvc;
	@Autowired private ObjectMapper mapper;
	@Autowired private ApplicationContext context;

	@Test
	void criaHashUuidEDataEPreservaContaNaSegundaExecucao() {
		String email = novoEmail();
		long totalAntes = usuarios.count();
		assertTrue(service.provisionar(new CadastroRequest(" Administrador Teste ", " " + email.toUpperCase(java.util.Locale.ROOT) + " ", "SenhaTeste-123")));
		entityManager.clear();
		var administrador = usuarios.findByEmailIgnoreCase(email).orElseThrow();
		UUID id = administrador.getId();
		var dataCadastro = administrador.getDataCadastro();
		String hash = administrador.getSenha();
		assertNotNull(id);
		assertNotNull(dataCadastro);
		assertEquals("Administrador Teste", administrador.getNome());
		assertEquals(email, administrador.getEmail());
		assertEquals(PerfilUsuario.ADMINISTRADOR, administrador.getPerfil());
		assertTrue(hash.startsWith("{bcrypt}"));
		assertTrue(passwords.matches("SenhaTeste-123", hash));
		assertFalse(service.provisionar(new CadastroRequest("Nome diferente", email, "OutraSenha-456")));
		entityManager.flush();
		entityManager.clear();
		var preservado = usuarios.findById(id).orElseThrow();
		assertEquals("Administrador Teste", preservado.getNome());
		assertEquals(hash, preservado.getSenha());
		assertEquals(dataCadastro, preservado.getDataCadastro());
		assertEquals(PerfilUsuario.ADMINISTRADOR, preservado.getPerfil());
		assertEquals(totalAntes + 1, usuarios.count());
	}

	@Test
	void naoPromoveUsuarioOuModeradorExistentes() {
		for (var perfil : new PerfilUsuario[] { PerfilUsuario.USUARIO, PerfilUsuario.MODERADOR }) {
			String email = novoEmail();
			var existente = usuarios.saveAndFlush(new Usuario("Nome original", email, passwords.encode("SenhaOriginal-123"), perfil));
			String hash = existente.getSenha();
			assertThrows(IllegalStateException.class,
					() -> service.provisionar(new CadastroRequest("Administrador", email, "SenhaNova-456")));
			entityManager.clear();
			var preservado = usuarios.findById(existente.getId()).orElseThrow();
			assertEquals(perfil, preservado.getPerfil());
			assertEquals("Nome original", preservado.getNome());
			assertEquals(hash, preservado.getSenha());
		}
	}

	@Test
	void administradorProvisionadoFazLoginEAcessaEscritaDeInstituicoes() throws Exception {
		String email = novoEmail();
		service.provisionar(new CadastroRequest("Administrador Teste", email, "SenhaTeste-123"));
		var login = mvc.perform(postApi("/auth/login").content(mapper.writeValueAsString(Map.of("email", email, "senha", "SenhaTeste-123"))))
				.andExpect(status().isOk()).andReturn();
		String token = mapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
		mvc.perform(get("/api/v1/usuarios/me").contextPath("/api/v1").servletPath("/usuarios/me")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.perfil").value("ADMINISTRADOR"))
				.andExpect(jsonPath("$.senha").doesNotExist());
		mvc.perform(postApi("/instituicoes").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.content(mapper.writeValueAsString(Map.of("nome", "Instituicao Admin " + UUID.randomUUID(),
						"sigla", "A" + UUID.randomUUID().toString().substring(0, 20)))))
				.andExpect(status().isCreated());
	}

	@Test
	void colisaoComCadastroPublicoNaoElevaPerfil() throws Exception {
		String email = novoEmail();
		mvc.perform(postApi("/auth/cadastro").content(mapper.writeValueAsString(Map.of("nome", "Usuario Publico", "email", email,
				"senha", "SenhaPublica-123", "perfil", "ADMINISTRADOR"))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.perfil").value("USUARIO"));
		assertThrows(IllegalStateException.class,
				() -> service.provisionar(new CadastroRequest("Administrador", email, "SenhaNova-456")));
		entityManager.clear();
		var usuario = usuarios.findByEmailIgnoreCase(email).orElseThrow();
		assertEquals(PerfilUsuario.USUARIO, usuario.getPerfil());
		assertTrue(passwords.matches("SenhaPublica-123", usuario.getSenha()));
	}

	@Test
	void testesNaoAtivamRunnersAutomaticamente() {
		assertTrue(context.getBeansOfType(AdministradorInicialRunner.class).isEmpty());
		assertTrue(context.getBeansOfType(DadosIniciaisRunner.class).isEmpty());
	}

	private String novoEmail() { return "admin-inicial-" + UUID.randomUUID() + "@example.com"; }

	private MockHttpServletRequestBuilder postApi(String path) {
		return post("/api/v1" + path).contextPath("/api/v1").servletPath(path).contentType(MediaType.APPLICATION_JSON);
	}
}
