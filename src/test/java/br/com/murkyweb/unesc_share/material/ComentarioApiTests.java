package br.com.murkyweb.unesc_share.material;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import br.com.murkyweb.unesc_share.interacao.comentario.domain.Comentario;
import br.com.murkyweb.unesc_share.interacao.comentario.domain.StatusComentario;
import br.com.murkyweb.unesc_share.interacao.comentario.dto.ComentarioRequest;
import br.com.murkyweb.unesc_share.interacao.comentario.dto.ComentarioResponse;
import br.com.murkyweb.unesc_share.interacao.comentario.repository.ComentarioRepository;
import br.com.murkyweb.unesc_share.interacao.comentario.service.ComentarioService;
import br.com.murkyweb.unesc_share.material.domain.Material;
import br.com.murkyweb.unesc_share.material.domain.StatusMaterial;
import br.com.murkyweb.unesc_share.usuario.domain.PerfilUsuario;
import br.com.murkyweb.unesc_share.usuario.domain.Usuario;

class ComentarioApiTests extends MaterialApiSupport {
	@Autowired private ComentarioRepository comentarios;
	@Autowired private ComentarioService service;
	private String path(Material material) { return "/" + material.getId() + "/comentarios"; }
	private String body(String conteudo) { return json(new ComentarioRequest(conteudo)); }
	private Comentario comentario(Material material, Usuario autor, String texto, StatusComentario status) {
		var comentario = new Comentario(texto, autor, material); comentario.alterarStatus(status);
		return comentarios.saveAndFlush(comentario);
	}

	@Test void qualquerPerfilInclusiveAutorComentaEPublicaImediatamenteSemAlterarMaterial() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var material = material(dono, base(), StatusMaterial.PUBLICADO);
		var data = material.getDataCadastro(); String titulo = material.getTitulo();
		for (var autor : List.of(dono, usuario(PerfilUsuario.USUARIO), usuario(PerfilUsuario.MODERADOR), usuario(PerfilUsuario.ADMINISTRADOR))) {
			var response = mvc.perform(req(HttpMethod.POST, path(material), autor).content(body("  Comentario de teste  ")))
					.andExpect(status().isCreated()).andExpect(jsonPath("$.conteudo").value("Comentario de teste"))
					.andExpect(jsonPath("$.status").value("ATIVO")).andExpect(jsonPath("$.usuarioId").value(autor.getId().toString()))
					.andExpect(jsonPath("$.materialId").value(material.getId().toString())).andExpect(header().doesNotExist("Location"))
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)).andReturn().getResponse();
			var node = mapper.readTree(response.getContentAsString()); UUID id = UUID.fromString(node.get("id").asText());
			assertNotEquals(material.getId(), id); assertNotNull(Instant.parse(node.get("dataCadastro").asText()));
			assertEquals(autor.getId(), comentarios.findById(id).orElseThrow().getUsuario().getId());
		}
		mvc.perform(req(HttpMethod.GET, path(material), dono)).andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(4)).andExpect(jsonPath("$.page.totalElements").value(4));
		assertEquals(StatusMaterial.PUBLICADO, material.getStatus()); assertEquals(data, material.getDataCadastro());
		assertEquals(titulo, material.getTitulo()); assertEquals(0, material.getVisualizacoes());
		assertEquals(1, magnets.countByMaterialId(material.getId()));
	}
	@Test void autorEstadoMaterialEIdDoComentarioNaoSaoEscolhidosPeloJson() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO); var base = base();
		var material = material(dono, base, StatusMaterial.PUBLICADO); var outro = material(dono, base, StatusMaterial.PUBLICADO);
		UUID idForjado = UUID.randomUUID();
		var response = mvc.perform(req(HttpMethod.POST, path(material), autor).content(json(Map.of(
				"conteudo", "Conteudo valido", "usuarioId", dono.getId(), "materialId", outro.getId(), "status", "OCULTO",
				"id", idForjado, "dataCadastro", "2000-01-01T00:00:00Z"))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.usuarioId").value(autor.getId().toString()))
				.andExpect(jsonPath("$.materialId").value(material.getId().toString())).andExpect(jsonPath("$.status").value("ATIVO"))
				.andReturn().getResponse();
		assertNotEquals(idForjado.toString(), mapper.readTree(response.getContentAsString()).get("id").asText());
		assertEquals(0, comentarios.findAllByMaterialIdAndStatus(outro.getId(), StatusComentario.ATIVO,
				org.springframework.data.domain.Pageable.unpaged()).getTotalElements());
	}
	@Test void listaSomenteAtivosDoMaterialDaRotaSemAceitarStatusOuAutorDoCliente() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var base = base(); var material = material(autor, base, StatusMaterial.PUBLICADO);
		var outro = material(autor, base, StatusMaterial.PUBLICADO);
		var ativo = comentario(material, autor, "Ativo visivel", StatusComentario.ATIVO);
		comentario(material, autor, "OCULTO_CANARY", StatusComentario.OCULTO);
		comentario(material, autor, "REMOVIDO_CANARY", StatusComentario.REMOVIDO);
		comentario(outro, autor, "OUTRO_MATERIAL_CANARY", StatusComentario.ATIVO);
		for (var leitor : List.of(autor, usuario(PerfilUsuario.USUARIO), usuario(PerfilUsuario.MODERADOR), usuario(PerfilUsuario.ADMINISTRADOR))) {
			var response = mvc.perform(req(HttpMethod.GET, path(material), leitor).param("status", "OCULTO")
					.param("usuarioId", UUID.randomUUID().toString()).param("materialId", outro.getId().toString()))
					.andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
					.andExpect(jsonPath("$.content[0].id").value(ativo.getId().toString())).andReturn().getResponse().getContentAsString();
			assertFalse(response.contains("CANARY"));
		}
	}
	@Test void materialSemComentariosRetornaPaginaVaziaComPaginacaoPadrao() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var material = material(autor, base(), StatusMaterial.PUBLICADO);
		mvc.perform(req(HttpMethod.GET, path(material), autor)).andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty())
				.andExpect(jsonPath("$.page.number").value(0)).andExpect(jsonPath("$.page.size").value(20))
				.andExpect(jsonPath("$.page.totalElements").value(0));
	}
	@Test void paginaEmOrdemCronologicaComDesempatePorUuidEIgnoraSortDoCliente() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var material = material(autor, base(), StatusMaterial.PUBLICADO);
		var a = comentario(material, autor, "A", StatusComentario.ATIVO);
		var b = comentario(material, autor, "B", StatusComentario.ATIVO);
		var c = comentario(material, autor, "C", StatusComentario.ATIVO);
		em.createNativeQuery("update comentarios set data_cadastro = :data where material_id = :id")
				.setParameter("data", Instant.parse("2026-01-01T00:00:00Z")).setParameter("id", material.getId()).executeUpdate();
		em.clear();
		var expected = List.of(a.getId().toString(), b.getId().toString(), c.getId().toString()).stream().sorted().toList();
		var first = mvc.perform(req(HttpMethod.GET, path(material), autor).param("size", "2").param("sort", "id,desc")).andExpect(status().isOk())
				.andExpect(jsonPath("$.page.totalElements").value(3)).andExpect(jsonPath("$.page.totalPages").value(2))
				.andReturn().getResponse();
		var content = mapper.readTree(first.getContentAsString()).get("content");
		assertEquals(expected.get(0), content.get(0).get("id").asText()); assertEquals(expected.get(1), content.get(1).get("id").asText());
		mvc.perform(req(HttpMethod.GET, path(material), autor).param("page", "1").param("size", "2")).andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1)).andExpect(jsonPath("$.content[0].id").value(expected.get(2)));
		mvc.perform(req(HttpMethod.GET, path(material), autor).param("page", "2").param("size", "2")).andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
	}
	@Test void semJwtNaoPodeCadastrarOuListar() throws Exception {
		var material = material(usuario(PerfilUsuario.USUARIO), base(), StatusMaterial.PUBLICADO); long before = comentarios.count();
		mvc.perform(req(HttpMethod.POST, path(material), null).content(body("Comentario"))).andExpect(status().isUnauthorized());
		mvc.perform(req(HttpMethod.GET, path(material), null)).andExpect(status().isUnauthorized()); assertEquals(before, comentarios.count());
	}
	@Test void nenhumPerfilNemDonoComentaOuListaMaterialNaoPublicado() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var base = base();
		var leitores = List.of(dono, usuario(PerfilUsuario.USUARIO), usuario(PerfilUsuario.MODERADOR), usuario(PerfilUsuario.ADMINISTRADOR));
		for (var state : List.of(StatusMaterial.PENDENTE, StatusMaterial.BLOQUEADO, StatusMaterial.REMOVIDO)) {
			var material = material(dono, base, state); comentario(material, dono, "PRIVADO_CANARY", StatusComentario.ATIVO);
			long before = comentarios.count();
			for (var leitor : leitores) {
				var response = mvc.perform(req(HttpMethod.GET, path(material), leitor)).andExpect(status().isNotFound()).andReturn().getResponse();
				assertFalse(response.getContentAsString().contains("PRIVADO_CANARY"));
				mvc.perform(req(HttpMethod.POST, path(material), leitor).content(body("Nao deve ser salvo"))).andExpect(status().isNotFound());
			}
			assertEquals(before, comentarios.count()); assertEquals(state, material.getStatus());
		}
	}
	@Test void mudarEstadoDoMaterialOcultaComentariosSemApagarOuReclassificarRegistros() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var material = material(autor, base(), StatusMaterial.PUBLICADO);
		var comentario = comentario(material, autor, "Texto preservado", StatusComentario.ATIVO);
		mvc.perform(req(HttpMethod.GET, path(material), autor)).andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1));
		for (var state : List.of(StatusMaterial.PENDENTE, StatusMaterial.BLOQUEADO, StatusMaterial.REMOVIDO)) {
			material.alterarStatus(state); materiais.flush();
			mvc.perform(req(HttpMethod.GET, path(material), autor)).andExpect(status().isNotFound());
			assertEquals(StatusComentario.ATIVO, comentarios.findById(comentario.getId()).orElseThrow().getStatus());
			assertEquals("Texto preservado", comentarios.findById(comentario.getId()).orElseThrow().getConteudo());
		}
	}
	@Test void validaConteudoObrigatorioTamanhoENulSemEcoarTextoRejeitado() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var material = material(autor, base(), StatusMaterial.PUBLICADO); long before = comentarios.count();
		for (String input : new String[]{null, "", " \n\t ", "X".repeat(4001), "PRIVADO_CANARY\u0000"}) {
			var response = mvc.perform(req(HttpMethod.POST, path(material), autor).content(body(input)))
					.andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
					.andReturn().getResponse();
			assertFalse(response.getContentAsString().contains("PRIVADO_CANARY"));
		}
		mvc.perform(req(HttpMethod.POST, path(material), autor).content("{}")).andExpect(status().isBadRequest());
		assertEquals(before, comentarios.count());
	}
	@Test void limite4000ETextoUnicodeComQuebrasDeLinhaSaoAceitos() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var material = material(autor, base(), StatusMaterial.PUBLICADO);
		for (String texto : List.of("X".repeat(4000), "Ótimo material!\nExplicação útil.\t🙂")) {
			mvc.perform(req(HttpMethod.POST, path(material), autor).content(body(texto))).andExpect(status().isCreated())
					.andExpect(jsonPath("$.conteudo").value(texto));
		}
	}
	@Test void textoRecebidoETextoJsonNaoHtmlRenderizadoEPermiteMaisDeUmComentarioPorUsuario() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var material = material(autor, base(), StatusMaterial.PUBLICADO);
		String texto = "<script>CANARY_TEXTO</script>";
		for (int i = 0; i < 2; i++) {
			mvc.perform(req(HttpMethod.POST, path(material), autor).content(body(texto))).andExpect(status().isCreated())
					.andExpect(jsonPath("$.conteudo").value(texto)).andExpect(header().string("X-Content-Type-Options", "nosniff"));
		}
		mvc.perform(req(HttpMethod.GET, path(material), autor)).andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(2));
	}
	@Test void usuarioDoTokenPrecisaExistirParaCadastrar() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO);
		var material = material(dono, base(), StatusMaterial.PUBLICADO); long before = comentarios.count(); em.remove(autor); em.flush();
		mvc.perform(req(HttpMethod.POST, path(material), autor).content(body("Comentario"))).andExpect(status().isUnauthorized());
		assertEquals(before, comentarios.count());
	}
	@Test void respostaSoContemDtoSemDadosPessoaisOuRelacionamentosJPA() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var material = material(autor, base(), StatusMaterial.PUBLICADO);
		comentario(material, autor, "Texto", StatusComentario.ATIVO);
		var response = mvc.perform(req(HttpMethod.GET, path(material), autor)).andExpect(status().isOk()).andReturn().getResponse();
		var node = mapper.readTree(response.getContentAsString()).get("content").get(0);
		assertEquals(6, node.size());
		for (String campo : List.of("usuario", "material", "email", "senha", "nome", "perfil")) { assertFalse(node.has(campo)); }
		assertFalse(response.getContentAsString().contains(autor.getEmail()));
		assertFalse(response.getContentAsString().contains(autor.getSenha()));
	}
	@Test void uuidInvalidoMaterialInexistenteEJsonMalformado() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO);
		for (var method : List.of(HttpMethod.GET, HttpMethod.POST)) {
			mvc.perform(req(method, "/invalido/comentarios", autor).content(body("Texto"))).andExpect(status().isBadRequest());
			mvc.perform(req(method, "/" + UUID.randomUUID() + "/comentarios", autor).content(body("Texto"))).andExpect(status().isNotFound());
		}
		var material = material(autor, base(), StatusMaterial.PUBLICADO);
		mvc.perform(req(HttpMethod.POST, path(material), autor).content("{invalido")).andExpect(status().isBadRequest());
	}
	@Test void validacaoNativaDaPaginacao() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var material = material(autor, base(), StatusMaterial.PUBLICADO);
		for (String query : List.of("page=-1", "size=0", "size=101", "size=abc", "page=2147483648")) {
			var parts = query.split("=");
			mvc.perform(req(HttpMethod.GET, path(material), autor).param(parts[0], parts[1])).andExpect(status().isBadRequest());
		}
		mvc.perform(req(HttpMethod.GET, path(material), autor).param("size", "100")).andExpect(status().isOk());
	}
	@Test void cadastroAceitaVendorJsonMasRejeitaConteudoAcademicoDireto() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var material = material(autor, base(), StatusMaterial.PUBLICADO);
		mvc.perform(req(HttpMethod.POST, path(material), autor).contentType("application/vnd.unescshare+json").content(body("Texto")))
				.andExpect(status().isCreated());
		mvc.perform(req(HttpMethod.POST, path(material), autor).contentType("application/pdf").content("%PDF-1.7"))
				.andExpect(status().isUnsupportedMediaType());
	}
	@Test void colecaoNaoAceitaPutDeleteENaoHaGetIndividual() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var material = material(autor, base(), StatusMaterial.PUBLICADO);
		var comentario = comentario(material, autor, "Texto preservado", StatusComentario.ATIVO);
		for (var method : List.of(HttpMethod.PUT, HttpMethod.DELETE)) {
			mvc.perform(req(method, path(material), autor).content(body("Texto editado"))).andExpect(status().isMethodNotAllowed());
		}
		mvc.perform(req(HttpMethod.GET, path(material) + "/" + comentario.getId(), autor)).andExpect(status().isMethodNotAllowed());
		assertEquals("Texto preservado", comentarios.findById(comentario.getId()).orElseThrow().getConteudo());
	}
	@Test void representacoesTextuaisNaoExpoemConteudoDeComentario() {
		assertFalse(new ComentarioRequest("CANARY_PRIVADO").toString().contains("CANARY_PRIVADO"));
		assertFalse(new ComentarioResponse(UUID.randomUUID(), "CANARY_PRIVADO", Instant.now(), StatusComentario.ATIVO,
				UUID.randomUUID(), UUID.randomUUID()).toString().contains("CANARY_PRIVADO"));
	}
	@Test void servicoTambemExigeAutenticacaoForaDoController() {
		org.springframework.security.core.context.SecurityContextHolder.clearContext();
		assertThrows(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class,
				() -> service.listar(UUID.randomUUID(), org.springframework.data.domain.PageRequest.of(0, 20)));
		assertThrows(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class,
				() -> service.cadastrar(UUID.randomUUID(), new ComentarioRequest("Texto")));
		assertThrows(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class,
				() -> service.atualizar(UUID.randomUUID(), UUID.randomUUID(), new ComentarioRequest("Texto")));
		assertThrows(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class,
				() -> service.remover(UUID.randomUUID(), UUID.randomUUID()));
	}
	@Test void consultaDoRepositoryAplicaStatusDoMaterialNoProprioBanco() {
		var autor = usuario(PerfilUsuario.USUARIO); var material = material(autor, base(), StatusMaterial.PUBLICADO);
		comentario(material, autor, "Texto", StatusComentario.ATIVO);
		var pageable = org.springframework.data.domain.PageRequest.of(0, 20);
		assertEquals(1, comentarios.findAllByMaterialIdAndMaterialStatusAndStatus(material.getId(), StatusMaterial.PUBLICADO,
				StatusComentario.ATIVO, pageable).getTotalElements());
		material.alterarStatus(StatusMaterial.BLOQUEADO); materiais.flush();
		assertTrue(comentarios.findAllByMaterialIdAndMaterialStatusAndStatus(material.getId(), StatusMaterial.PUBLICADO,
				StatusComentario.ATIVO, pageable).isEmpty());
	}
}
