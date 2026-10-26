package br.com.murkyweb.unesc_share.material;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
import br.com.murkyweb.unesc_share.interacao.comentario.repository.ComentarioRepository;
import br.com.murkyweb.unesc_share.material.domain.Material;
import br.com.murkyweb.unesc_share.material.domain.StatusMaterial;
import br.com.murkyweb.unesc_share.usuario.domain.PerfilUsuario;
import br.com.murkyweb.unesc_share.usuario.domain.Usuario;

class ComentarioEdicaoRemocaoApiTests extends MaterialApiSupport {
	@Autowired private ComentarioRepository comentarios;
	private Comentario comentario(Material material, Usuario autor, StatusComentario status) {
		var comentario = new Comentario("Texto original", autor, material); comentario.alterarStatus(status);
		return comentarios.saveAndFlush(comentario);
	}
	private String path(Comentario comentario) {
		return "/" + comentario.getMaterial().getId() + "/comentarios/" + comentario.getId();
	}
	private String body(String conteudo) { return json(new ComentarioRequest(conteudo)); }

	@Test void autorDeQualquerPerfilEditaAtivoPublicadoPreservandoIdentidadeDataEEstado() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var material = material(dono, base(), StatusMaterial.PUBLICADO);
		for (var perfil : PerfilUsuario.values()) {
			var autor = usuario(perfil); var comentario = comentario(material, autor, StatusComentario.ATIVO); var data = comentario.getDataCadastro();
			mvc.perform(req(HttpMethod.PUT, path(comentario), autor).content(body("  Texto editado  "))).andExpect(status().isOk())
					.andExpect(jsonPath("$.id").value(comentario.getId().toString())).andExpect(jsonPath("$.conteudo").value("Texto editado"))
					.andExpect(jsonPath("$.status").value("ATIVO")).andExpect(jsonPath("$.usuarioId").value(autor.getId().toString()))
					.andExpect(jsonPath("$.materialId").value(material.getId().toString()));
			assertEquals(data, comentario.getDataCadastro()); assertEquals(StatusComentario.ATIVO, comentario.getStatus());
			assertEquals(autor.getId(), comentario.getUsuario().getId());
		}
		assertEquals(StatusMaterial.PUBLICADO, material.getStatus()); assertEquals(0, material.getVisualizacoes());
		assertEquals(1, magnets.countByMaterialId(material.getId()));
	}
	@Test void autorNaoEditaOcultoOuRemovidoNemRestauraPorCamposExtras() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var material = material(autor, base(), StatusMaterial.PUBLICADO);
		for (var state : List.of(StatusComentario.OCULTO, StatusComentario.REMOVIDO)) {
			var comentario = comentario(material, autor, state);
			mvc.perform(req(HttpMethod.PUT, path(comentario), autor).content(json(Map.of("conteudo", "Tentativa", "status", "ATIVO"))))
					.andExpect(status().isConflict());
			assertEquals("Texto original", comentario.getConteudo()); assertEquals(state, comentario.getStatus());
		}
	}
	@Test void autorNaoEditaComentarioDeMaterialNaoPublicadoMesmoSendoDonoOuEquipe() throws Exception {
		var base = base();
		for (var perfil : PerfilUsuario.values()) {
			var autor = usuario(perfil);
			for (var state : List.of(StatusMaterial.PENDENTE, StatusMaterial.BLOQUEADO, StatusMaterial.REMOVIDO)) {
				var material = material(autor, base, state); var comentario = comentario(material, autor, StatusComentario.ATIVO);
				mvc.perform(req(HttpMethod.PUT, path(comentario), autor).content(body("Tentativa"))).andExpect(status().isNotFound());
				assertEquals("Texto original", comentario.getConteudo()); assertEquals(StatusComentario.ATIVO, comentario.getStatus());
			}
		}
	}
	@Test void remocaoLogicaFuncionaEmTodosOsEstadosDeMaterialEComentario() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO); var base = base();
		for (var materialState : StatusMaterial.values()) {
			var material = material(dono, base, materialState);
			for (var commentState : StatusComentario.values()) {
				var comentario = comentario(material, autor, commentState); var data = comentario.getDataCadastro();
				long count = comentarios.count();
				for (int i = 0; i < 2; i++) {
					mvc.perform(req(HttpMethod.DELETE, path(comentario), autor)).andExpect(status().isNoContent())
							.andExpect(content().string(""));
				}
				em.flush(); em.refresh(comentario);
				assertEquals(StatusComentario.REMOVIDO, comentario.getStatus()); assertEquals("Texto original", comentario.getConteudo());
				assertEquals(data, comentario.getDataCadastro()); assertEquals(count, comentarios.count());
				assertTrue(comentarios.existsById(comentario.getId())); assertEquals(materialState, material.getStatus());
			}
		}
	}
	@Test void moderadorEAdministradorPodemRemoverPropriosMasNaoComoModeracao() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var material = material(dono, base(), StatusMaterial.BLOQUEADO);
		for (var perfil : List.of(PerfilUsuario.MODERADOR, PerfilUsuario.ADMINISTRADOR)) {
			var autor = usuario(perfil); var comentario = comentario(material, autor, StatusComentario.OCULTO);
			mvc.perform(req(HttpMethod.DELETE, path(comentario), autor)).andExpect(status().isNoContent());
			assertEquals(StatusComentario.REMOVIDO, comentario.getStatus());
		}
	}
	@Test void terceirosIncluindoDonoDoMaterialEEquipeRecebem404SemAlterarOuVazarTexto() throws Exception {
		var dono = usuario(PerfilUsuario.ADMINISTRADOR); var autor = usuario(PerfilUsuario.USUARIO); var base = base();
		var terceiros = List.of(dono, usuario(PerfilUsuario.USUARIO), usuario(PerfilUsuario.MODERADOR), usuario(PerfilUsuario.ADMINISTRADOR));
		for (var materialState : StatusMaterial.values()) {
			var material = material(dono, base, materialState);
			for (var commentState : StatusComentario.values()) {
				var comentario = comentario(material, autor, commentState);
				for (var terceiro : terceiros) {
					for (var method : List.of(HttpMethod.PUT, HttpMethod.DELETE)) {
						var response = mvc.perform(req(method, path(comentario), terceiro).content(body("Tentativa")))
								.andExpect(status().isNotFound()).andReturn().getResponse();
						assertFalse(response.getContentAsString().contains("Texto original"));
					}
				}
				assertEquals("Texto original", comentario.getConteudo()); assertEquals(commentState, comentario.getStatus());
			}
		}
	}
	@Test void autorNaoPodeUsarMaterialDiferenteNaRotaMesmoComComentarioProprio() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var base = base();
		var material = material(autor, base, StatusMaterial.PUBLICADO); var outro = material(autor, base, StatusMaterial.PUBLICADO);
		var comentario = comentario(material, autor, StatusComentario.ATIVO);
		for (var method : List.of(HttpMethod.PUT, HttpMethod.DELETE)) {
			mvc.perform(req(method, "/" + outro.getId() + "/comentarios/" + comentario.getId(), autor).content(body("Tentativa")))
					.andExpect(status().isNotFound());
		}
		assertEquals("Texto original", comentario.getConteudo()); assertEquals(StatusComentario.ATIVO, comentario.getStatus());
	}
	@Test void semJwtNaoPodeEditarOuRemover() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var material = material(autor, base(), StatusMaterial.PUBLICADO);
		var comentario = comentario(material, autor, StatusComentario.ATIVO);
		for (var method : List.of(HttpMethod.PUT, HttpMethod.DELETE)) {
			mvc.perform(req(method, path(comentario), null).content(body("Tentativa"))).andExpect(status().isUnauthorized());
		}
		assertEquals(StatusComentario.ATIVO, comentario.getStatus()); assertEquals("Texto original", comentario.getConteudo());
	}
	@Test void edicaoMantemValidacaoNativaDoTextoSemEcoarDadosRejeitados() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var material = material(autor, base(), StatusMaterial.PUBLICADO);
		var comentario = comentario(material, autor, StatusComentario.ATIVO);
		for (String texto : new String[]{null, "", " \n\t ", "X".repeat(4001), "CANARY_REJEITADO\u0000"}) {
			var response = mvc.perform(req(HttpMethod.PUT, path(comentario), autor).content(body(texto)))
					.andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
					.andReturn().getResponse();
			assertFalse(response.getContentAsString().contains("CANARY_REJEITADO"));
		}
		mvc.perform(req(HttpMethod.PUT, path(comentario), autor).content("{}")).andExpect(status().isBadRequest());
		assertEquals("Texto original", comentario.getConteudo());
	}
	@Test void edicaoNaoAceitaTrocarAutorMaterialStatusDataOuIdDoJson() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var material = material(autor, base(), StatusMaterial.PUBLICADO);
		var comentario = comentario(material, autor, StatusComentario.ATIVO); var data = comentario.getDataCadastro();
		mvc.perform(req(HttpMethod.PUT, path(comentario), autor).content(json(Map.of("conteudo", "Texto novo", "id", UUID.randomUUID(),
				"usuarioId", UUID.randomUUID(), "materialId", UUID.randomUUID(), "status", "OCULTO", "dataCadastro", "2000-01-01T00:00:00Z"))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ATIVO"))
				.andExpect(jsonPath("$.usuarioId").value(autor.getId().toString())).andExpect(jsonPath("$.id").value(comentario.getId().toString()));
		assertEquals(material.getId(), comentario.getMaterial().getId()); assertEquals(data, comentario.getDataCadastro());
	}
	@Test void edicaoVendorJsonAceitaLimite4000ERejeitaTipoOuJsonInvalidos() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var material = material(autor, base(), StatusMaterial.PUBLICADO);
		var comentario = comentario(material, autor, StatusComentario.ATIVO);
		mvc.perform(req(HttpMethod.PUT, path(comentario), autor).contentType("application/vnd.unescshare+json").content(body("X".repeat(4000))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.conteudo").value("X".repeat(4000)));
		mvc.perform(req(HttpMethod.PUT, path(comentario), autor).content("{invalido")).andExpect(status().isBadRequest());
		mvc.perform(req(HttpMethod.PUT, path(comentario), autor).contentType("application/pdf").content("%PDF-1.7"))
				.andExpect(status().isUnsupportedMediaType());
	}
	@Test void inexistentesEUuidsInvalidosRetornamErrosNativosSemModificarComentario() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var material = material(autor, base(), StatusMaterial.PUBLICADO);
		var comentario = comentario(material, autor, StatusComentario.ATIVO);
		for (var method : List.of(HttpMethod.PUT, HttpMethod.DELETE)) {
			for (String invalidPath : List.of("/" + material.getId() + "/comentarios/" + UUID.randomUUID(),
					"/" + UUID.randomUUID() + "/comentarios/" + comentario.getId())) {
				mvc.perform(req(method, invalidPath, autor).content(body("Tentativa"))).andExpect(status().isNotFound());
			}
			for (String invalidPath : List.of("/invalido/comentarios/" + comentario.getId(), "/" + material.getId() + "/comentarios/invalido")) {
				mvc.perform(req(method, invalidPath, autor).content(body("Tentativa"))).andExpect(status().isBadRequest());
			}
		}
		assertEquals(StatusComentario.ATIVO, comentario.getStatus()); assertEquals("Texto original", comentario.getConteudo());
	}
	@Test void removidoSomeDaListagemENaoPodeSerReeditadoOuRestaurado() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var material = material(autor, base(), StatusMaterial.PUBLICADO);
		var comentario = comentario(material, autor, StatusComentario.ATIVO);
		mvc.perform(req(HttpMethod.DELETE, path(comentario), autor)).andExpect(status().isNoContent());
		mvc.perform(req(HttpMethod.PUT, path(comentario), autor).content(body("Restaurar"))).andExpect(status().isConflict());
		mvc.perform(req(HttpMethod.GET, "/" + material.getId() + "/comentarios", autor)).andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isEmpty());
		assertTrue(comentarios.existsById(comentario.getId())); assertEquals(StatusComentario.REMOVIDO, comentario.getStatus());
	}
	@Test void removerComentarioDeMaterialPrivadoNaoAbreConsultaPrivadaNemDevolveConteudo() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var material = material(autor, base(), StatusMaterial.BLOQUEADO);
		var comentario = comentario(material, autor, StatusComentario.OCULTO);
		mvc.perform(req(HttpMethod.DELETE, path(comentario), autor)).andExpect(status().isNoContent()).andExpect(content().string(""));
		mvc.perform(req(HttpMethod.GET, "/" + material.getId() + "/comentarios", autor)).andExpect(status().isNotFound());
		mvc.perform(req(HttpMethod.GET, path(comentario), autor)).andExpect(status().isMethodNotAllowed());
		assertEquals("Texto original", comentario.getConteudo());
	}
}
