package br.com.murkyweb.unesc_share.academico.disciplina;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import br.com.murkyweb.unesc_share.academico.curso.repository.CursoRepository;
import br.com.murkyweb.unesc_share.academico.disciplina.domain.Disciplina;
import br.com.murkyweb.unesc_share.academico.disciplina.dto.DisciplinaRequest;
import br.com.murkyweb.unesc_share.academico.disciplina.repository.DisciplinaRepository;
import br.com.murkyweb.unesc_share.academico.disciplina.service.DisciplinaService;
import br.com.murkyweb.unesc_share.academico.instituicao.domain.Instituicao;
import br.com.murkyweb.unesc_share.categoria.domain.Categoria;
import br.com.murkyweb.unesc_share.compartilhamento.magnet.domain.Magnet;
import br.com.murkyweb.unesc_share.compartilhamento.magnet.repository.MagnetRepository;
import br.com.murkyweb.unesc_share.compartilhamento.torrent.domain.Torrent;
import br.com.murkyweb.unesc_share.compartilhamento.torrent.repository.TorrentRepository;
import br.com.murkyweb.unesc_share.material.domain.Material;
import br.com.murkyweb.unesc_share.material.domain.StatusMaterial;
import br.com.murkyweb.unesc_share.material.repository.MaterialRepository;
import br.com.murkyweb.unesc_share.security.JwtProperties;
import br.com.murkyweb.unesc_share.usuario.domain.PerfilUsuario;
import br.com.murkyweb.unesc_share.usuario.domain.Usuario;
import jakarta.persistence.EntityManager;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DisciplinaApiTests {

	@Autowired private MockMvc mvc;
	@Autowired private ObjectMapper mapper;
	@Autowired private DisciplinaRepository disciplinas;
	@Autowired private CursoRepository cursos;
	@Autowired private MaterialRepository materiais;
	@Autowired private TorrentRepository torrents;
	@Autowired private MagnetRepository magnets;
	@Autowired private DisciplinaService service;
	@Autowired private JwtEncoder encoder;
	@Autowired private JwtProperties properties;
	@Autowired private EntityManager em;

	@Test
	void todasAsOperacoesExigemToken() throws Exception {
		String id = "/" + UUID.randomUUID();
		for (var request : List.of(api(HttpMethod.GET, ""), api(HttpMethod.GET, id), api(HttpMethod.POST, ""),
				api(HttpMethod.PUT, id), api(HttpMethod.DELETE, id))) {
			mvc.perform(request).andExpect(status().isUnauthorized());
		}
	}

	@Test
	void todosOsPerfisConsultamDtoSemExporCursoOuMateriais() throws Exception {
		var disciplina = criarDisciplina(criarCurso(), "Disciplina Teste", "COD-01");
		for (var perfil : PerfilUsuario.values()) {
			String token = token(perfil);
			mvc.perform(autorizada(HttpMethod.GET, "/" + disciplina.getId(), token))
					.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(disciplina.getId().toString()))
					.andExpect(jsonPath("$.codigo").value("COD-01"))
					.andExpect(jsonPath("$.cursoId").value(disciplina.getCurso().getId().toString()))
					.andExpect(jsonPath("$.curso").doesNotExist()).andExpect(jsonPath("$.materiais").doesNotExist());
			mvc.perform(autorizada(HttpMethod.GET, "", token)).andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
		}
	}

	@Test
	void usuarioEModeradorNaoEscrevemMesmoComCorpoInvalido() throws Exception {
		var disciplina = criarDisciplina(criarCurso(), "Disciplina Teste", "COD-01");
		for (var perfil : List.of(PerfilUsuario.USUARIO, PerfilUsuario.MODERADOR)) {
			String token = token(perfil);
			mvc.perform(autorizada(HttpMethod.POST, "", token).content("{}")).andExpect(status().isForbidden());
			mvc.perform(autorizada(HttpMethod.PUT, "/" + disciplina.getId(), token).content("{}")).andExpect(status().isForbidden());
			mvc.perform(autorizada(HttpMethod.DELETE, "/" + disciplina.getId(), token)).andExpect(status().isForbidden());
		}
		assertTrue(disciplinas.existsById(disciplina.getId()));
	}

	@Test
	void administradorCadastraNormalizaEditaEExcluiPreservandoCurso() throws Exception {
		var curso = criarCurso();
		String token = token(PerfilUsuario.ADMINISTRADOR);
		var result = mvc.perform(autorizada(HttpMethod.POST, "", token)
				.content(json(new DisciplinaRequest("  Disciplina Teste  ", " Descricao ", " cc-001 ", curso.getId()))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.nome").value("Disciplina Teste"))
				.andExpect(jsonPath("$.descricao").value("Descricao")).andExpect(jsonPath("$.codigo").value("CC-001")).andReturn();
		UUID id = UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
		assertEquals("http://localhost/api/v1/disciplinas/" + id, result.getResponse().getHeader(HttpHeaders.LOCATION));
		mvc.perform(autorizada(HttpMethod.GET, "/" + id, token)).andExpect(status().isOk());
		mvc.perform(autorizada(HttpMethod.PUT, "/" + id, token)
				.content(json(new DisciplinaRequest("Disciplina Editada", "Nova descricao", "cc-002", curso.getId()))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()))
				.andExpect(jsonPath("$.nome").value("Disciplina Editada")).andExpect(jsonPath("$.codigo").value("CC-002"));
		em.clear();
		assertEquals("CC-002", disciplinas.findById(id).orElseThrow().getCodigo());
		mvc.perform(autorizada(HttpMethod.DELETE, "/" + id, token)).andExpect(status().isNoContent()).andExpect(content().string(""));
		assertFalse(disciplinas.existsById(id));
		assertTrue(cursos.existsById(curso.getId()));
	}

	@Test
	void filtraPorCursoComPaginacaoEOrdenacaoPorNome() throws Exception {
		var curso = criarCurso();
		for (String letra : List.of("D", "B", "A", "C")) {
			criarDisciplina(curso, letra + " Disciplina", "COD-" + letra);
		}
		criarDisciplina(criarCurso(), "A Disciplina de outro curso", "COD-A");
		mvc.perform(autorizada(HttpMethod.GET, "", token(PerfilUsuario.USUARIO))
				.param("cursoId", curso.getId().toString()).param("page", "1").param("size", "2"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.content[0].nome").value("C Disciplina"))
				.andExpect(jsonPath("$.content[1].nome").value("D Disciplina"))
				.andExpect(jsonPath("$.content[0].cursoId").value(curso.getId().toString()))
				.andExpect(jsonPath("$.page.number").value(1)).andExpect(jsonPath("$.page.size").value(2))
				.andExpect(jsonPath("$.page.totalElements").value(4)).andExpect(jsonPath("$.page.totalPages").value(2));
	}

	@Test
	void listagemSemFiltroUsaPadroesEPaginaAlemDoTotalFicaVazia() throws Exception {
		criarDisciplina(criarCurso(), "Disciplina Teste", "COD-01");
		String token = token(PerfilUsuario.USUARIO);
		mvc.perform(autorizada(HttpMethod.GET, "", token)).andExpect(status().isOk())
				.andExpect(jsonPath("$.page.number").value(0)).andExpect(jsonPath("$.page.size").value(20))
				.andExpect(jsonPath("$.page.totalElements").value(disciplinas.count()));
		mvc.perform(autorizada(HttpMethod.GET, "", token).param("page", "100000"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
	}

	@Test
	void filtroComCursoInexistenteRetornaPaginaVazia() throws Exception {
		mvc.perform(autorizada(HttpMethod.GET, "", token(PerfilUsuario.USUARIO)).param("cursoId", UUID.randomUUID().toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.page.totalElements").value(0));
	}

	@Test
	void rejeitaPaginacaoOuFiltroMalformados() throws Exception {
		String token = token(PerfilUsuario.USUARIO);
		for (var parametros : List.of(Map.of("page", "-1"), Map.of("size", "0"), Map.of("size", "101"),
				Map.of("page", "texto"), Map.of("cursoId", "invalido"))) {
			var request = autorizada(HttpMethod.GET, "", token);
			parametros.forEach(request::param);
			mvc.perform(request).andExpect(status().isBadRequest())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		}
	}

	@Test
	void rejeitaCamposObrigatoriosOuAcimaDosLimites() throws Exception {
		UUID cursoId = criarCurso().getId();
		String token = token(PerfilUsuario.ADMINISTRADOR);
		for (var request : List.of(new DisciplinaRequest(null, null, null, null), new DisciplinaRequest(" ", null, "COD", cursoId),
				new DisciplinaRequest("Nome", null, " ", cursoId), new DisciplinaRequest("N".repeat(151), null, "COD", cursoId),
				new DisciplinaRequest("Nome", "D".repeat(1001), "COD", cursoId), new DisciplinaRequest("Nome", null, "C".repeat(31), cursoId))) {
			mvc.perform(autorizada(HttpMethod.POST, "", token).content(json(request)))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").isArray());
		}
		var disciplina = criarDisciplina(cursos.findById(cursoId).orElseThrow(), "Disciplina Teste", "COD-01");
		mvc.perform(autorizada(HttpMethod.PUT, "/" + disciplina.getId(), token).content("{\"nome\":\"Sem codigo e curso\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void aceitaDescricaoOpcionalEPermiteLimparNaEdicao() throws Exception {
		var curso = criarCurso();
		String token = token(PerfilUsuario.ADMINISTRADOR);
		var cadastro = mvc.perform(autorizada(HttpMethod.POST, "", token)
				.content(json(new DisciplinaRequest("Disciplina", null, "COD-01", curso.getId()))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.descricao").value(nullValue())).andReturn();
		String id = mapper.readTree(cadastro.getResponse().getContentAsString()).get("id").asText();
		mvc.perform(autorizada(HttpMethod.PUT, "/" + id, token)
				.content(json(new DisciplinaRequest("Disciplina", "Descricao adicionada", "COD-01", curso.getId()))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.descricao").value("Descricao adicionada"));
		mvc.perform(autorizada(HttpMethod.PUT, "/" + id, token)
				.content(json(new DisciplinaRequest("Disciplina", " ", "COD-01", curso.getId()))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.descricao").value(nullValue()));
	}

	@Test
	void rejeitaJsonMalformadoOuCursoIdInvalido() throws Exception {
		String token = token(PerfilUsuario.ADMINISTRADOR);
		for (String body : List.of("{invalido", "{\"nome\":\"Disciplina\",\"codigo\":\"COD\",\"cursoId\":\"invalido\"}")) {
			mvc.perform(autorizada(HttpMethod.POST, "", token).content(body)).andExpect(status().isBadRequest())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		}
	}

	@Test
	void cadastroComCursoInexistenteRetorna404() throws Exception {
		mvc.perform(autorizada(HttpMethod.POST, "", token(PerfilUsuario.ADMINISTRADOR))
				.content(json(new DisciplinaRequest("Disciplina", null, "COD-01", UUID.randomUUID()))))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Curso nao encontrado."));
	}

	@Test
	void codigoDuplicadoNoMesmoCursoRetorna409IgnorandoCaixa() throws Exception {
		var curso = criarCurso();
		criarDisciplina(curso, "Original", "cod-01");
		mvc.perform(autorizada(HttpMethod.POST, "", token(PerfilUsuario.ADMINISTRADOR))
				.content(json(new DisciplinaRequest("Outro nome", null, " COD-01 ", curso.getId()))))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("Ja existe disciplina com esse codigo no curso."));
	}

	@Test
	void mesmoCodigoEmCursosDiferentesEPermitido() throws Exception {
		criarDisciplina(criarCurso(), "Original", "COD-01");
		var outro = criarCurso();
		mvc.perform(autorizada(HttpMethod.POST, "", token(PerfilUsuario.ADMINISTRADOR))
				.content(json(new DisciplinaRequest("Mesmo codigo", null, "COD-01", outro.getId()))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.cursoId").value(outro.getId().toString()));
	}

	@Test
	void mesmoNomeComCodigosDiferentesNoCursoEPermitido() throws Exception {
		var curso = criarCurso();
		criarDisciplina(curso, "Mesmo nome", "COD-01");
		mvc.perform(autorizada(HttpMethod.POST, "", token(PerfilUsuario.ADMINISTRADOR))
				.content(json(new DisciplinaRequest("Mesmo nome", null, "COD-02", curso.getId())))).andExpect(status().isCreated());
	}

	@Test
	void edicaoRejeitaDuplicidadeMasPermiteManterProprioCodigo() throws Exception {
		var curso = criarCurso();
		criarDisciplina(curso, "Primeira", "COD-01");
		var disciplina = criarDisciplina(curso, "Segunda", "COD-02");
		String token = token(PerfilUsuario.ADMINISTRADOR);
		mvc.perform(autorizada(HttpMethod.PUT, "/" + disciplina.getId(), token)
				.content(json(new DisciplinaRequest("Segunda", null, "cod-01", curso.getId())))).andExpect(status().isConflict());
		mvc.perform(autorizada(HttpMethod.PUT, "/" + disciplina.getId(), token)
				.content(json(new DisciplinaRequest("Segunda", null, "cod-02", curso.getId()))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(disciplina.getId().toString()));
	}

	@Test
	void edicaoPreservaMateriaisERecusaTransferenciaDeCurso() throws Exception {
		var curso = criarCurso();
		var outro = criarCurso();
		var disciplina = criarDisciplina(curso, "Disciplina Teste", "COD-01");
		var material = criarMaterial(disciplina);
		String token = token(PerfilUsuario.ADMINISTRADOR);
		mvc.perform(autorizada(HttpMethod.PUT, "/" + disciplina.getId(), token)
				.content(json(new DisciplinaRequest("Disciplina Editada", "Descricao", "COD-02", curso.getId())))).andExpect(status().isOk());
		mvc.perform(autorizada(HttpMethod.PUT, "/" + disciplina.getId(), token)
				.content(json(new DisciplinaRequest("Transferida", null, "COD-03", outro.getId()))))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("O curso da disciplina nao pode ser alterado neste incremento."));
		em.clear();
		var preservada = disciplinas.findById(disciplina.getId()).orElseThrow();
		assertEquals("Disciplina Editada", preservada.getNome());
		assertEquals("COD-02", preservada.getCodigo());
		assertEquals(curso.getId(), preservada.getCurso().getId());
		assertEquals(disciplina.getId(), materiais.findById(material.getId()).orElseThrow().getDisciplina().getId());
	}

	@ParameterizedTest
	@EnumSource(StatusMaterial.class)
	void qualquerStatusDeMaterialBloqueiaExclusaoEPreservaTorrentEMagnet(StatusMaterial status) throws Exception {
		var disciplina = criarDisciplina(criarCurso(), "Disciplina Teste", "COD-01");
		var material = criarMaterial(disciplina);
		material.alterarStatus(status);
		materiais.saveAndFlush(material);
		String hash = UUID.randomUUID().toString().replace("-", "") + "01234567";
		torrents.saveAndFlush(new Torrent("teste.torrent", "testes/metadata.torrent", hash, 123, material));
		magnets.saveAndFlush(new Magnet("magnet:?xt=urn:btih:" + hash, hash, material));
		mvc.perform(autorizada(HttpMethod.DELETE, "/" + disciplina.getId(), token(PerfilUsuario.ADMINISTRADOR)))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("Disciplina possui materiais vinculados e nao pode ser excluida."));
		em.clear();
		assertTrue(disciplinas.existsById(disciplina.getId()));
		assertEquals(status, materiais.findById(material.getId()).orElseThrow().getStatus());
		assertEquals(disciplina.getId(), materiais.findById(material.getId()).orElseThrow().getDisciplina().getId());
		assertEquals(1, torrents.findAllByMaterialIdOrderByDataCadastroAsc(material.getId()).size());
		assertEquals(1, magnets.findAllByMaterialIdOrderByDataCadastroAsc(material.getId()).size());
	}

	@Test
	void uuidInexistenteRetorna404NasOperacoesPorId() throws Exception {
		String id = "/" + UUID.randomUUID();
		String token = token(PerfilUsuario.ADMINISTRADOR);
		mvc.perform(autorizada(HttpMethod.GET, id, token)).andExpect(status().isNotFound());
		mvc.perform(autorizada(HttpMethod.PUT, id, token)
				.content(json(new DisciplinaRequest("Disciplina", null, "COD-01", criarCurso().getId())))).andExpect(status().isNotFound());
		mvc.perform(autorizada(HttpMethod.DELETE, id, token)).andExpect(status().isNotFound());
	}

	@Test
	void uuidMalformadoRetorna400NasOperacoesPorId() throws Exception {
		String token = token(PerfilUsuario.ADMINISTRADOR);
		mvc.perform(autorizada(HttpMethod.GET, "/invalido", token)).andExpect(status().isBadRequest());
		mvc.perform(autorizada(HttpMethod.PUT, "/invalido", token)
				.content(json(new DisciplinaRequest("Disciplina", null, "COD-01", criarCurso().getId())))).andExpect(status().isBadRequest());
		mvc.perform(autorizada(HttpMethod.DELETE, "/invalido", token)).andExpect(status().isBadRequest());
	}

	@Test
	void naoAceitaIdOuMateriaisInjetadosNoCorpo() throws Exception {
		var curso = criarCurso();
		UUID idInformado = UUID.randomUUID();
		var result = mvc.perform(autorizada(HttpMethod.POST, "", token(PerfilUsuario.ADMINISTRADOR))
				.content(mapper.writeValueAsString(Map.of("nome", "Disciplina", "codigo", "COD-01", "cursoId", curso.getId(),
						"id", idInformado, "materiais", List.of(Map.of("titulo", "Material injetado"))))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.materiais").doesNotExist()).andReturn();
		UUID id = UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
		assertNotEquals(idInformado, id);
		assertFalse(materiais.existsByDisciplinaId(id));
	}

	@Test
	@WithMockUser(roles = "USUARIO")
	void servicoProtegeEscritasChamadasForaDoController() {
		var request = new DisciplinaRequest("Disciplina", null, "COD-01", UUID.randomUUID());
		assertThrows(AccessDeniedException.class, () -> service.cadastrar(request));
		assertThrows(AccessDeniedException.class, () -> service.atualizar(UUID.randomUUID(), request));
		assertThrows(AccessDeniedException.class, () -> service.excluir(UUID.randomUUID()));
	}

	private Curso criarCurso() {
		String sufixo = UUID.randomUUID().toString();
		var instituicao = new Instituicao("Instituicao Disciplinas " + sufixo, "D" + sufixo.substring(0, 20));
		em.persist(instituicao);
		return cursos.saveAndFlush(new Curso("Curso Teste", null, instituicao));
	}

	private Disciplina criarDisciplina(Curso curso, String nome, String codigo) {
		return disciplinas.saveAndFlush(new Disciplina(nome, "Descricao Teste", codigo, curso));
	}

	private Material criarMaterial(Disciplina disciplina) {
		String sufixo = UUID.randomUUID().toString();
		var usuario = new Usuario("Usuario Teste", sufixo + "@example.test", "hash-sintetico-exclusivo-para-persistencia", PerfilUsuario.USUARIO);
		var categoria = new Categoria("Categoria Disciplina " + sufixo, null);
		em.persist(usuario);
		em.persist(categoria);
		return materiais.saveAndFlush(new Material("Material Teste", "Somente metadados academicos.", usuario, disciplina, categoria));
	}

	private String json(DisciplinaRequest request) { return mapper.writeValueAsString(request); }

	private String token(PerfilUsuario perfil) {
		var claims = JwtClaimsSet.builder().issuer(properties.issuer()).audience(List.of(properties.audience()))
				.subject(UUID.randomUUID().toString()).issuedAt(Instant.now()).notBefore(Instant.now().minusSeconds(1))
				.expiresAt(Instant.now().plusSeconds(900)).claim("roles", List.of(perfil.name())).build();
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private MockHttpServletRequestBuilder api(HttpMethod method, String path) {
		return request(method, "/api/v1/disciplinas" + path).contextPath("/api/v1").servletPath("/disciplinas" + path)
				.contentType(MediaType.APPLICATION_JSON);
	}

	private MockHttpServletRequestBuilder autorizada(HttpMethod method, String path, String token) {
		return api(method, path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}
}
