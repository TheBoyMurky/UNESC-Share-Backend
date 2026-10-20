package br.com.murkyweb.unesc_share.academico.curso;

import static org.hamcrest.Matchers.nullValue;
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
import br.com.murkyweb.unesc_share.academico.curso.dto.CursoRequest;
import br.com.murkyweb.unesc_share.academico.curso.repository.CursoRepository;
import br.com.murkyweb.unesc_share.academico.curso.service.CursoService;
import br.com.murkyweb.unesc_share.academico.disciplina.domain.Disciplina;
import br.com.murkyweb.unesc_share.academico.disciplina.repository.DisciplinaRepository;
import br.com.murkyweb.unesc_share.academico.instituicao.domain.Instituicao;
import br.com.murkyweb.unesc_share.academico.instituicao.repository.InstituicaoRepository;
import br.com.murkyweb.unesc_share.security.JwtProperties;
import br.com.murkyweb.unesc_share.usuario.domain.PerfilUsuario;
import jakarta.persistence.EntityManager;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CursoApiTests {

	@Autowired private MockMvc mvc;
	@Autowired private ObjectMapper mapper;
	@Autowired private CursoRepository cursos;
	@Autowired private InstituicaoRepository instituicoes;
	@Autowired private DisciplinaRepository disciplinas;
	@Autowired private CursoService service;
	@Autowired private JwtEncoder encoder;
	@Autowired private JwtProperties properties;
	@Autowired private EntityManager entityManager;

	@Test
	void todasAsOperacoesExigemToken() throws Exception {
		String id = "/" + UUID.randomUUID();
		for (var request : List.of(api(HttpMethod.GET, ""), api(HttpMethod.GET, id), api(HttpMethod.POST, ""),
				api(HttpMethod.PUT, id), api(HttpMethod.DELETE, id))) {
			mvc.perform(request).andExpect(status().isUnauthorized());
		}
	}

	@Test
	void todosOsPerfisPodemConsultarSemExporEntidadesRelacionadas() throws Exception {
		var curso = criarCurso(criarInstituicao(), "Curso Teste");
		for (var perfil : PerfilUsuario.values()) {
			String token = token(perfil);
			mvc.perform(autorizada(HttpMethod.GET, "/" + curso.getId(), token))
					.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(curso.getId().toString()))
					.andExpect(jsonPath("$.instituicaoId").value(curso.getInstituicao().getId().toString()))
					.andExpect(jsonPath("$.instituicao").doesNotExist()).andExpect(jsonPath("$.disciplinas").doesNotExist());
			mvc.perform(autorizada(HttpMethod.GET, "", token)).andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
		}
	}

	@Test
	void usuarioEModeradorNaoEscrevemMesmoComCorpoInvalido() throws Exception {
		var curso = criarCurso(criarInstituicao(), "Curso Teste");
		for (var perfil : List.of(PerfilUsuario.USUARIO, PerfilUsuario.MODERADOR)) {
			String token = token(perfil);
			mvc.perform(autorizada(HttpMethod.POST, "", token).content("{}")).andExpect(status().isForbidden());
			mvc.perform(autorizada(HttpMethod.PUT, "/" + curso.getId(), token).content("{}")).andExpect(status().isForbidden());
			mvc.perform(autorizada(HttpMethod.DELETE, "/" + curso.getId(), token)).andExpect(status().isForbidden());
		}
		assertTrue(cursos.existsById(curso.getId()));
	}

	@Test
	void administradorCadastraConsultaEditaEExcluiCursoPreservandoInstituicao() throws Exception {
		var instituicao = criarInstituicao();
		String token = token(PerfilUsuario.ADMINISTRADOR);
		var cadastro = mvc.perform(autorizada(HttpMethod.POST, "", token)
				.content(json(new CursoRequest("  Curso Teste  ", "  Descricao inicial  ", instituicao.getId()))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.nome").value("Curso Teste"))
				.andExpect(jsonPath("$.descricao").value("Descricao inicial")).andReturn();
		UUID id = UUID.fromString(mapper.readTree(cadastro.getResponse().getContentAsString()).get("id").asText());
		assertEquals("http://localhost/api/v1/cursos/" + id, cadastro.getResponse().getHeader(HttpHeaders.LOCATION));
		mvc.perform(autorizada(HttpMethod.GET, "/" + id, token)).andExpect(status().isOk());
		mvc.perform(autorizada(HttpMethod.PUT, "/" + id, token)
				.content(json(new CursoRequest("Curso Atualizado", "Descricao atualizada", instituicao.getId()))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()))
				.andExpect(jsonPath("$.nome").value("Curso Atualizado")).andExpect(jsonPath("$.descricao").value("Descricao atualizada"));
		entityManager.clear();
		assertEquals("Curso Atualizado", cursos.findById(id).orElseThrow().getNome());
		mvc.perform(autorizada(HttpMethod.DELETE, "/" + id, token)).andExpect(status().isNoContent()).andExpect(content().string(""));
		assertFalse(cursos.existsById(id));
		assertTrue(instituicoes.existsById(instituicao.getId()));
	}

	@Test
	void filtraPorInstituicaoComPaginacaoEOrdenacaoPorNome() throws Exception {
		var instituicao = criarInstituicao();
		for (String nome : List.of("D Curso", "B Curso", "A Curso", "C Curso")) {
			criarCurso(instituicao, nome);
		}
		criarCurso(criarInstituicao(), "A Curso de outra instituicao");
		mvc.perform(autorizada(HttpMethod.GET, "", token(PerfilUsuario.USUARIO))
				.param("instituicaoId", instituicao.getId().toString()).param("page", "1").param("size", "2"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.content[0].nome").value("C Curso")).andExpect(jsonPath("$.content[1].nome").value("D Curso"))
				.andExpect(jsonPath("$.content[0].instituicaoId").value(instituicao.getId().toString()))
				.andExpect(jsonPath("$.page.number").value(1)).andExpect(jsonPath("$.page.size").value(2))
				.andExpect(jsonPath("$.page.totalElements").value(4)).andExpect(jsonPath("$.page.totalPages").value(2));
	}

	@Test
	void listagemSemFiltroUsaPadroesEPaginaAlemDoTotalFicaVazia() throws Exception {
		criarCurso(criarInstituicao(), "Curso Teste");
		String token = token(PerfilUsuario.USUARIO);
		mvc.perform(autorizada(HttpMethod.GET, "", token)).andExpect(status().isOk())
				.andExpect(jsonPath("$.page.number").value(0)).andExpect(jsonPath("$.page.size").value(20))
				.andExpect(jsonPath("$.page.totalElements").value(cursos.count()));
		mvc.perform(autorizada(HttpMethod.GET, "", token).param("page", "100000"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
	}

	@Test
	void filtroComInstituicaoInexistenteRetornaPaginaVazia() throws Exception {
		mvc.perform(autorizada(HttpMethod.GET, "", token(PerfilUsuario.USUARIO)).param("instituicaoId", UUID.randomUUID().toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.page.totalElements").value(0));
	}

	@Test
	void rejeitaPaginacaoOuFiltroMalformados() throws Exception {
		String token = token(PerfilUsuario.USUARIO);
		for (var parametros : List.of(Map.of("page", "-1"), Map.of("size", "0"), Map.of("size", "101"),
				Map.of("page", "texto"), Map.of("instituicaoId", "nao-e-uuid"))) {
			var request = autorizada(HttpMethod.GET, "", token);
			parametros.forEach(request::param);
			mvc.perform(request).andExpect(status().isBadRequest())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		}
	}

	@Test
	void rejeitaCamposObrigatoriosOuAcimaDosLimites() throws Exception {
		UUID instituicaoId = criarInstituicao().getId();
		String token = token(PerfilUsuario.ADMINISTRADOR);
		for (var request : List.of(new CursoRequest(null, null, null), new CursoRequest(" ", null, instituicaoId),
				new CursoRequest("N".repeat(151), null, instituicaoId), new CursoRequest("Curso", "D".repeat(1001), instituicaoId))) {
			mvc.perform(autorizada(HttpMethod.POST, "", token).content(json(request)))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").isArray());
		}
		var curso = criarCurso(instituicoes.findById(instituicaoId).orElseThrow(), "Curso Teste");
		mvc.perform(autorizada(HttpMethod.PUT, "/" + curso.getId(), token).content("{\"nome\":\"Sem instituicao\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void aceitaDescricaoOpcionalEPermiteLimparNaEdicao() throws Exception {
		var instituicao = criarInstituicao();
		String token = token(PerfilUsuario.ADMINISTRADOR);
		var cadastro = mvc.perform(autorizada(HttpMethod.POST, "", token)
				.content(json(new CursoRequest("Curso Sem Descricao", null, instituicao.getId()))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.descricao").value(nullValue())).andReturn();
		String id = mapper.readTree(cadastro.getResponse().getContentAsString()).get("id").asText();
		mvc.perform(autorizada(HttpMethod.PUT, "/" + id, token)
				.content(json(new CursoRequest("Curso Sem Descricao", "Descricao adicionada", instituicao.getId()))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.descricao").value("Descricao adicionada"));
		mvc.perform(autorizada(HttpMethod.PUT, "/" + id, token)
				.content(json(new CursoRequest("Curso Sem Descricao", "   ", instituicao.getId()))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.descricao").value(nullValue()));
	}

	@Test
	void rejeitaJsonMalformadoOuUuidInvalidoNoCorpo() throws Exception {
		String token = token(PerfilUsuario.ADMINISTRADOR);
		for (String body : List.of("{invalido", "{\"nome\":\"Curso\",\"instituicaoId\":\"invalido\"}")) {
			mvc.perform(autorizada(HttpMethod.POST, "", token).content(body)).andExpect(status().isBadRequest())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		}
	}

	@Test
	void cadastroComInstituicaoInexistenteRetorna404() throws Exception {
		mvc.perform(autorizada(HttpMethod.POST, "", token(PerfilUsuario.ADMINISTRADOR))
				.content(json(new CursoRequest("Curso Teste", null, UUID.randomUUID()))))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Instituicao nao encontrada."));
	}

	@Test
	void nomeDuplicadoNaMesmaInstituicaoRetorna409IgnorandoCaixa() throws Exception {
		var instituicao = criarInstituicao();
		criarCurso(instituicao, "Curso Teste");
		mvc.perform(autorizada(HttpMethod.POST, "", token(PerfilUsuario.ADMINISTRADOR))
				.content(json(new CursoRequest("CURSO TESTE", null, instituicao.getId()))))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("Ja existe curso com esse nome na instituicao."));
	}

	@Test
	void mesmoNomeEmInstituicoesDiferentesEPermitido() throws Exception {
		criarCurso(criarInstituicao(), "Curso Teste");
		var outra = criarInstituicao();
		mvc.perform(autorizada(HttpMethod.POST, "", token(PerfilUsuario.ADMINISTRADOR))
				.content(json(new CursoRequest("Curso Teste", null, outra.getId()))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.instituicaoId").value(outra.getId().toString()));
	}

	@Test
	void edicaoRejeitaDuplicidadeMasPermiteManterProprioNome() throws Exception {
		var instituicao = criarInstituicao();
		criarCurso(instituicao, "Primeiro Curso");
		var curso = criarCurso(instituicao, "Segundo Curso");
		String token = token(PerfilUsuario.ADMINISTRADOR);
		mvc.perform(autorizada(HttpMethod.PUT, "/" + curso.getId(), token)
				.content(json(new CursoRequest("PRIMEIRO CURSO", null, instituicao.getId())))).andExpect(status().isConflict());
		mvc.perform(autorizada(HttpMethod.PUT, "/" + curso.getId(), token)
				.content(json(new CursoRequest("Segundo Curso", null, instituicao.getId()))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(curso.getId().toString()));
	}

	@Test
	void edicaoPreservaDisciplinasEProibeTransferenciaDeInstituicao() throws Exception {
		var instituicao = criarInstituicao();
		var outra = criarInstituicao();
		var curso = criarCurso(instituicao, "Curso Teste");
		var disciplina = disciplinas.saveAndFlush(new Disciplina("Disciplina Teste", null, "COD-TESTE", curso));
		String token = token(PerfilUsuario.ADMINISTRADOR);
		mvc.perform(autorizada(HttpMethod.PUT, "/" + curso.getId(), token)
				.content(json(new CursoRequest("Curso Editado", "Descricao", instituicao.getId())))).andExpect(status().isOk());
		mvc.perform(autorizada(HttpMethod.PUT, "/" + curso.getId(), token)
				.content(json(new CursoRequest("Nome Transferido", null, outra.getId()))))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("A instituicao do curso nao pode ser alterada neste incremento."));
		entityManager.clear();
		assertEquals("Curso Editado", cursos.findById(curso.getId()).orElseThrow().getNome());
		assertEquals(instituicao.getId(), cursos.findById(curso.getId()).orElseThrow().getInstituicao().getId());
		assertEquals(curso.getId(), disciplinas.findById(disciplina.getId()).orElseThrow().getCurso().getId());
	}

	@Test
	void naoExcluiCursoComDisciplinasNemRelacionamentos() throws Exception {
		var instituicao = criarInstituicao();
		var curso = criarCurso(instituicao, "Curso Teste");
		var disciplina = disciplinas.saveAndFlush(new Disciplina("Disciplina Teste", null, "COD-TESTE", curso));
		mvc.perform(autorizada(HttpMethod.DELETE, "/" + curso.getId(), token(PerfilUsuario.ADMINISTRADOR)))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("Curso possui disciplinas vinculadas e nao pode ser excluido."));
		assertTrue(cursos.existsById(curso.getId()));
		assertTrue(disciplinas.existsById(disciplina.getId()));
		assertTrue(instituicoes.existsById(instituicao.getId()));
	}

	@Test
	void uuidInexistenteRetorna404NasOperacoesPorId() throws Exception {
		String id = "/" + UUID.randomUUID();
		String token = token(PerfilUsuario.ADMINISTRADOR);
		mvc.perform(autorizada(HttpMethod.GET, id, token)).andExpect(status().isNotFound());
		mvc.perform(autorizada(HttpMethod.PUT, id, token)
				.content(json(new CursoRequest("Curso Teste", null, criarInstituicao().getId())))).andExpect(status().isNotFound());
		mvc.perform(autorizada(HttpMethod.DELETE, id, token)).andExpect(status().isNotFound());
	}

	@Test
	void uuidMalformadoRetorna400NasOperacoesPorId() throws Exception {
		String token = token(PerfilUsuario.ADMINISTRADOR);
		mvc.perform(autorizada(HttpMethod.GET, "/invalido", token)).andExpect(status().isBadRequest());
		mvc.perform(autorizada(HttpMethod.PUT, "/invalido", token)
				.content(json(new CursoRequest("Curso Teste", null, criarInstituicao().getId())))).andExpect(status().isBadRequest());
		mvc.perform(autorizada(HttpMethod.DELETE, "/invalido", token)).andExpect(status().isBadRequest());
	}

	@Test
	void naoAceitaIdOuDisciplinasInjetadosNoCorpo() throws Exception {
		var instituicao = criarInstituicao();
		UUID idInformado = UUID.randomUUID();
		var response = mvc.perform(autorizada(HttpMethod.POST, "", token(PerfilUsuario.ADMINISTRADOR))
				.content(mapper.writeValueAsString(Map.of("nome", "Curso Teste", "instituicaoId", instituicao.getId(),
						"id", idInformado, "disciplinas", List.of(Map.of("nome", "Disciplina injetada"))))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.disciplinas").doesNotExist()).andReturn();
		UUID id = UUID.fromString(mapper.readTree(response.getResponse().getContentAsString()).get("id").asText());
		assertNotEquals(idInformado, id);
		assertFalse(disciplinas.existsByCursoId(id));
	}

	@Test
	@WithMockUser(roles = "USUARIO")
	void servicoProtegeEscritasChamadasForaDoController() {
		var request = new CursoRequest("Curso", null, UUID.randomUUID());
		assertThrows(AccessDeniedException.class, () -> service.cadastrar(request));
		assertThrows(AccessDeniedException.class, () -> service.atualizar(UUID.randomUUID(), request));
		assertThrows(AccessDeniedException.class, () -> service.excluir(UUID.randomUUID()));
	}

	private Instituicao criarInstituicao() {
		return instituicoes.saveAndFlush(new Instituicao("Instituicao Cursos " + UUID.randomUUID(), "C" + UUID.randomUUID().toString().substring(0, 20)));
	}

	private Curso criarCurso(Instituicao instituicao, String nome) {
		return cursos.saveAndFlush(new Curso(nome, "Descricao Teste", instituicao));
	}

	private String json(CursoRequest request) { return mapper.writeValueAsString(request); }

	private String token(PerfilUsuario perfil) {
		var claims = JwtClaimsSet.builder().issuer(properties.issuer()).audience(List.of(properties.audience()))
				.subject(UUID.randomUUID().toString()).issuedAt(Instant.now()).notBefore(Instant.now().minusSeconds(1))
				.expiresAt(Instant.now().plusSeconds(900)).claim("roles", List.of(perfil.name())).build();
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private MockHttpServletRequestBuilder api(HttpMethod method, String path) {
		return request(method, "/api/v1/cursos" + path).contextPath("/api/v1").servletPath("/cursos" + path)
				.contentType(MediaType.APPLICATION_JSON);
	}

	private MockHttpServletRequestBuilder autorizada(HttpMethod method, String path, String token) {
		return api(method, path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}
}
