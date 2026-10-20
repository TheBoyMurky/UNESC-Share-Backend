package br.com.murkyweb.unesc_share.academico.instituicao;

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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import br.com.murkyweb.unesc_share.academico.curso.domain.Curso;
import br.com.murkyweb.unesc_share.academico.curso.repository.CursoRepository;
import br.com.murkyweb.unesc_share.academico.instituicao.domain.Instituicao;
import br.com.murkyweb.unesc_share.academico.instituicao.dto.InstituicaoRequest;
import br.com.murkyweb.unesc_share.academico.instituicao.repository.InstituicaoRepository;
import br.com.murkyweb.unesc_share.academico.instituicao.service.InstituicaoService;
import br.com.murkyweb.unesc_share.security.JwtProperties;
import br.com.murkyweb.unesc_share.usuario.domain.PerfilUsuario;
import br.com.murkyweb.unesc_share.usuario.domain.Usuario;
import br.com.murkyweb.unesc_share.usuario.repository.UsuarioRepository;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InstituicaoApiTests {

	@Autowired private MockMvc mvc;
	@Autowired private ObjectMapper mapper;
	@Autowired private InstituicaoRepository instituicoes;
	@Autowired private CursoRepository cursos;
	@Autowired private UsuarioRepository usuarios;
	@Autowired private PasswordEncoder passwords;
	@Autowired private InstituicaoService service;
	@Autowired private JwtEncoder encoder;
	@Autowired private JwtProperties properties;

	@Test
	void exigeTokenEmTodasAsOperacoes() throws Exception {
		String id = UUID.randomUUID().toString();
		for (var request : List.of(api(HttpMethod.GET, ""), api(HttpMethod.GET, "/" + id),
				api(HttpMethod.POST, ""), api(HttpMethod.PUT, "/" + id), api(HttpMethod.DELETE, "/" + id))) {
			mvc.perform(request).andExpect(status().isUnauthorized());
		}
	}

	@Test
	void todosOsPerfisAutenticadosPodemConsultar() throws Exception {
		var instituicao = criarInstituicao();
		for (var perfil : PerfilUsuario.values()) {
			String token = token(perfil);
			mvc.perform(autorizada(HttpMethod.GET, "/" + instituicao.getId(), token))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.id").value(instituicao.getId().toString()))
					.andExpect(jsonPath("$.nome").value(instituicao.getNome()))
					.andExpect(jsonPath("$.sigla").value(instituicao.getSigla()))
					.andExpect(jsonPath("$.cursos").doesNotExist());
			mvc.perform(autorizada(HttpMethod.GET, "", token))
					.andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
		}
	}

	@Test
	void usuarioEModeradorNaoPodemAlterarNemComCorpoInvalido() throws Exception {
		var instituicao = criarInstituicao();
		for (var perfil : List.of(PerfilUsuario.USUARIO, PerfilUsuario.MODERADOR)) {
			String token = token(perfil);
			mvc.perform(autorizada(HttpMethod.POST, "", token).content("{}"))
					.andExpect(status().isForbidden());
			mvc.perform(autorizada(HttpMethod.PUT, "/" + instituicao.getId(), token).content("{}"))
					.andExpect(status().isForbidden());
			mvc.perform(autorizada(HttpMethod.DELETE, "/" + instituicao.getId(), token))
					.andExpect(status().isForbidden());
		}
		assertTrue(instituicoes.existsById(instituicao.getId()));
	}

	@Test
	void administradorFazLoginECadastraEditaEExcluiInstituicao() throws Exception {
		String senha = "SenhaTeste-123";
		String email = "instituicao-" + UUID.randomUUID() + "@example.com";
		usuarios.saveAndFlush(new Usuario("Administrador Teste", email, passwords.encode(senha), PerfilUsuario.ADMINISTRADOR));
		var login = mvc.perform(post("/api/v1/auth/login").contextPath("/api/v1").servletPath("/auth/login")
				.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("email", email, "senha", senha))))
				.andExpect(status().isOk()).andReturn();
		String token = mapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
		var request = novoRequest();
		var cadastro = mvc.perform(autorizada(HttpMethod.POST, "", token).content(json(request)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.nome").value(request.nome()))
				.andExpect(jsonPath("$.sigla").value(request.sigla())).andReturn();
		UUID id = UUID.fromString(mapper.readTree(cadastro.getResponse().getContentAsString()).get("id").asText());
		assertEquals("http://localhost/api/v1/instituicoes/" + id, cadastro.getResponse().getHeader(HttpHeaders.LOCATION));
		assertTrue(instituicoes.existsById(id));
		var atualizada = novoRequest();
		mvc.perform(autorizada(HttpMethod.PUT, "/" + id, token).content(json(atualizada)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()))
				.andExpect(jsonPath("$.nome").value(atualizada.nome())).andExpect(jsonPath("$.sigla").value(atualizada.sigla()));
		mvc.perform(autorizada(HttpMethod.GET, "/" + id, token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.nome").value(atualizada.nome()));
		mvc.perform(autorizada(HttpMethod.DELETE, "/" + id, token))
				.andExpect(status().isNoContent()).andExpect(content().string(""));
		assertFalse(instituicoes.existsById(id));
		mvc.perform(autorizada(HttpMethod.GET, "/" + id, token)).andExpect(status().isNotFound());
	}

	@Test
	void normalizaEspacosESiglaENaoAceitaIdOuCursosDoCorpo() throws Exception {
		var request = novoRequest();
		UUID idInformado = UUID.randomUUID();
		var result = mvc.perform(autorizada(HttpMethod.POST, "", token(PerfilUsuario.ADMINISTRADOR))
				.content(mapper.writeValueAsString(Map.of("nome", "  " + request.nome() + "  ",
						"sigla", "  " + request.sigla().toLowerCase(java.util.Locale.ROOT) + "  ",
						"id", idInformado, "cursos", List.of(Map.of("nome", "Curso injetado"))))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.nome").value(request.nome()))
				.andExpect(jsonPath("$.sigla").value(request.sigla())).andReturn();
		UUID id = UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
		assertNotEquals(idInformado, id);
		assertFalse(cursos.existsByInstituicaoId(id));
	}

	@Test
	void listaPaginadaTemMetadadosELimiteDeItens() throws Exception {
		criarInstituicao();
		criarInstituicao();
		criarInstituicao();
		criarInstituicao();
		mvc.perform(autorizada(HttpMethod.GET, "", token(PerfilUsuario.USUARIO)).param("page", "1").param("size", "2"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.page.number").value(1)).andExpect(jsonPath("$.page.size").value(2))
				.andExpect(jsonPath("$.page.totalElements").value(instituicoes.count()));
	}

	@Test
	void rejeitaPaginacaoInvalida() throws Exception {
		String token = token(PerfilUsuario.USUARIO);
		for (var parametros : List.of(Map.of("page", "-1"), Map.of("size", "0"), Map.of("size", "101"),
				Map.of("page", "texto"), Map.of("size", "2147483648"))) {
			var request = autorizada(HttpMethod.GET, "", token);
			parametros.forEach(request::param);
			mvc.perform(request).andExpect(status().isBadRequest())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		}
	}

	@Test
	void rejeitaNomeOuSiglaAusentesVaziosOuLongos() throws Exception {
		String token = token(PerfilUsuario.ADMINISTRADOR);
		for (var body : List.of(Map.of(), Map.of("nome", " ", "sigla", "SIGLA"), Map.of("nome", "Nome", "sigla", " "),
				Map.of("nome", "N".repeat(181), "sigla", "SIGLA"), Map.of("nome", "Nome", "sigla", "S".repeat(31)))) {
			mvc.perform(autorizada(HttpMethod.POST, "", token).content(mapper.writeValueAsString(body)))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").isArray());
		}
		var instituicao = criarInstituicao();
		mvc.perform(autorizada(HttpMethod.PUT, "/" + instituicao.getId(), token).content("{\"nome\":\"Apenas nome\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejeitaJsonMalformado() throws Exception {
		mvc.perform(autorizada(HttpMethod.POST, "", token(PerfilUsuario.ADMINISTRADOR)).content("{invalido"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void cadastroRejeitaNomeOuSiglaDuplicadosIgnorandoCaixa() throws Exception {
		var existente = criarInstituicao();
		String token = token(PerfilUsuario.ADMINISTRADOR);
		for (var request : List.of(new InstituicaoRequest(existente.getNome().toUpperCase(java.util.Locale.ROOT), novoRequest().sigla()),
				new InstituicaoRequest(novoRequest().nome(), existente.getSigla().toLowerCase(java.util.Locale.ROOT)))) {
			mvc.perform(autorizada(HttpMethod.POST, "", token).content(json(request)))
					.andExpect(status().isConflict())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		}
	}

	@Test
	void edicaoRejeitaDuplicidadeComOutraInstituicao() throws Exception {
		var existente = criarInstituicao();
		var outra = criarInstituicao();
		String token = token(PerfilUsuario.ADMINISTRADOR);
		for (var request : List.of(new InstituicaoRequest(existente.getNome(), outra.getSigla()),
				new InstituicaoRequest(outra.getNome(), existente.getSigla()))) {
			mvc.perform(autorizada(HttpMethod.PUT, "/" + outra.getId(), token).content(json(request)))
					.andExpect(status().isConflict());
		}
		assertEquals(outra.getNome(), instituicoes.findById(outra.getId()).orElseThrow().getNome());
	}

	@Test
	void permiteReenviarMesmosDadosSemConsiderarProprioIdDuplicado() throws Exception {
		var instituicao = criarInstituicao();
		mvc.perform(autorizada(HttpMethod.PUT, "/" + instituicao.getId(), token(PerfilUsuario.ADMINISTRADOR))
				.content(json(new InstituicaoRequest(instituicao.getNome(), instituicao.getSigla()))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(instituicao.getId().toString()));
	}

	@Test
	void bloqueiaExclusaoComCursosEPreservaVinculos() throws Exception {
		var instituicao = criarInstituicao();
		var curso = cursos.saveAndFlush(new Curso("Curso Teste", "Descricao", instituicao));
		mvc.perform(autorizada(HttpMethod.DELETE, "/" + instituicao.getId(), token(PerfilUsuario.ADMINISTRADOR)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("Instituicao possui cursos vinculados e nao pode ser excluida."));
		assertTrue(instituicoes.existsById(instituicao.getId()));
		assertTrue(cursos.existsById(curso.getId()));
		assertEquals(instituicao.getId(), cursos.findById(curso.getId()).orElseThrow().getInstituicao().getId());
	}

	@Test
	void retorna404ParaUuidInexistenteEmConsultaEdicaoEExclusao() throws Exception {
		String id = "/" + UUID.randomUUID();
		String token = token(PerfilUsuario.ADMINISTRADOR);
		mvc.perform(autorizada(HttpMethod.GET, id, token)).andExpect(status().isNotFound());
		mvc.perform(autorizada(HttpMethod.PUT, id, token).content(json(novoRequest()))).andExpect(status().isNotFound());
		mvc.perform(autorizada(HttpMethod.DELETE, id, token)).andExpect(status().isNotFound());
	}

	@Test
	void retorna400ParaUuidInvalido() throws Exception {
		String token = token(PerfilUsuario.ADMINISTRADOR);
		mvc.perform(autorizada(HttpMethod.GET, "/invalido", token)).andExpect(status().isBadRequest());
		mvc.perform(autorizada(HttpMethod.PUT, "/invalido", token).content(json(novoRequest()))).andExpect(status().isBadRequest());
		mvc.perform(autorizada(HttpMethod.DELETE, "/invalido", token)).andExpect(status().isBadRequest());
	}

	@Test
	@WithMockUser(roles = "USUARIO")
	void servicoTambemProtegeEscritasForaDoController() {
		assertThrows(AccessDeniedException.class, () -> service.cadastrar(novoRequest()));
		assertThrows(AccessDeniedException.class, () -> service.atualizar(UUID.randomUUID(), novoRequest()));
		assertThrows(AccessDeniedException.class, () -> service.excluir(UUID.randomUUID()));
	}

	private Instituicao criarInstituicao() {
		var request = novoRequest();
		return instituicoes.saveAndFlush(new Instituicao(request.nome(), request.sigla()));
	}

	private InstituicaoRequest novoRequest() {
		return new InstituicaoRequest("Instituicao Teste " + UUID.randomUUID(), "I" + UUID.randomUUID().toString().substring(0, 20));
	}

	private String json(InstituicaoRequest request) {
		return mapper.writeValueAsString(request);
	}

	private String token(PerfilUsuario perfil) {
		var claims = JwtClaimsSet.builder().issuer(properties.issuer()).audience(List.of(properties.audience()))
				.subject(UUID.randomUUID().toString()).issuedAt(Instant.now()).notBefore(Instant.now().minusSeconds(1))
				.expiresAt(Instant.now().plusSeconds(900)).claim("roles", List.of(perfil.name())).build();
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private MockHttpServletRequestBuilder api(HttpMethod method, String path) {
		return request(method, "/api/v1/instituicoes" + path).contextPath("/api/v1").servletPath("/instituicoes" + path)
				.contentType(MediaType.APPLICATION_JSON);
	}

	private MockHttpServletRequestBuilder autorizada(HttpMethod method, String path, String token) {
		return api(method, path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}
}
