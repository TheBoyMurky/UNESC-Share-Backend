package br.com.murkyweb.unesc_share.categoria;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
import br.com.murkyweb.unesc_share.academico.disciplina.domain.Disciplina;
import br.com.murkyweb.unesc_share.academico.instituicao.domain.Instituicao;
import br.com.murkyweb.unesc_share.categoria.domain.Categoria;
import br.com.murkyweb.unesc_share.categoria.dto.CategoriaRequest;
import br.com.murkyweb.unesc_share.categoria.repository.CategoriaRepository;
import br.com.murkyweb.unesc_share.categoria.service.CategoriaService;
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
class CategoriaApiTests {

	@Autowired private MockMvc mvc;
	@Autowired private ObjectMapper mapper;
	@Autowired private CategoriaRepository categorias;
	@Autowired private MaterialRepository materiais;
	@Autowired private TorrentRepository torrents;
	@Autowired private MagnetRepository magnets;
	@Autowired private CategoriaService service;
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
	void todosOsPerfisConsultamDtoSemExporMateriais() throws Exception {
		var categoria = criarCategoria();
		for (var perfil : PerfilUsuario.values()) {
			String token = token(perfil);
			mvc.perform(autorizada(HttpMethod.GET, "/" + categoria.getId(), token))
					.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(categoria.getId().toString()))
					.andExpect(jsonPath("$.nome").value(categoria.getNome()))
					.andExpect(jsonPath("$.materiais").doesNotExist());
			mvc.perform(autorizada(HttpMethod.GET, "", token)).andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
		}
	}

	@Test
	void usuarioEModeradorNaoEscrevemMesmoComCorpoInvalido() throws Exception {
		var categoria = criarCategoria();
		for (var perfil : List.of(PerfilUsuario.USUARIO, PerfilUsuario.MODERADOR)) {
			String token = token(perfil);
			mvc.perform(autorizada(HttpMethod.POST, "", token).content("{}")).andExpect(status().isForbidden());
			mvc.perform(autorizada(HttpMethod.PUT, "/" + categoria.getId(), token).content("{}")).andExpect(status().isForbidden());
			mvc.perform(autorizada(HttpMethod.DELETE, "/" + categoria.getId(), token)).andExpect(status().isForbidden());
		}
		assertTrue(categorias.existsById(categoria.getId()));
	}

	@Test
	void administradorCadastraNormalizaConsultaEditaEExcluiCategoria() throws Exception {
		String nome = novoNome();
		String token = token(PerfilUsuario.ADMINISTRADOR);
		var result = mvc.perform(autorizada(HttpMethod.POST, "", token).content(json(new CategoriaRequest("  " + nome + "  ", " Descricao "))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.nome").value(nome))
				.andExpect(jsonPath("$.descricao").value("Descricao")).andReturn();
		UUID id = UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
		assertEquals("http://localhost/api/v1/categorias/" + id, result.getResponse().getHeader(HttpHeaders.LOCATION));
		mvc.perform(autorizada(HttpMethod.GET, "/" + id, token)).andExpect(status().isOk());
		String novoNome = novoNome();
		mvc.perform(autorizada(HttpMethod.PUT, "/" + id, token).content(json(new CategoriaRequest(novoNome, "Nova descricao"))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()))
				.andExpect(jsonPath("$.nome").value(novoNome)).andExpect(jsonPath("$.descricao").value("Nova descricao"));
		em.clear();
		assertEquals(novoNome, categorias.findById(id).orElseThrow().getNome());
		mvc.perform(autorizada(HttpMethod.DELETE, "/" + id, token)).andExpect(status().isNoContent()).andExpect(content().string(""));
		assertFalse(categorias.existsById(id));
	}

	@Test
	void listagemPaginadaSegueOrdenacaoPorNomeEId() throws Exception {
		for (int i = 0; i < 4; i++) { criarCategoria(); }
		var esperadas = categorias.findAll(PageRequest.of(1, 2, Sort.by("nome", "id"))).getContent();
		mvc.perform(autorizada(HttpMethod.GET, "", token(PerfilUsuario.USUARIO)).param("page", "1").param("size", "2"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.content[0].id").value(esperadas.get(0).getId().toString()))
				.andExpect(jsonPath("$.content[1].id").value(esperadas.get(1).getId().toString()))
				.andExpect(jsonPath("$.page.number").value(1)).andExpect(jsonPath("$.page.size").value(2))
				.andExpect(jsonPath("$.page.totalElements").value(categorias.count()));
	}

	@Test
	void usaPaginacaoPadraoAceitaLimiteMaximoEPaginaVazia() throws Exception {
		criarCategoria();
		String token = token(PerfilUsuario.USUARIO);
		mvc.perform(autorizada(HttpMethod.GET, "", token)).andExpect(status().isOk())
				.andExpect(jsonPath("$.page.number").value(0)).andExpect(jsonPath("$.page.size").value(20));
		mvc.perform(autorizada(HttpMethod.GET, "", token).param("size", "100"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.page.size").value(100));
		mvc.perform(autorizada(HttpMethod.GET, "", token).param("page", "100000"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
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
	void rejeitaNomeAusenteBrancoOuCamposAcimaDosLimites() throws Exception {
		String token = token(PerfilUsuario.ADMINISTRADOR);
		for (var request : List.of(new CategoriaRequest(null, null), new CategoriaRequest(" ", null),
				new CategoriaRequest("N".repeat(81), null), new CategoriaRequest(novoNome(), "D".repeat(501)))) {
			mvc.perform(autorizada(HttpMethod.POST, "", token).content(json(request)))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").isArray());
		}
		mvc.perform(autorizada(HttpMethod.PUT, "/" + criarCategoria().getId(), token).content("{}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void aceitaLimitesDeOitentaEQuinhentosCaracteres() throws Exception {
		String nome = "N".repeat(44) + UUID.randomUUID();
		mvc.perform(autorizada(HttpMethod.POST, "", token(PerfilUsuario.ADMINISTRADOR))
				.content(json(new CategoriaRequest(nome, "D".repeat(500)))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.nome").value(nome));
	}

	@Test
	void descricaoEOpcionalEPodeSerRemovidaNaEdicao() throws Exception {
		var categoria = criarCategoria();
		String token = token(PerfilUsuario.ADMINISTRADOR);
		mvc.perform(autorizada(HttpMethod.PUT, "/" + categoria.getId(), token).content(json(new CategoriaRequest(categoria.getNome(), " "))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.descricao").value(nullValue()));
		mvc.perform(autorizada(HttpMethod.POST, "", token).content(json(new CategoriaRequest(novoNome(), null))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.descricao").value(nullValue()));
	}

	@Test
	void rejeitaJsonMalformado() throws Exception {
		mvc.perform(autorizada(HttpMethod.POST, "", token(PerfilUsuario.ADMINISTRADOR)).content("{invalido"))
				.andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void nomeDuplicadoIgnorandoCaixaRetorna409() throws Exception {
		var categoria = criarCategoria();
		mvc.perform(autorizada(HttpMethod.POST, "", token(PerfilUsuario.ADMINISTRADOR))
				.content(json(new CategoriaRequest(categoria.getNome().toUpperCase(Locale.ROOT), null))))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("Ja existe categoria com esse nome."));
	}

	@Test
	void edicaoRejeitaDuplicidadeMasPermiteManterProprioNome() throws Exception {
		var primeira = criarCategoria();
		var segunda = criarCategoria();
		String token = token(PerfilUsuario.ADMINISTRADOR);
		mvc.perform(autorizada(HttpMethod.PUT, "/" + segunda.getId(), token)
				.content(json(new CategoriaRequest(primeira.getNome().toUpperCase(Locale.ROOT), null)))).andExpect(status().isConflict());
		mvc.perform(autorizada(HttpMethod.PUT, "/" + segunda.getId(), token)
				.content(json(new CategoriaRequest(segunda.getNome(), "Descricao editada"))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(segunda.getId().toString()));
	}

	@Test
	void edicaoPreservaUuidEVinculoDeMaterial() throws Exception {
		var categoria = criarCategoria();
		var material = criarMaterial(categoria);
		String novoNome = novoNome();
		mvc.perform(autorizada(HttpMethod.PUT, "/" + categoria.getId(), token(PerfilUsuario.ADMINISTRADOR))
				.content(json(new CategoriaRequest(novoNome, "Descricao editada"))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(categoria.getId().toString()));
		em.clear();
		assertEquals(novoNome, categorias.findById(categoria.getId()).orElseThrow().getNome());
		assertEquals(categoria.getId(), materiais.findById(material.getId()).orElseThrow().getCategoria().getId());
	}

	@ParameterizedTest
	@EnumSource(StatusMaterial.class)
	void qualquerStatusDeMaterialImpedeExclusaoEPreservaTorrentEMagnet(StatusMaterial status) throws Exception {
		var categoria = criarCategoria();
		var material = criarMaterial(categoria);
		material.alterarStatus(status);
		materiais.saveAndFlush(material);
		String hash = UUID.randomUUID().toString().replace("-", "") + "01234567";
		torrents.saveAndFlush(new Torrent("teste.torrent", "testes/categoria.torrent", hash, 123, material));
		magnets.saveAndFlush(new Magnet("magnet:?xt=urn:btih:" + hash, hash, material));
		mvc.perform(autorizada(HttpMethod.DELETE, "/" + categoria.getId(), token(PerfilUsuario.ADMINISTRADOR)))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("Categoria possui materiais vinculados e nao pode ser excluida."));
		em.clear();
		assertTrue(categorias.existsById(categoria.getId()));
		assertEquals(status, materiais.findById(material.getId()).orElseThrow().getStatus());
		assertEquals(categoria.getId(), materiais.findById(material.getId()).orElseThrow().getCategoria().getId());
		assertEquals(1, torrents.findAllByMaterialIdOrderByDataCadastroAsc(material.getId()).size());
		assertEquals(1, magnets.findAllByMaterialIdOrderByDataCadastroAsc(material.getId()).size());
	}

	@Test
	void uuidInexistenteRetorna404EmConsultaEdicaoEExclusao() throws Exception {
		String id = "/" + UUID.randomUUID();
		String token = token(PerfilUsuario.ADMINISTRADOR);
		mvc.perform(autorizada(HttpMethod.GET, id, token)).andExpect(status().isNotFound());
		mvc.perform(autorizada(HttpMethod.PUT, id, token).content(json(new CategoriaRequest(novoNome(), null)))).andExpect(status().isNotFound());
		mvc.perform(autorizada(HttpMethod.DELETE, id, token)).andExpect(status().isNotFound());
	}

	@Test
	void uuidMalformadoRetorna400() throws Exception {
		String token = token(PerfilUsuario.ADMINISTRADOR);
		mvc.perform(autorizada(HttpMethod.GET, "/invalido", token)).andExpect(status().isBadRequest());
		mvc.perform(autorizada(HttpMethod.PUT, "/invalido", token).content(json(new CategoriaRequest(novoNome(), null)))).andExpect(status().isBadRequest());
		mvc.perform(autorizada(HttpMethod.DELETE, "/invalido", token)).andExpect(status().isBadRequest());
	}

	@Test
	void naoAceitaIdOuMateriaisInjetadosNoCorpo() throws Exception {
		UUID idInformado = UUID.randomUUID();
		var result = mvc.perform(autorizada(HttpMethod.POST, "", token(PerfilUsuario.ADMINISTRADOR))
				.content(mapper.writeValueAsString(Map.of("nome", novoNome(), "id", idInformado,
						"materiais", List.of(Map.of("titulo", "Material injetado"))))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.materiais").doesNotExist()).andReturn();
		UUID id = UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
		assertNotEquals(idInformado, id);
		assertFalse(materiais.existsByCategoriaId(id));
	}

	@Test
	@WithMockUser(roles = "USUARIO")
	void servicoTambemProtegeEscritasForaDoController() {
		var request = new CategoriaRequest(novoNome(), null);
		assertThrows(AccessDeniedException.class, () -> service.cadastrar(request));
		assertThrows(AccessDeniedException.class, () -> service.atualizar(UUID.randomUUID(), request));
		assertThrows(AccessDeniedException.class, () -> service.excluir(UUID.randomUUID()));
	}

	private Categoria criarCategoria() { return categorias.saveAndFlush(new Categoria(novoNome(), "Descricao Teste")); }

	private String novoNome() { return "Categoria Teste " + UUID.randomUUID(); }

	private Material criarMaterial(Categoria categoria) {
		String sufixo = UUID.randomUUID().toString();
		var instituicao = new Instituicao("Instituicao Categoria " + sufixo, "K" + sufixo.substring(0, 20));
		em.persist(instituicao);
		var curso = new Curso("Curso Teste", null, instituicao);
		em.persist(curso);
		var disciplina = new Disciplina("Disciplina Teste", null, "CAT-TEST", curso);
		em.persist(disciplina);
		var usuario = new Usuario("Usuario Teste", sufixo + "@example.test", "hash-sintetico-exclusivo-para-persistencia", PerfilUsuario.USUARIO);
		em.persist(usuario);
		return materiais.saveAndFlush(new Material("Material Teste", "Somente metadados academicos.", usuario, disciplina, categoria));
	}

	private String json(CategoriaRequest request) { return mapper.writeValueAsString(request); }

	private String token(PerfilUsuario perfil) {
		var claims = JwtClaimsSet.builder().issuer(properties.issuer()).audience(List.of(properties.audience()))
				.subject(UUID.randomUUID().toString()).issuedAt(Instant.now()).notBefore(Instant.now().minusSeconds(1))
				.expiresAt(Instant.now().plusSeconds(900)).claim("roles", List.of(perfil.name())).build();
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private MockHttpServletRequestBuilder api(HttpMethod method, String path) {
		return request(method, "/api/v1/categorias" + path).contextPath("/api/v1").servletPath("/categorias" + path)
				.contentType(MediaType.APPLICATION_JSON);
	}

	private MockHttpServletRequestBuilder autorizada(HttpMethod method, String path, String token) {
		return api(method, path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}
}
