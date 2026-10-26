package br.com.murkyweb.unesc_share.material;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import br.com.murkyweb.unesc_share.interacao.denuncia.domain.Denuncia;
import br.com.murkyweb.unesc_share.interacao.denuncia.domain.MotivoDenuncia;
import br.com.murkyweb.unesc_share.interacao.denuncia.domain.StatusDenuncia;
import br.com.murkyweb.unesc_share.interacao.denuncia.dto.DenunciaRequest;
import br.com.murkyweb.unesc_share.interacao.denuncia.repository.DenunciaRepository;
import br.com.murkyweb.unesc_share.interacao.denuncia.service.DenunciaService;
import br.com.murkyweb.unesc_share.material.domain.Material;
import br.com.murkyweb.unesc_share.material.domain.StatusMaterial;
import br.com.murkyweb.unesc_share.usuario.domain.PerfilUsuario;
import br.com.murkyweb.unesc_share.usuario.domain.Usuario;

class DenunciaApiTests extends MaterialApiSupport {
	@Autowired DenunciaRepository denuncias;
	@Autowired DenunciaService service;
	private String path(Material material) { return "/" + material.getId() + "/denuncias"; }
	private String body(MotivoDenuncia motivo, String descricao) { return json(new DenunciaRequest(motivo, descricao)); }
	private Denuncia denuncia(Material material, Usuario autor, StatusDenuncia status) {
		var denuncia = new Denuncia(MotivoDenuncia.SPAM, "Descricao privada", autor, material); denuncia.alterarStatus(status);
		return denuncias.saveAndFlush(denuncia);
	}
	private MockHttpServletRequestBuilder denReq(HttpMethod method, String path, Usuario usuario) {
		var req = request(method, "/api/v1/denuncias" + path).contextPath("/api/v1").servletPath("/denuncias" + path).contentType(MediaType.APPLICATION_JSON);
		if (usuario != null) { req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token(usuario)); } return req;
	}
	@Test void qualquerPerfilInclusiveDonoDenunciaSemAlterarMaterialComDtoELocationConsultavel() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var material = material(dono, base(), StatusMaterial.PUBLICADO);
		var data = material.getDataCadastro(); var titulo = material.getTitulo();
		for (var autor : List.of(dono, usuario(PerfilUsuario.USUARIO), usuario(PerfilUsuario.MODERADOR), usuario(PerfilUsuario.ADMINISTRADOR))) {
			var response = mvc.perform(req(HttpMethod.POST, path(material), autor).content(body(MotivoDenuncia.SPAM, "  Contexto da denuncia  ")))
					.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDENTE"))
					.andExpect(jsonPath("$.descricao").value("Contexto da denuncia")).andExpect(jsonPath("$.motivo").value("SPAM"))
					.andExpect(jsonPath("$.materialId").value(material.getId().toString())).andReturn().getResponse();
			var node = mapper.readTree(response.getContentAsString()); UUID id = UUID.fromString(node.get("id").asText());
			assertEquals(6, node.size()); assertNotNull(java.time.Instant.parse(node.get("dataCadastro").asText()));
			assertEquals("http://localhost/api/v1/denuncias/" + id, response.getHeader("Location"));
			assertEquals(autor.getId(), denuncias.findById(id).orElseThrow().getUsuario().getId());
			assertFalse(response.getContentAsString().contains(autor.getEmail())); assertFalse(response.getContentAsString().contains("usuarioId"));
			mvc.perform(denReq(HttpMethod.GET, "/" + id, autor)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()));
		}
		assertEquals(StatusMaterial.PUBLICADO, material.getStatus()); assertEquals(data, material.getDataCadastro()); assertEquals(titulo, material.getTitulo());
		assertEquals(0, material.getVisualizacoes()); assertEquals(1, magnets.countByMaterialId(material.getId()));
	}
	@Test void aceitaTodosOsMotivosDefinidos() throws Exception {
		var material = material(usuario(PerfilUsuario.USUARIO), base(), StatusMaterial.PUBLICADO);
		for (var motivo : MotivoDenuncia.values()) {
			mvc.perform(req(HttpMethod.POST, path(material), usuario(PerfilUsuario.USUARIO)).content(body(motivo, "Contexto obrigatorio")))
					.andExpect(status().isCreated()).andExpect(jsonPath("$.motivo").value(motivo.name()));
		}
	}
	@Test void camposExtrasNaoEscolhemAutorMaterialStatusIdOuData() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO); var base = base();
		var material = material(dono, base, StatusMaterial.PUBLICADO); var outro = material(dono, base, StatusMaterial.PUBLICADO); UUID fake = UUID.randomUUID();
		var response = mvc.perform(req(HttpMethod.POST, path(material), autor).content(json(Map.of("motivo", "SPAM", "descricao", "Relato valido",
				"usuarioId", dono.getId(), "materialId", outro.getId(), "status", "PROCEDENTE", "id", fake, "dataCadastro", "2000-01-01T00:00:00Z"))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDENTE"))
				.andExpect(jsonPath("$.materialId").value(material.getId().toString())).andReturn().getResponse();
		var node = mapper.readTree(response.getContentAsString()); UUID id = UUID.fromString(node.get("id").asText()); assertNotEquals(fake, id);
		assertEquals(autor.getId(), denuncias.findById(id).orElseThrow().getUsuario().getId());
		assertTrue(java.time.Instant.parse(node.get("dataCadastro").asText()).isAfter(java.time.Instant.parse("2020-01-01T00:00:00Z")));
		assertFalse(denuncias.existsByUsuarioIdAndMaterialIdAndStatusIn(autor.getId(), outro.getId(), List.of(StatusDenuncia.PENDENTE)));
	}
	@Test void descricaoObrigatoriaAte2000SemNuloComErrosSemEcoarValor() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var material = material(autor, base(), StatusMaterial.PUBLICADO); long before = denuncias.count();
		for (String descricao : new String[]{null, "", " \n\t ", "X".repeat(2001), "DESCRICAO_REJEITADA_CANARY\u0000"}) {
			var response = mvc.perform(req(HttpMethod.POST, path(material), autor).content(body(MotivoDenuncia.OUTRO, descricao)))
					.andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).andReturn().getResponse();
			assertFalse(response.getContentAsString().contains("DESCRICAO_REJEITADA_CANARY"));
		}
		mvc.perform(req(HttpMethod.POST, path(material), autor).content("{\"motivo\":\"SPAM\"}")).andExpect(status().isBadRequest());
		assertEquals(before, denuncias.count());
	}
	@Test void motivoObrigatorioValidoEJsonBemFormado() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var material = material(autor, base(), StatusMaterial.PUBLICADO);
		for (String content : List.of(body(null, "Descricao"), "{}", "{invalido", "{\"motivo\":\"NAO_EXISTE\",\"descricao\":\"Descricao\"}",
				"{\"motivo\":true,\"descricao\":\"Descricao\"}")) {
			mvc.perform(req(HttpMethod.POST, path(material), autor).content(content)).andExpect(status().isBadRequest());
		}
	}
	@Test void aceitaLimite2000VendorJsonETextoLiteralSemRenderizarHtml() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var material = material(autor, base(), StatusMaterial.PUBLICADO);
		String literal = "<script>alert('literal')</script>"; String descricao = literal + "X".repeat(2000 - literal.length());
		mvc.perform(req(HttpMethod.POST, path(material), autor).contentType("application/vnd.unescshare+json").content(body(MotivoDenuncia.OUTRO, descricao)))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.descricao").value(descricao));
		mvc.perform(req(HttpMethod.POST, path(material), autor).contentType("application/pdf").content("%PDF-1.7")).andExpect(status().isUnsupportedMediaType());
	}
	@Test void sujeitoNaoPersistidoNaoCadastra() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO); var material = material(dono, base(), StatusMaterial.PUBLICADO);
		var request = req(HttpMethod.POST, path(material), autor).content(body(MotivoDenuncia.SPAM, "Descricao")); em.remove(autor); em.flush();
		mvc.perform(request).andExpect(status().isUnauthorized());
	}
	@Test void naoCadastraEmNaoPublicadoParaQualquerPerfilInclusiveAutor() throws Exception {
		var base = base();
		for (var perfil : PerfilUsuario.values()) {
			var dono = usuario(perfil);
			for (var state : List.of(StatusMaterial.PENDENTE, StatusMaterial.BLOQUEADO, StatusMaterial.REMOVIDO)) {
				var material = material(dono, base, state);
				for (var autor : List.of(dono, usuario(perfil))) {
					mvc.perform(req(HttpMethod.POST, path(material), autor).content(body(MotivoDenuncia.SPAM, "Descricao"))).andExpect(status().isNotFound());
				}
				assertEquals(state, material.getStatus());
			}
		}
	}
	@Test void pendenteOuEmAnaliseImpedeNovoRelatoMesmoComOutroMotivoSemAlterarOriginal() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO); var base = base();
		for (var state : List.of(StatusDenuncia.PENDENTE, StatusDenuncia.EM_ANALISE)) {
			var material = material(dono, base, StatusMaterial.PUBLICADO); var original = denuncia(material, autor, state); var data = original.getDataCadastro();
			for (var motivo : MotivoDenuncia.values()) {
				mvc.perform(req(HttpMethod.POST, path(material), autor).content(body(motivo, "Novo relato"))).andExpect(status().isConflict());
			}
			em.refresh(original); assertEquals(state, original.getStatus()); assertEquals("Descricao privada", original.getDescricao()); assertEquals(data, original.getDataCadastro());
			assertEquals(1, denuncias.findAllByMaterialId(material.getId(), org.springframework.data.domain.Pageable.unpaged()).getTotalElements());
		}
	}
	@Test void concluidaPermiteNovoRelatoPendenteSemRestaurarOuApagarAnterior() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO); var base = base();
		for (var state : List.of(StatusDenuncia.PROCEDENTE, StatusDenuncia.IMPROCEDENTE)) {
			var material = material(dono, base, StatusMaterial.PUBLICADO); var original = denuncia(material, autor, state);
			var response = mvc.perform(req(HttpMethod.POST, path(material), autor).content(body(MotivoDenuncia.SPAM, "Novo fato")))
					.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDENTE")).andReturn().getResponse();
			assertNotEquals(original.getId().toString(), mapper.readTree(response.getContentAsString()).get("id").asText());
			assertEquals(state, original.getStatus()); assertTrue(denuncias.existsById(original.getId()));
			mvc.perform(req(HttpMethod.POST, path(material), autor).content(body(MotivoDenuncia.OUTRO, "Outro fato"))).andExpect(status().isConflict());
		}
	}
	@Test void duplicidadeNaoImpedeOutrosUsuariosOuMateriais() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO); var outro = usuario(PerfilUsuario.USUARIO); var base = base();
		var primeiro = material(dono, base, StatusMaterial.PUBLICADO); var segundo = material(dono, base, StatusMaterial.PUBLICADO);
		denuncia(primeiro, autor, StatusDenuncia.PENDENTE);
		mvc.perform(req(HttpMethod.POST, path(primeiro), outro).content(body(MotivoDenuncia.SPAM, "Relato independente"))).andExpect(status().isCreated());
		mvc.perform(req(HttpMethod.POST, path(segundo), autor).content(body(MotivoDenuncia.SPAM, "Outro material"))).andExpect(status().isCreated());
	}
	@Test void minhasMantemRelatosDeTodosOsEstadosSemExporMaterialPrivadoOuOutroDenunciante() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO); var outro = usuario(PerfilUsuario.USUARIO); var base = base();
		for (var materialState : StatusMaterial.values()) {
			for (var state : StatusDenuncia.values()) {
				var material = material(dono, base, materialState);
				denuncia(material, autor, state); denuncia(material, outro, StatusDenuncia.PENDENTE);
			}
		}
		var response = mvc.perform(denReq(HttpMethod.GET, "/minhas", autor)).andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(16)).andExpect(jsonPath("$.page.totalElements").value(16)).andExpect(jsonPath("$.page.size").value(20)).andReturn().getResponse();
		var node = mapper.readTree(response.getContentAsString());
		for (var item : node.get("content")) {
			assertEquals(6, item.size()); UUID id = UUID.fromString(item.get("id").asText()); assertEquals(autor.getId(), denuncias.findById(id).orElseThrow().getUsuario().getId());
		}
		assertFalse(response.getContentAsString().contains("titulo")); assertFalse(response.getContentAsString().contains("usuarioId"));
		assertFalse(response.getContentAsString().contains(dono.getEmail())); assertFalse(response.getContentAsString().contains("magnetUri"));
	}
	@Test void detalhesPropriosPersistemQuandoMaterialDeixaDeSerPublicado() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO); var base = base();
		for (var state : StatusMaterial.values()) {
			var material = material(dono, base, StatusMaterial.PUBLICADO); var propria = denuncia(material, autor, StatusDenuncia.PENDENTE);
			material.alterarStatus(state); materiais.flush();
			mvc.perform(denReq(HttpMethod.GET, "/" + propria.getId(), autor)).andExpect(status().isOk()).andExpect(jsonPath("$.descricao").value("Descricao privada"));
		}
	}
	@Test void donoEquipeETerceirosNaoConsultamRelatoAlheioMesmoComIdentidadeForjada() throws Exception {
		var dono = usuario(PerfilUsuario.ADMINISTRADOR); var autor = usuario(PerfilUsuario.USUARIO);
		var material = material(dono, base(), StatusMaterial.PUBLICADO); var privada = denuncia(material, autor, StatusDenuncia.PENDENTE);
		for (var leitor : List.of(dono, usuario(PerfilUsuario.USUARIO), usuario(PerfilUsuario.MODERADOR), usuario(PerfilUsuario.ADMINISTRADOR))) {
			var response = mvc.perform(denReq(HttpMethod.GET, "/" + privada.getId(), leitor).param("usuarioId", autor.getId().toString()))
					.andExpect(status().isNotFound()).andReturn().getResponse(); assertFalse(response.getContentAsString().contains("Descricao privada"));
			mvc.perform(denReq(HttpMethod.GET, "/minhas", leitor).param("usuarioId", autor.getId().toString()).param("materialId", material.getId().toString()).param("status", "PENDENTE"))
					.andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.page.totalElements").value(0));
		}
	}
	@Test void paginacaoEOrdenacaoSaoFixasSemFiltrarPorParametrosExtras() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var material = material(autor, base(), StatusMaterial.PUBLICADO);
		denuncia(material, autor, StatusDenuncia.PROCEDENTE); var segunda = denuncia(material, autor, StatusDenuncia.IMPROCEDENTE);
		mvc.perform(denReq(HttpMethod.GET, "/minhas", autor).param("page", "1").param("size", "1").param("sort", "id,desc")
				.param("status", "PENDENTE").param("materialId", UUID.randomUUID().toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(segunda.getId().toString())).andExpect(jsonPath("$.page.totalElements").value(2))
				.andExpect(jsonPath("$.page.totalPages").value(2));
		for (var param : List.of(Map.of("page", "-1"), Map.of("page", "abc"), Map.of("size", "0"), Map.of("size", "101"))) {
			var req = denReq(HttpMethod.GET, "/minhas", autor); param.forEach(req::param); mvc.perform(req).andExpect(status().isBadRequest());
		}
		mvc.perform(denReq(HttpMethod.GET, "/minhas", autor).param("size", "100")).andExpect(status().isOk());
	}
	@Test void todasAsRotasExigemJwt() throws Exception {
		mvc.perform(req(HttpMethod.POST, "/" + UUID.randomUUID() + "/denuncias", null).content(body(MotivoDenuncia.SPAM, "Descricao"))).andExpect(status().isUnauthorized());
		mvc.perform(denReq(HttpMethod.GET, "/minhas", null)).andExpect(status().isUnauthorized());
		mvc.perform(denReq(HttpMethod.GET, "/" + UUID.randomUUID(), null)).andExpect(status().isUnauthorized());
	}
	@Test void inexistentesEUuidsInvalidosSao404Ou400() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO);
		for (String id : List.of(UUID.randomUUID().toString(), "invalido")) {
			int expected = id.equals("invalido") ? 400 : 404;
			mvc.perform(req(HttpMethod.POST, "/" + id + "/denuncias", autor).content(body(MotivoDenuncia.SPAM, "Descricao"))).andExpect(status().is(expected));
			mvc.perform(denReq(HttpMethod.GET, "/" + id, autor)).andExpect(status().is(expected));
		}
	}
	@Test void naoExistemEdicaoExclusaoCancelamentoListagemGeralOuTransicaoDeEstado() throws Exception {
		var autor = usuario(PerfilUsuario.ADMINISTRADOR); var material = material(autor, base(), StatusMaterial.PUBLICADO); var propria = denuncia(material, autor, StatusDenuncia.PENDENTE);
		for (String path : List.of("/minhas", "/" + propria.getId())) {
			for (var method : List.of(HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH, HttpMethod.POST)) {
				mvc.perform(denReq(method, path, autor).content(json(Map.of("status", "PROCEDENTE")))).andExpect(status().isMethodNotAllowed());
			}
		}
		mvc.perform(denReq(HttpMethod.GET, "", autor)).andExpect(status().isNotFound());
		mvc.perform(req(HttpMethod.GET, path(material), autor)).andExpect(status().isMethodNotAllowed());
		mvc.perform(denReq(HttpMethod.POST, "/" + propria.getId() + "/analise", autor).content("{}")).andExpect(status().isNotFound());
		assertEquals(StatusDenuncia.PENDENTE, propria.getStatus()); assertTrue(denuncias.existsById(propria.getId()));
	}
	@Test void serviceExigeAutenticacaoMesmoSemController() {
		UUID id = UUID.randomUUID(); var page = org.springframework.data.domain.PageRequest.of(0, 20);
		assertThrows(AuthenticationCredentialsNotFoundException.class, () -> service.cadastrar(id, new DenunciaRequest(MotivoDenuncia.SPAM, "Descricao")));
		assertThrows(AuthenticationCredentialsNotFoundException.class, () -> service.minhas(page));
		assertThrows(AuthenticationCredentialsNotFoundException.class, () -> service.buscar(id));
	}
}
