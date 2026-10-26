package br.com.murkyweb.unesc_share.material;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import br.com.murkyweb.unesc_share.interacao.denuncia.domain.Denuncia;
import br.com.murkyweb.unesc_share.interacao.denuncia.domain.MotivoDenuncia;
import br.com.murkyweb.unesc_share.interacao.denuncia.domain.StatusDenuncia;
import br.com.murkyweb.unesc_share.interacao.denuncia.dto.DenunciaModeracaoRequest;
import br.com.murkyweb.unesc_share.interacao.denuncia.repository.DenunciaRepository;
import br.com.murkyweb.unesc_share.interacao.denuncia.repository.ModeracaoDenunciaRepository;
import br.com.murkyweb.unesc_share.interacao.denuncia.service.DenunciaModeracaoService;
import br.com.murkyweb.unesc_share.material.domain.Material;
import br.com.murkyweb.unesc_share.material.domain.StatusMaterial;
import br.com.murkyweb.unesc_share.usuario.domain.PerfilUsuario;
import br.com.murkyweb.unesc_share.usuario.domain.Usuario;

class DenunciaModeracaoApiTests extends MaterialApiSupport {
	@Autowired DenunciaRepository denuncias;
	@Autowired ModeracaoDenunciaRepository historico;
	@Autowired DenunciaModeracaoService service;
	private Denuncia denuncia(Material material, Usuario autor, StatusDenuncia estado) {
		var d = new Denuncia(MotivoDenuncia.SPAM, "Relato original", autor, material); d.alterarStatus(estado); return denuncias.saveAndFlush(d);
	}
	private MockHttpServletRequestBuilder dr(HttpMethod method, String tail, Usuario actor) {
		String path = "/denuncias/moderacao" + tail;
		var req = request(method, "/api/v1" + path).contextPath("/api/v1").servletPath(path).contentType(MediaType.APPLICATION_JSON);
		if (actor != null) { req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token(actor)); } return req;
	}
	private String body(String justificativa) { return json(new DenunciaModeracaoRequest(justificativa)); }
	private void acao(Denuncia d, Usuario equipe, String acao) throws Exception {
		mvc.perform(dr(HttpMethod.POST, "/" + d.getId() + "/" + acao, equipe).content(body("  Decisao fundamentada  "))).andExpect(status().isOk());
	}

	@Test void equipeAnalisaEConcluiComHistoricoSemAlterarRelatoOuMaterial() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO); var base = base();
		for (var perfil : List.of(PerfilUsuario.MODERADOR, PerfilUsuario.ADMINISTRADOR)) {
			var equipe = usuario(perfil);
			for (String destino : List.of("procedencia", "improcedencia")) {
				var material = material(dono, base, StatusMaterial.PUBLICADO); var d = denuncia(material, autor, StatusDenuncia.PENDENTE); var data = d.getDataCadastro();
				acao(d, equipe, "analise"); acao(d, equipe, destino); em.refresh(d);
				assertEquals(destino.equals("procedencia") ? StatusDenuncia.PROCEDENTE : StatusDenuncia.IMPROCEDENTE, d.getStatus());
				assertEquals(data, d.getDataCadastro()); assertEquals("Relato original", d.getDescricao()); assertEquals(MotivoDenuncia.SPAM, d.getMotivo());
				var rows = historico.findAllByDenunciaId(d.getId(), org.springframework.data.domain.PageRequest.of(0, 20,
						org.springframework.data.domain.Sort.by("dataCadastro", "id"))).getContent();
				assertEquals(2, rows.size()); assertEquals(StatusDenuncia.PENDENTE, rows.get(0).getEstadoAnterior());
				assertEquals(StatusDenuncia.EM_ANALISE, rows.get(0).getEstadoNovo()); assertEquals(StatusDenuncia.EM_ANALISE, rows.get(1).getEstadoAnterior());
				assertEquals(d.getStatus(), rows.get(1).getEstadoNovo());
				for (var row : rows) {
					assertEquals(equipe.getId(), row.getResponsavel().getId()); assertEquals("Decisao fundamentada", row.getJustificativa());
					assertNotNull(row.getId()); assertNotNull(row.getDataCadastro()); assertEquals(d.getId(), row.getDenuncia().getId());
				}
				assertEquals(StatusMaterial.PUBLICADO, material.getStatus()); assertEquals(0, material.getVisualizacoes()); assertEquals(1, magnets.countByMaterialId(material.getId()));
			}
		}
	}
	@Test void matrizDeTransicoesRejeitaSaltosRepeticoesReaberturasETrocaDeDecisao() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO); var equipe = usuario(PerfilUsuario.MODERADOR); var base = base();
		for (var estado : StatusDenuncia.values()) {
			for (String acao : List.of("analise", "procedencia", "improcedencia")) {
				var material = material(dono, base, StatusMaterial.PUBLICADO); var d = denuncia(material, autor, estado);
				boolean valida = (estado == StatusDenuncia.PENDENTE && acao.equals("analise")) || (estado == StatusDenuncia.EM_ANALISE && !acao.equals("analise"));
				mvc.perform(dr(HttpMethod.POST, "/" + d.getId() + "/" + acao, equipe).content(body("Justificativa")))
						.andExpect(status().is(valida ? 200 : 409));
				assertEquals(valida ? 1 : 0, historico.findAllByDenunciaId(d.getId(), Pageable.unpaged()).getTotalElements());
				if (!valida) { assertEquals(estado, d.getStatus()); }
			}
		}
	}
	@Test void repetirAcaoBemSucedidaNaoDuplicaHistorico() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO); var equipe = usuario(PerfilUsuario.MODERADOR);
		var d = denuncia(material(dono, base(), StatusMaterial.PUBLICADO), autor, StatusDenuncia.PENDENTE);
		for (String acao : List.of("analise", "procedencia")) {
			acao(d, equipe, acao); long count = historico.findAllByDenunciaId(d.getId(), Pageable.unpaged()).getTotalElements();
			mvc.perform(dr(HttpMethod.POST, "/" + d.getId() + "/" + acao, equipe).content(body("Outro motivo"))).andExpect(status().isConflict());
			assertEquals(count, historico.findAllByDenunciaId(d.getId(), Pageable.unpaged()).getTotalElements());
		}
	}
	@Test void moderacaoFuncionaEmTodosOsEstadosDeMaterialSemBloquearOuPublicar() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO); var equipe = usuario(PerfilUsuario.ADMINISTRADOR); var base = base();
		for (var estado : StatusMaterial.values()) {
			for (String conclusao : List.of("procedencia", "improcedencia")) {
				var material = material(dono, base, estado); var d = denuncia(material, autor, StatusDenuncia.PENDENTE);
				acao(d, equipe, "analise"); acao(d, equipe, conclusao); assertEquals(estado, material.getStatus());
				assertEquals(1, magnets.countByMaterialId(material.getId()));
			}
		}
	}
	@Test void denuncianteOuDonoDoMaterialNaoModeraEmNenhumPerfilOuAcaoMasPodeLerComoEquipe() throws Exception {
		var base = base();
		for (var perfil : List.of(PerfilUsuario.MODERADOR, PerfilUsuario.ADMINISTRADOR)) {
			for (boolean denunciante : List.of(true, false)) {
				var equipe = usuario(perfil); var dono = denunciante ? usuario(PerfilUsuario.USUARIO) : equipe;
				var autor = denunciante ? equipe : usuario(PerfilUsuario.USUARIO);
				for (String acao : List.of("analise", "procedencia", "improcedencia")) {
					var d = denuncia(material(dono, base, StatusMaterial.PUBLICADO), autor, acao.equals("analise") ? StatusDenuncia.PENDENTE : StatusDenuncia.EM_ANALISE);
					mvc.perform(dr(HttpMethod.POST, "/" + d.getId() + "/" + acao, equipe).content(body("Justificativa"))).andExpect(status().isForbidden());
					assertTrue(historico.findAllByDenunciaId(d.getId(), Pageable.unpaged()).isEmpty());
					mvc.perform(dr(HttpMethod.GET, "/" + d.getId(), equipe)).andExpect(status().isOk());
				}
			}
		}
	}
	@Test void outroMembroElegivelPodeConcluirAnaliseSemAtribuicaoExclusiva() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO); var a = usuario(PerfilUsuario.MODERADOR); var b = usuario(PerfilUsuario.ADMINISTRADOR);
		var d = denuncia(material(dono, base(), StatusMaterial.PUBLICADO), autor, StatusDenuncia.PENDENTE);
		acao(d, a, "analise"); acao(d, b, "improcedencia");
		var rows = historico.findAllByDenunciaId(d.getId(), org.springframework.data.domain.PageRequest.of(0, 20, org.springframework.data.domain.Sort.by("dataCadastro", "id"))).getContent();
		assertEquals(a.getId(), rows.get(0).getResponsavel().getId()); assertEquals(b.getId(), rows.get(1).getResponsavel().getId());
	}
	@Test void filaPadraoPendenteEFiltragemPorEstadoSemExporIdentidade() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO); var equipe = usuario(PerfilUsuario.MODERADOR); var base = base();
		for (var estado : StatusDenuncia.values()) { denuncia(material(dono, base, StatusMaterial.BLOQUEADO), autor, estado); }
		var response = mvc.perform(dr(HttpMethod.GET, "", equipe)).andExpect(status().isOk()).andExpect(jsonPath("$.page.size").value(20)).andReturn().getResponse();
		for (var item : mapper.readTree(response.getContentAsString()).get("content")) { assertEquals("PENDENTE", item.get("status").asText()); assertEquals(6, item.size()); }
		for (var estado : StatusDenuncia.values()) {
			var r = mvc.perform(dr(HttpMethod.GET, "", equipe).param("status", estado.name()).param("usuarioId", UUID.randomUUID().toString()))
					.andExpect(status().isOk()).andReturn().getResponse();
			for (var item : mapper.readTree(r.getContentAsString()).get("content")) { assertEquals(estado.name(), item.get("status").asText()); }
			assertFalse(r.getContentAsString().contains(autor.getId().toString())); assertFalse(r.getContentAsString().contains(autor.getEmail()));
		}
	}
	@Test void detalhesEHistoricoNaoExpoemDenuncianteOuDadosPrivadosDeMaterial() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO); var equipe = usuario(PerfilUsuario.MODERADOR);
		var material = material(dono, base(), StatusMaterial.REMOVIDO); var d = denuncia(material, autor, StatusDenuncia.PENDENTE); acao(d, equipe, "analise");
		var r = mvc.perform(dr(HttpMethod.GET, "/" + d.getId(), equipe)).andExpect(status().isOk()).andReturn().getResponse();
		assertEquals(6, mapper.readTree(r.getContentAsString()).size()); assertFalse(r.getContentAsString().contains(autor.getId().toString()));
		assertFalse(r.getContentAsString().contains(autor.getEmail())); assertFalse(r.getContentAsString().contains(material.getTitulo()));
		var h = mvc.perform(dr(HttpMethod.GET, "/" + d.getId() + "/historico", equipe)).andExpect(status().isOk())
				.andExpect(jsonPath("$.page.totalElements").value(1)).andExpect(jsonPath("$.content[0].responsavelId").value(equipe.getId().toString()))
				.andExpect(jsonPath("$.content[0].justificativa").value("Decisao fundamentada")).andReturn().getResponse();
		assertEquals(7, mapper.readTree(h.getContentAsString()).get("content").get(0).size());
		assertFalse(h.getContentAsString().contains("Relato original")); assertFalse(h.getContentAsString().contains(autor.getId().toString()));
	}
	@Test void rotasPropriasNaoGanhamExcecaoDeEquipeEDenuncianteVeStatusSemHistoricoInterno() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO); var equipe = usuario(PerfilUsuario.MODERADOR);
		var d = denuncia(material(dono, base(), StatusMaterial.PUBLICADO), autor, StatusDenuncia.PENDENTE); acao(d, equipe, "analise");
		String path = "/denuncias/" + d.getId();
		mvc.perform(request(HttpMethod.GET, "/api/v1" + path).contextPath("/api/v1").servletPath(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token(equipe)))
				.andExpect(status().isNotFound());
		mvc.perform(request(HttpMethod.GET, "/api/v1" + path).contextPath("/api/v1").servletPath(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token(autor)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EM_ANALISE")).andExpect(jsonPath("$.justificativa").doesNotExist());
		mvc.perform(dr(HttpMethod.GET, "/" + d.getId() + "/historico", autor)).andExpect(status().isForbidden());
	}
	@Test void usuariosComunsIncluindoDonoEDenuncianteRecebem403EmTodasAsRotas() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO); var d = denuncia(material(dono, base(), StatusMaterial.PUBLICADO), autor, StatusDenuncia.PENDENTE);
		for (var leitor : List.of(dono, autor, usuario(PerfilUsuario.USUARIO))) {
			for (String tail : List.of("", "/" + d.getId(), "/" + d.getId() + "/historico")) { mvc.perform(dr(HttpMethod.GET, tail, leitor)).andExpect(status().isForbidden()); }
			for (String acao : List.of("analise", "procedencia", "improcedencia")) { mvc.perform(dr(HttpMethod.POST, "/" + d.getId() + "/" + acao, leitor).content(body("Motivo"))).andExpect(status().isForbidden()); }
		}
		assertEquals(StatusDenuncia.PENDENTE, d.getStatus()); assertTrue(historico.findAllByDenunciaId(d.getId(), Pageable.unpaged()).isEmpty());
	}
	@Test void seisRotasExigemJwt() throws Exception {
		UUID id = UUID.randomUUID();
		for (String tail : List.of("", "/" + id, "/" + id + "/historico")) { mvc.perform(dr(HttpMethod.GET, tail, null)).andExpect(status().isUnauthorized()); }
		for (String acao : List.of("analise", "procedencia", "improcedencia")) { mvc.perform(dr(HttpMethod.POST, "/" + id + "/" + acao, null).content(body("Motivo"))).andExpect(status().isUnauthorized()); }
	}
	@Test void justificativaObrigatoriaAte1000SemNuloSemEcoarValorRejeitado() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO); var equipe = usuario(PerfilUsuario.MODERADOR); var base = base();
		for (String acao : List.of("analise", "procedencia", "improcedencia")) {
			var d = denuncia(material(dono, base, StatusMaterial.PUBLICADO), autor, acao.equals("analise") ? StatusDenuncia.PENDENTE : StatusDenuncia.EM_ANALISE);
			for (String texto : new String[]{null, "", " \n\t ", "X".repeat(1001), "JUSTIFICATIVA_CANARY\u0000"}) {
				var r = mvc.perform(dr(HttpMethod.POST, "/" + d.getId() + "/" + acao, equipe).content(body(texto))).andExpect(status().isBadRequest())
						.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).andReturn().getResponse();
				assertFalse(r.getContentAsString().contains("JUSTIFICATIVA_CANARY"));
			}
			assertTrue(historico.findAllByDenunciaId(d.getId(), Pageable.unpaged()).isEmpty());
		}
	}
	@Test void vendorJsonLimiteECamposExtrasNaoEscolhemEstadoResponsavelOuRelato() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO); var equipe = usuario(PerfilUsuario.ADMINISTRADOR);
		var d = denuncia(material(dono, base(), StatusMaterial.PUBLICADO), autor, StatusDenuncia.PENDENTE);
		mvc.perform(dr(HttpMethod.POST, "/" + d.getId() + "/analise", equipe).contentType("application/vnd.unescshare+json").content(json(Map.of(
				"justificativa", "X".repeat(1000), "responsavelId", autor.getId(), "estadoNovo", "PROCEDENTE", "estadoAnterior", "IMPROCEDENTE",
				"descricao", "Substituido", "usuarioId", equipe.getId(), "materialId", UUID.randomUUID())))).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("EM_ANALISE")).andExpect(jsonPath("$.descricao").value("Relato original"));
		var row = historico.findAllByDenunciaId(d.getId(), Pageable.unpaged()).getContent().getFirst(); assertEquals(equipe.getId(), row.getResponsavel().getId());
		assertEquals(StatusDenuncia.PENDENTE, row.getEstadoAnterior()); assertEquals(StatusDenuncia.EM_ANALISE, row.getEstadoNovo()); assertEquals("X".repeat(1000), row.getJustificativa());
	}
	@Test void paginacaoEnumTipoEUuidInvalidosMantemTratamentoNativo() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO); var equipe = usuario(PerfilUsuario.MODERADOR);
		var d = denuncia(material(dono, base(), StatusMaterial.PUBLICADO), autor, StatusDenuncia.PENDENTE);
		for (String tail : List.of("", "/" + d.getId() + "/historico")) {
			for (var param : List.of(Map.of("page", "-1"), Map.of("page", "abc"), Map.of("size", "0"), Map.of("size", "101"))) {
				var req = dr(HttpMethod.GET, tail, equipe); param.forEach(req::param); mvc.perform(req).andExpect(status().isBadRequest());
			}
			mvc.perform(dr(HttpMethod.GET, tail, equipe).param("size", "100")).andExpect(status().isOk());
		}
		mvc.perform(dr(HttpMethod.GET, "", equipe).param("status", "INVALIDO")).andExpect(status().isBadRequest());
		mvc.perform(dr(HttpMethod.GET, "/invalido", equipe)).andExpect(status().isBadRequest());
		for (String acao : List.of("analise", "procedencia", "improcedencia")) {
			mvc.perform(dr(HttpMethod.POST, "/" + d.getId() + "/" + acao, equipe).content("{invalido")).andExpect(status().isBadRequest());
			mvc.perform(dr(HttpMethod.POST, "/" + d.getId() + "/" + acao, equipe).contentType("text/plain").content("Motivo")).andExpect(status().isUnsupportedMediaType());
			mvc.perform(dr(HttpMethod.POST, "/invalido/" + acao, equipe).content(body("Motivo"))).andExpect(status().isBadRequest());
		}
	}
	@Test void historicoVazioERecursosInexistentesRetornamPaginaVaziaOu404() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO); var equipe = usuario(PerfilUsuario.MODERADOR);
		var d = denuncia(material(dono, base(), StatusMaterial.PUBLICADO), autor, StatusDenuncia.PENDENTE);
		mvc.perform(dr(HttpMethod.GET, "/" + d.getId() + "/historico", equipe)).andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
		UUID id = UUID.randomUUID();
		for (String tail : List.of("/" + id, "/" + id + "/historico")) { mvc.perform(dr(HttpMethod.GET, tail, equipe)).andExpect(status().isNotFound()); }
		for (String acao : List.of("analise", "procedencia", "improcedencia")) { mvc.perform(dr(HttpMethod.POST, "/" + id + "/" + acao, equipe).content(body("Motivo"))).andExpect(status().isNotFound()); }
	}
	@Test void historicoNaoPossuiEscritaArbitrariaENaoExcluiRelato() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO); var equipe = usuario(PerfilUsuario.ADMINISTRADOR);
		var d = denuncia(material(dono, base(), StatusMaterial.PUBLICADO), autor, StatusDenuncia.PENDENTE); acao(d, equipe, "analise");
		for (var method : List.of(HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.POST)) {
			mvc.perform(dr(method, "/" + d.getId() + "/historico", equipe).content(body("Injetado"))).andExpect(status().isMethodNotAllowed());
			mvc.perform(dr(method, "/" + d.getId(), equipe).content(body("Injetado"))).andExpect(status().isMethodNotAllowed());
		}
		assertTrue(denuncias.existsById(d.getId())); assertEquals(1, historico.findAllByDenunciaId(d.getId(), Pageable.unpaged()).getTotalElements());
	}
	@Test void responsavelNaoPersistidoNaoModera() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO); var equipe = usuario(PerfilUsuario.MODERADOR);
		var d = denuncia(material(dono, base(), StatusMaterial.PUBLICADO), autor, StatusDenuncia.PENDENTE);
		var req = dr(HttpMethod.POST, "/" + d.getId() + "/analise", equipe).content(body("Motivo")); em.remove(equipe); em.flush();
		mvc.perform(req).andExpect(status().isUnauthorized()); assertEquals(StatusDenuncia.PENDENTE, d.getStatus());
	}
	@Test void conclusaoPermiteNovoRelatoSemReabrirAnterior() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO); var equipe = usuario(PerfilUsuario.MODERADOR);
		var material = material(dono, base(), StatusMaterial.PUBLICADO); var d = denuncia(material, autor, StatusDenuncia.PENDENTE);
		acao(d, equipe, "analise"); acao(d, equipe, "procedencia");
		mvc.perform(req(HttpMethod.POST, "/" + material.getId() + "/denuncias", autor).content("{\"motivo\":\"SPAM\",\"descricao\":\"Novo relato\"}"))
				.andExpect(status().isCreated());
		mvc.perform(dr(HttpMethod.POST, "/" + d.getId() + "/analise", equipe).content(body("Reabrir"))).andExpect(status().isConflict());
		assertEquals(StatusDenuncia.PROCEDENTE, d.getStatus());
	}
	@Test void serviceProtegePerfilMesmoForaDoController() {
		UUID id = UUID.randomUUID(); var page = org.springframework.data.domain.PageRequest.of(0, 20); var req = new DenunciaModeracaoRequest("Motivo");
		assertThrows(AuthenticationCredentialsNotFoundException.class, () -> service.listar(StatusDenuncia.PENDENTE, page));
		assertThrows(AuthenticationCredentialsNotFoundException.class, () -> service.buscar(id));
		assertThrows(AuthenticationCredentialsNotFoundException.class, () -> service.historico(id, page));
		assertThrows(AuthenticationCredentialsNotFoundException.class, () -> service.analisar(id, req));
		assertThrows(AuthenticationCredentialsNotFoundException.class, () -> service.proceder(id, req));
		assertThrows(AuthenticationCredentialsNotFoundException.class, () -> service.improceder(id, req));
		var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
		context.setAuthentication(new org.springframework.security.authentication.TestingAuthenticationToken("teste", null, "ROLE_USUARIO"));
		org.springframework.security.core.context.SecurityContextHolder.setContext(context);
		try {
			assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.listar(StatusDenuncia.PENDENTE, page));
			assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.buscar(id));
			assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.historico(id, page));
			assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.analisar(id, req));
			assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.proceder(id, req));
			assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.improceder(id, req));
		} finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
	}
	@Test void historicoPaginadoTemOrdemCronologicaENaoMisturaOutrasDenuncias() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO); var equipe = usuario(PerfilUsuario.MODERADOR); var base = base();
		var d = denuncia(material(dono, base, StatusMaterial.PUBLICADO), autor, StatusDenuncia.PENDENTE);
		var outra = denuncia(material(dono, base, StatusMaterial.PUBLICADO), autor, StatusDenuncia.PENDENTE);
		acao(d, equipe, "analise"); acao(d, equipe, "procedencia"); acao(outra, equipe, "analise");
		mvc.perform(dr(HttpMethod.GET, "/" + d.getId() + "/historico", equipe).param("page", "1").param("size", "1").param("sort", "id,desc"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(2)).andExpect(jsonPath("$.page.totalPages").value(2))
				.andExpect(jsonPath("$.content[0].denunciaId").value(d.getId().toString())).andExpect(jsonPath("$.content[0].estadoNovo").value("PROCEDENTE"));
		mvc.perform(dr(HttpMethod.GET, "", equipe).param("status", "EM_ANALISE").param("size", "1"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.page.size").value(1)).andExpect(jsonPath("$.content.length()").value(1));
	}
}
