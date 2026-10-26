package br.com.murkyweb.unesc_share.material;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import br.com.murkyweb.unesc_share.interacao.comentario.domain.Comentario;
import br.com.murkyweb.unesc_share.interacao.comentario.domain.ModeracaoComentario;
import br.com.murkyweb.unesc_share.interacao.comentario.domain.StatusComentario;
import br.com.murkyweb.unesc_share.interacao.comentario.dto.ComentarioModeracaoRequest;
import br.com.murkyweb.unesc_share.interacao.comentario.repository.ComentarioRepository;
import br.com.murkyweb.unesc_share.interacao.comentario.repository.ModeracaoComentarioRepository;
import br.com.murkyweb.unesc_share.interacao.comentario.service.ComentarioModeracaoService;
import br.com.murkyweb.unesc_share.material.domain.Material;
import br.com.murkyweb.unesc_share.material.domain.StatusMaterial;
import br.com.murkyweb.unesc_share.usuario.domain.PerfilUsuario;
import br.com.murkyweb.unesc_share.usuario.domain.Usuario;

class ComentarioModeracaoApiTests extends MaterialApiSupport {
	@Autowired ComentarioRepository comentarios;
	@Autowired ModeracaoComentarioRepository historico;
	@Autowired ComentarioModeracaoService service;

	private Comentario comentario(Material material, Usuario autor, StatusComentario status) {
		var comentario = new Comentario("Texto original privado", autor, material); comentario.alterarStatus(status);
		return comentarios.saveAndFlush(comentario);
	}
	private String colecao(Material material) { return "/" + material.getId() + "/comentarios"; }
	private String path(Comentario comentario) { return colecao(comentario.getMaterial()) + "/" + comentario.getId(); }
	private String motivo(String motivo) { return json(new ComentarioModeracaoRequest(motivo)); }
	private List<ModeracaoComentario> registros(Comentario comentario) {
		return historico.findAllByComentarioIdAndComentarioMaterialId(comentario.getId(), comentario.getMaterial().getId(),
				PageRequest.of(0, 100, org.springframework.data.domain.Sort.by("dataCadastro", "id"))).getContent();
	}
	private void acao(Comentario comentario, Usuario equipe, String acao) throws Exception {
		mvc.perform(req(HttpMethod.POST, path(comentario) + "/" + acao, equipe).content(motivo("  Decisao justificada  ")))
				.andExpect(status().isOk());
	}

	@Test void equipeOcultaEReexibeTerceiroComAuditoriaPreservandoTextoEReferencias() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var material = material(autor, base(), StatusMaterial.PUBLICADO);
		for (var perfil : List.of(PerfilUsuario.MODERADOR, PerfilUsuario.ADMINISTRADOR)) {
			var equipe = usuario(perfil); var comentario = comentario(material, autor, StatusComentario.ATIVO);
			var data = comentario.getDataCadastro();
			acao(comentario, equipe, "ocultacao"); assertEquals(StatusComentario.OCULTO, comentario.getStatus());
			acao(comentario, equipe, "reexibicao"); em.flush(); em.refresh(comentario);
			assertEquals(StatusComentario.ATIVO, comentario.getStatus()); assertEquals(data, comentario.getDataCadastro());
			assertEquals("Texto original privado", comentario.getConteudo()); assertEquals(autor.getId(), comentario.getUsuario().getId());
			var rows = registros(comentario); assertEquals(2, rows.size());
			assertEquals(StatusComentario.ATIVO, rows.get(0).getEstadoAnterior()); assertEquals(StatusComentario.OCULTO, rows.get(0).getEstadoNovo());
			assertEquals(StatusComentario.OCULTO, rows.get(1).getEstadoAnterior()); assertEquals(StatusComentario.ATIVO, rows.get(1).getEstadoNovo());
			for (var row : rows) {
				assertEquals(equipe.getId(), row.getResponsavel().getId()); assertEquals("Decisao justificada", row.getMotivo());
				assertEquals(comentario.getId(), row.getComentario().getId()); assertNotNull(row.getId()); assertNotNull(row.getDataCadastro());
			}
		}
		assertEquals(StatusMaterial.PUBLICADO, material.getStatus()); assertEquals(0, material.getVisualizacoes());
		assertEquals(1, magnets.countByMaterialId(material.getId()));
	}
	@Test void equipePodeOcultarEmQualquerEstadoDoMaterial() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var equipe = usuario(PerfilUsuario.MODERADOR); var base = base();
		for (var state : StatusMaterial.values()) {
			var material = material(autor, base, state); var comentario = comentario(material, autor, StatusComentario.ATIVO);
			acao(comentario, equipe, "ocultacao"); assertEquals(StatusComentario.OCULTO, comentario.getStatus());
			assertEquals(state, material.getStatus()); assertEquals(1, registros(comentario).size());
		}
	}
	@Test void reexibicaoEmMaterialNaoPublicadoRetorna409SemAuditoria() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var equipe = usuario(PerfilUsuario.ADMINISTRADOR); var base = base();
		for (var state : List.of(StatusMaterial.PENDENTE, StatusMaterial.BLOQUEADO, StatusMaterial.REMOVIDO)) {
			var comentario = comentario(material(autor, base, state), autor, StatusComentario.OCULTO);
			mvc.perform(req(HttpMethod.POST, path(comentario) + "/reexibicao", equipe).content(motivo("Reexibir"))).andExpect(status().isConflict());
			assertEquals(StatusComentario.OCULTO, comentario.getStatus()); assertTrue(registros(comentario).isEmpty());
		}
	}
	@Test void transicoesInvalidasERepetidasNaoRestauramRemovidosNemGeramHistorico() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var equipe = usuario(PerfilUsuario.MODERADOR);
		var material = material(autor, base(), StatusMaterial.PUBLICADO);
		for (var state : StatusComentario.values()) {
			var comentario = comentario(material, autor, state);
			for (String acao : List.of("ocultacao", "reexibicao")) {
				boolean invalido = state == StatusComentario.REMOVIDO || (state == StatusComentario.ATIVO && acao.equals("reexibicao"))
						|| (state == StatusComentario.OCULTO && acao.equals("ocultacao"));
				if (invalido) {
					mvc.perform(req(HttpMethod.POST, path(comentario) + "/" + acao, equipe).content(motivo("Motivo")))
							.andExpect(status().isConflict());
					assertEquals(state, comentario.getStatus()); assertTrue(registros(comentario).isEmpty());
				}
			}
		}
	}
	@Test void repetirAcaoBemSucedidaRetorna409SemDuplicarAuditoria() throws Exception {
		var equipe = usuario(PerfilUsuario.MODERADOR); var material = material(equipe, base(), StatusMaterial.PUBLICADO);
		var comentario = comentario(material, equipe, StatusComentario.ATIVO);
		for (String acao : List.of("ocultacao", "reexibicao")) {
			acao(comentario, equipe, acao); int count = registros(comentario).size();
			mvc.perform(req(HttpMethod.POST, path(comentario) + "/" + acao, equipe).content(motivo("Outro motivo"))).andExpect(status().isConflict());
			assertEquals(count, registros(comentario).size());
		}
	}
	@Test void listagemRestritaIncluiAtivosOcultosNaoPublicadosMasNuncaRemovidosOuOutroMaterial() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var equipe = usuario(PerfilUsuario.MODERADOR); var base = base();
		var outro = material(autor, base, StatusMaterial.PUBLICADO); comentario(outro, autor, StatusComentario.ATIVO);
		for (var state : StatusMaterial.values()) {
			var material = material(autor, base, state);
			comentario(material, autor, StatusComentario.ATIVO); comentario(material, autor, StatusComentario.OCULTO);
			var removido = comentario(material, autor, StatusComentario.REMOVIDO); removido.editar("REMOVIDO_CANARY"); comentarios.flush();
			var response = mvc.perform(req(HttpMethod.GET, colecao(material) + "/moderacao", equipe)
					.param("status", "REMOVIDO").param("usuarioId", UUID.randomUUID().toString()).param("sort", "id,desc"))
					.andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(2))
					.andExpect(jsonPath("$.page.totalElements").value(2)).andExpect(jsonPath("$.page.size").value(20))
					.andReturn().getResponse();
			assertFalse(response.getContentAsString().contains("REMOVIDO_CANARY")); assertFalse(response.getContentAsString().contains(outro.getId().toString()));
		}
	}
	@Test void listagemComumContinuaSemPrivilegiosDaEquipeENaoMostraOcultos() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var equipe = usuario(PerfilUsuario.ADMINISTRADOR);
		var material = material(autor, base(), StatusMaterial.PUBLICADO); var comentario = comentario(material, autor, StatusComentario.ATIVO);
		acao(comentario, equipe, "ocultacao");
		mvc.perform(req(HttpMethod.GET, colecao(material), equipe)).andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
		acao(comentario, equipe, "reexibicao");
		mvc.perform(req(HttpMethod.GET, colecao(material), autor)).andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(1));
		material.alterarStatus(StatusMaterial.BLOQUEADO); materiais.flush();
		mvc.perform(req(HttpMethod.GET, colecao(material), equipe)).andExpect(status().isNotFound());
	}
	@Test void autorEDonoSemPerfilDeEquipeRecebem403EmTodasAsRotas() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO);
		var material = material(dono, base(), StatusMaterial.PUBLICADO); var comentario = comentario(material, autor, StatusComentario.ATIVO);
		for (var leitor : List.of(dono, autor, usuario(PerfilUsuario.USUARIO))) {
			mvc.perform(req(HttpMethod.GET, colecao(material) + "/moderacao", leitor)).andExpect(status().isForbidden());
			mvc.perform(req(HttpMethod.GET, path(comentario) + "/historico-moderacao", leitor)).andExpect(status().isForbidden());
			for (String acao : List.of("ocultacao", "reexibicao")) {
				mvc.perform(req(HttpMethod.POST, path(comentario) + "/" + acao, leitor).content(motivo("Motivo"))).andExpect(status().isForbidden());
			}
		}
		assertEquals(StatusComentario.ATIVO, comentario.getStatus()); assertTrue(registros(comentario).isEmpty());
	}
	@Test void todasAsRotasExigemJwt() throws Exception {
		String root = "/" + UUID.randomUUID() + "/comentarios"; String item = root + "/" + UUID.randomUUID();
		for (String path : List.of(root + "/moderacao", item + "/historico-moderacao")) {
			mvc.perform(req(HttpMethod.GET, path, null)).andExpect(status().isUnauthorized());
		}
		for (String acao : List.of("ocultacao", "reexibicao")) {
			mvc.perform(req(HttpMethod.POST, item + "/" + acao, null).content(motivo("Motivo"))).andExpect(status().isUnauthorized());
		}
	}
	@Test void motivoObrigatorioAte1000SemNuloComErrosSemEcoarValor() throws Exception {
		var equipe = usuario(PerfilUsuario.MODERADOR); var material = material(equipe, base(), StatusMaterial.PUBLICADO);
		for (String acao : List.of("ocultacao", "reexibicao")) {
			var comentario = comentario(material, equipe, acao.equals("ocultacao") ? StatusComentario.ATIVO : StatusComentario.OCULTO);
			for (String texto : new String[]{null, "", " \n\t ", "X".repeat(1001), "MOTIVO_PRIVADO_CANARY\u0000"}) {
				var response = mvc.perform(req(HttpMethod.POST, path(comentario) + "/" + acao, equipe).content(motivo(texto)))
						.andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).andReturn().getResponse();
				assertFalse(response.getContentAsString().contains("MOTIVO_PRIVADO_CANARY"));
			}
			mvc.perform(req(HttpMethod.POST, path(comentario) + "/" + acao, equipe).content("{}")).andExpect(status().isBadRequest());
			assertTrue(registros(comentario).isEmpty());
		}
	}
	@Test void limiteVendorJsonECamposExtrasNaoForjamResponsavelEstadosTextoOuData() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var equipe = usuario(PerfilUsuario.ADMINISTRADOR);
		var material = material(autor, base(), StatusMaterial.PUBLICADO); var comentario = comentario(material, autor, StatusComentario.ATIVO);
		mvc.perform(req(HttpMethod.POST, path(comentario) + "/ocultacao", equipe).contentType("application/vnd.unescshare+json")
				.content(json(Map.of("motivo", "X".repeat(1000), "responsavelId", autor.getId(), "estadoAnterior", "REMOVIDO",
						"estadoNovo", "REMOVIDO", "conteudo", "Injetado", "dataCadastro", "2000-01-01T00:00:00Z"))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OCULTO"))
				.andExpect(jsonPath("$.conteudo").value("Texto original privado"));
		var row = registros(comentario).getFirst(); assertEquals(equipe.getId(), row.getResponsavel().getId());
		assertEquals("X".repeat(1000), row.getMotivo()); assertEquals(StatusComentario.ATIVO, row.getEstadoAnterior());
		assertEquals(StatusComentario.OCULTO, row.getEstadoNovo()); assertTrue(row.getDataCadastro().isAfter(java.time.Instant.parse("2020-01-01T00:00:00Z")));
	}
	@Test void historicoTemSomenteMetadadosEPermaneceConsultavelAposRemocao() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var equipe = usuario(PerfilUsuario.MODERADOR);
		var material = material(autor, base(), StatusMaterial.PUBLICADO); var comentario = comentario(material, autor, StatusComentario.ATIVO);
		acao(comentario, equipe, "ocultacao");
		mvc.perform(req(HttpMethod.DELETE, path(comentario), autor)).andExpect(status().isNoContent());
		material.alterarStatus(StatusMaterial.REMOVIDO); materiais.flush();
		for (var perfil : List.of(PerfilUsuario.MODERADOR, PerfilUsuario.ADMINISTRADOR)) {
			var response = mvc.perform(req(HttpMethod.GET, path(comentario) + "/historico-moderacao", usuario(perfil)))
					.andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(1))
					.andExpect(jsonPath("$.content[0].comentarioId").value(comentario.getId().toString()))
					.andExpect(jsonPath("$.content[0].responsavelId").value(equipe.getId().toString()))
					.andExpect(jsonPath("$.content[0].motivo").value("Decisao justificada"))
					.andExpect(jsonPath("$.content[0].conteudo").doesNotExist()).andReturn().getResponse();
			var node = mapper.readTree(response.getContentAsString()).get("content").get(0); assertEquals(7, node.size());
			assertFalse(response.getContentAsString().contains("Texto original privado"));
			assertFalse(response.getContentAsString().contains(equipe.getEmail())); assertFalse(response.getContentAsString().contains("senha"));
		}
		assertEquals(StatusComentario.REMOVIDO, comentario.getStatus()); assertEquals("Texto original privado", comentario.getConteudo());
		assertEquals(1, registros(comentario).size());
	}
	@Test void equipeNaoGanhaPermissaoParaEditarOuExcluirTextoDeTerceiro() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var equipe = usuario(PerfilUsuario.ADMINISTRADOR);
		var material = material(autor, base(), StatusMaterial.PUBLICADO); var comentario = comentario(material, autor, StatusComentario.ATIVO);
		acao(comentario, equipe, "ocultacao");
		mvc.perform(req(HttpMethod.PUT, path(comentario), equipe).content(json(Map.of("conteudo", "Injetado")))).andExpect(status().isNotFound());
		mvc.perform(req(HttpMethod.DELETE, path(comentario), equipe)).andExpect(status().isNotFound());
		assertEquals(StatusComentario.OCULTO, comentario.getStatus()); assertEquals("Texto original privado", comentario.getConteudo());
	}
	@Test void materialOuComentarioInexistenteOuDeOutroMaterialRetorna404SemHistorico() throws Exception {
		var equipe = usuario(PerfilUsuario.MODERADOR); var base = base(); var material = material(equipe, base, StatusMaterial.PUBLICADO);
		var outro = material(equipe, base, StatusMaterial.PUBLICADO); var comentario = comentario(material, equipe, StatusComentario.ATIVO);
		for (String item : List.of("/" + outro.getId() + "/comentarios/" + comentario.getId(),
				"/" + UUID.randomUUID() + "/comentarios/" + comentario.getId(), colecao(material) + "/" + UUID.randomUUID())) {
			mvc.perform(req(HttpMethod.GET, item + "/historico-moderacao", equipe)).andExpect(status().isNotFound());
			for (String acao : List.of("ocultacao", "reexibicao")) {
				mvc.perform(req(HttpMethod.POST, item + "/" + acao, equipe).content(motivo("Motivo"))).andExpect(status().isNotFound());
			}
		}
		mvc.perform(req(HttpMethod.GET, "/" + UUID.randomUUID() + "/comentarios/moderacao", equipe)).andExpect(status().isNotFound());
		assertTrue(registros(comentario).isEmpty());
	}
	@Test void consultasVaziasEPaginacaoValidadaEOrdenacaoFixa() throws Exception {
		var equipe = usuario(PerfilUsuario.MODERADOR); var material = material(equipe, base(), StatusMaterial.PUBLICADO);
		mvc.perform(req(HttpMethod.GET, colecao(material) + "/moderacao", equipe)).andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
		var primeiro = comentario(material, equipe, StatusComentario.ATIVO); var segundo = comentario(material, equipe, StatusComentario.ATIVO);
		acao(primeiro, equipe, "ocultacao"); acao(primeiro, equipe, "reexibicao");
		mvc.perform(req(HttpMethod.GET, colecao(material) + "/moderacao", equipe).param("page", "1").param("size", "1").param("sort", "id,desc"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(segundo.getId().toString()))
				.andExpect(jsonPath("$.page.totalElements").value(2)).andExpect(jsonPath("$.page.totalPages").value(2));
		mvc.perform(req(HttpMethod.GET, path(primeiro) + "/historico-moderacao", equipe).param("page", "1").param("size", "1"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content[0].estadoNovo").value("ATIVO"));
		mvc.perform(req(HttpMethod.GET, path(segundo) + "/historico-moderacao", equipe)).andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
		for (String path : List.of(colecao(material) + "/moderacao", path(primeiro) + "/historico-moderacao")) {
			for (var param : List.of(Map.of("page", "-1"), Map.of("size", "0"), Map.of("size", "101"), Map.of("page", "abc"))) {
				var request = req(HttpMethod.GET, path, equipe); param.forEach(request::param);
				mvc.perform(request).andExpect(status().isBadRequest());
			}
			mvc.perform(req(HttpMethod.GET, path, equipe).param("size", "100")).andExpect(status().isOk());
		}
	}
	@Test void metodosDeEscritaDoHistoricoNaoExistem() throws Exception {
		var equipe = usuario(PerfilUsuario.ADMINISTRADOR); var material = material(equipe, base(), StatusMaterial.PUBLICADO);
		var comentario = comentario(material, equipe, StatusComentario.ATIVO); acao(comentario, equipe, "ocultacao");
		for (var method : List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE)) {
			mvc.perform(req(method, path(comentario) + "/historico-moderacao", equipe).content(motivo("Injetado"))).andExpect(status().isMethodNotAllowed());
		}
		assertEquals(1, registros(comentario).size());
	}
	@Test void jsonTipoEUuidInvalidosRetornam400Ou415SemMutacoes() throws Exception {
		var equipe = usuario(PerfilUsuario.MODERADOR); var material = material(equipe, base(), StatusMaterial.PUBLICADO);
		var comentario = comentario(material, equipe, StatusComentario.ATIVO);
		for (String acao : List.of("ocultacao", "reexibicao")) {
			mvc.perform(req(HttpMethod.POST, path(comentario) + "/" + acao, equipe).content("{invalido")).andExpect(status().isBadRequest());
			mvc.perform(req(HttpMethod.POST, path(comentario) + "/" + acao, equipe).contentType("text/plain").content("Motivo"))
					.andExpect(status().isUnsupportedMediaType());
			mvc.perform(req(HttpMethod.POST, colecao(material) + "/invalido/" + acao, equipe).content(motivo("Motivo"))).andExpect(status().isBadRequest());
		}
		mvc.perform(req(HttpMethod.GET, "/invalido/comentarios/moderacao", equipe)).andExpect(status().isBadRequest());
		mvc.perform(req(HttpMethod.GET, colecao(material) + "/invalido/historico-moderacao", equipe)).andExpect(status().isBadRequest());
		assertEquals(StatusComentario.ATIVO, comentario.getStatus()); assertTrue(registros(comentario).isEmpty());
	}
	@Test void responsavelNaoPersistidoNaoModeraNemGeraHistorico() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var equipe = usuario(PerfilUsuario.MODERADOR);
		var material = material(autor, base(), StatusMaterial.PUBLICADO); var comentario = comentario(material, autor, StatusComentario.ATIVO);
		var request = req(HttpMethod.POST, path(comentario) + "/ocultacao", equipe).content(motivo("Motivo"));
		em.remove(equipe); em.flush();
		mvc.perform(request).andExpect(status().isUnauthorized());
		assertEquals(StatusComentario.ATIVO, comentario.getStatus()); assertTrue(registros(comentario).isEmpty());
	}
	@Test void serviceProtegidoMesmoSemPassarPeloController() {
		UUID material = UUID.randomUUID(), comentario = UUID.randomUUID(); var page = PageRequest.of(0, 20);
		assertThrows(AuthenticationCredentialsNotFoundException.class, () -> service.listar(material, page));
		assertThrows(AuthenticationCredentialsNotFoundException.class, () -> service.historico(material, comentario, page));
		assertThrows(AuthenticationCredentialsNotFoundException.class, () -> service.ocultar(material, comentario, new ComentarioModeracaoRequest("Motivo")));
		assertThrows(AuthenticationCredentialsNotFoundException.class, () -> service.reexibir(material, comentario, new ComentarioModeracaoRequest("Motivo")));
	}
	@Test void serviceTambemNegaPerfilUsuarioSemDependerDosMatchersHttp() {
		var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
		context.setAuthentication(new org.springframework.security.authentication.TestingAuthenticationToken("teste", null, "ROLE_USUARIO"));
		org.springframework.security.core.context.SecurityContextHolder.setContext(context);
		try {
			UUID material = UUID.randomUUID(), comentario = UUID.randomUUID(); var page = PageRequest.of(0, 20);
			assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.listar(material, page));
			assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.historico(material, comentario, page));
			assertThrows(org.springframework.security.access.AccessDeniedException.class,
					() -> service.ocultar(material, comentario, new ComentarioModeracaoRequest("Motivo")));
			assertThrows(org.springframework.security.access.AccessDeniedException.class,
					() -> service.reexibir(material, comentario, new ComentarioModeracaoRequest("Motivo")));
		} finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
	}
}
