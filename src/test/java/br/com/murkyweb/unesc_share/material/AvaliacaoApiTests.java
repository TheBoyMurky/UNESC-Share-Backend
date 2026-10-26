package br.com.murkyweb.unesc_share.material;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import br.com.murkyweb.unesc_share.interacao.avaliacao.domain.Avaliacao;
import br.com.murkyweb.unesc_share.interacao.avaliacao.dto.AvaliacaoRequest;
import br.com.murkyweb.unesc_share.interacao.avaliacao.repository.AvaliacaoRepository;
import br.com.murkyweb.unesc_share.interacao.avaliacao.service.AvaliacaoService;
import br.com.murkyweb.unesc_share.material.domain.Material;
import br.com.murkyweb.unesc_share.material.domain.StatusMaterial;
import br.com.murkyweb.unesc_share.usuario.domain.PerfilUsuario;
import br.com.murkyweb.unesc_share.usuario.domain.Usuario;

class AvaliacaoApiTests extends MaterialApiSupport {
	@Autowired AvaliacaoRepository avaliacoes;
	@Autowired AvaliacaoService service;
	private String path(Material material) { return "/" + material.getId() + "/avaliacoes"; }
	private String nota(String nota) { return json(new AvaliacaoRequest(nota == null ? null : new BigDecimal(nota))); }
	private Avaliacao avaliar(Material material, Usuario autor, String nota) {
		return avaliacoes.saveAndFlush(new Avaliacao(new BigDecimal(nota), autor, material));
	}

	@Test void qualquerPerfilPodeAvaliarMaterialDeOutroUsuarioComDtoELocationDaPropriaConsulta() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var material = material(dono, base(), StatusMaterial.PUBLICADO);
		var data = material.getDataCadastro(); String titulo = material.getTitulo();
		for (var perfil : PerfilUsuario.values()) {
			var autor = usuario(perfil);
			var response = mvc.perform(req(HttpMethod.POST, path(material), autor).content(nota("4.75")))
					.andExpect(status().isCreated()).andExpect(header().string("Location", "http://localhost/api/v1/materiais/" + material.getId() + "/avaliacoes/minha"))
					.andExpect(jsonPath("$.nota").value(4.75)).andExpect(jsonPath("$.usuarioId").value(autor.getId().toString()))
					.andExpect(jsonPath("$.materialId").value(material.getId().toString())).andReturn().getResponse();
			var node = mapper.readTree(response.getContentAsString()); UUID id = UUID.fromString(node.get("id").asText());
			assertEquals(5, node.size()); assertNotNull(java.time.Instant.parse(node.get("dataCadastro").asText()));
			assertEquals(autor.getId(), avaliacoes.findById(id).orElseThrow().getUsuario().getId());
			assertFalse(response.getContentAsString().contains(autor.getEmail())); assertFalse(response.getContentAsString().contains("senha"));
			mvc.perform(req(HttpMethod.GET, path(material) + "/minha", autor)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()));
		}
		assertEquals(StatusMaterial.PUBLICADO, material.getStatus()); assertEquals(data, material.getDataCadastro());
		assertEquals(titulo, material.getTitulo()); assertEquals(0, material.getVisualizacoes()); assertEquals(1, magnets.countByMaterialId(material.getId()));
	}
	@Test void aceitaLimitesEValoresComMenosCasasNormalizandoParaDuasSemArredondar() throws Exception {
		var material = material(usuario(PerfilUsuario.USUARIO), base(), StatusMaterial.PUBLICADO);
		for (String valor : List.of("0.00", "5.00", "3.75", "4", "4.5")) {
			var autor = usuario(PerfilUsuario.USUARIO);
			mvc.perform(req(HttpMethod.POST, path(material), autor).content(nota(valor))).andExpect(status().isCreated());
			em.clear(); var salva = avaliacoes.findByUsuarioIdAndMaterialId(autor.getId(), material.getId()).orElseThrow();
			assertEquals(new BigDecimal(valor).setScale(2), salva.getNota());
		}
	}
	@Test void rejeitaNuloAusenteNegativoMaiorQueCincoETresCasasSemArredondamento() throws Exception {
		var material = material(usuario(PerfilUsuario.USUARIO), base(), StatusMaterial.PUBLICADO); var autor = usuario(PerfilUsuario.USUARIO);
		long before = avaliacoes.count();
		for (String valor : new String[]{null, "-0.01", "5.01", "10.00", "3.755", "4.000", "0.000", "5.001"}) {
			mvc.perform(req(HttpMethod.POST, path(material), autor).content(nota(valor))).andExpect(status().isBadRequest())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		}
		mvc.perform(req(HttpMethod.POST, path(material), autor).content("{}")).andExpect(status().isBadRequest());
		assertEquals(before, avaliacoes.count());
	}
	@Test void segundoCadastroRetorna409PreservaNotaIdDataEContagem() throws Exception {
		var material = material(usuario(PerfilUsuario.USUARIO), base(), StatusMaterial.PUBLICADO); var autor = usuario(PerfilUsuario.USUARIO);
		mvc.perform(req(HttpMethod.POST, path(material), autor).content(nota("3.75"))).andExpect(status().isCreated());
		var original = avaliacoes.findByUsuarioIdAndMaterialId(autor.getId(), material.getId()).orElseThrow(); var data = original.getDataCadastro();
		mvc.perform(req(HttpMethod.POST, path(material), autor).content(nota("5.00"))).andExpect(status().isConflict());
		em.flush(); em.refresh(original); assertEquals(new BigDecimal("3.75"), original.getNota()); assertEquals(data, original.getDataCadastro());
		mvc.perform(req(HttpMethod.GET, path(material) + "/resumo", autor)).andExpect(status().isOk()).andExpect(jsonPath("$.quantidade").value(1));
	}
	@Test void mesmoUsuarioPodeAvaliarMateriaisDiferentes() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO); var base = base();
		for (int i = 0; i < 2; i++) {
			var material = material(dono, base, StatusMaterial.PUBLICADO);
			mvc.perform(req(HttpMethod.POST, path(material), autor).content(nota("2.50"))).andExpect(status().isCreated());
		}
	}
	@Test void donoNaoPodeAutoavaliarMesmoSendoModeradorOuAdministrador() throws Exception {
		var base = base();
		for (var perfil : PerfilUsuario.values()) {
			var dono = usuario(perfil); var material = material(dono, base, StatusMaterial.PUBLICADO);
			mvc.perform(req(HttpMethod.POST, path(material), dono).content(nota("5.00"))).andExpect(status().isForbidden());
			assertFalse(avaliacoes.existsByUsuarioIdAndMaterialId(dono.getId(), material.getId()));
		}
	}
	@Test void nenhumPerfilConsultaOuCadastraEmMaterialNaoPublicadoMesmoDonoOuAvaliador() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO); var base = base();
		var leitores = List.of(dono, autor, usuario(PerfilUsuario.MODERADOR), usuario(PerfilUsuario.ADMINISTRADOR));
		for (var state : List.of(StatusMaterial.PENDENTE, StatusMaterial.BLOQUEADO, StatusMaterial.REMOVIDO)) {
			var material = material(dono, base, state); var antiga = avaliar(material, autor, "4.75");
			for (var leitor : leitores) {
				mvc.perform(req(HttpMethod.POST, path(material), leitor).content(nota("4.00"))).andExpect(status().isNotFound());
				mvc.perform(req(HttpMethod.GET, path(material) + "/minha", leitor)).andExpect(status().isNotFound());
				mvc.perform(req(HttpMethod.GET, path(material) + "/resumo", leitor)).andExpect(status().isNotFound());
			}
			assertTrue(avaliacoes.existsById(antiga.getId())); assertEquals(new BigDecimal("4.75"), antiga.getNota()); assertEquals(state, material.getStatus());
		}
	}
	@Test void rotasExigemJwtSemAlterarRegistros() throws Exception {
		String path = "/" + UUID.randomUUID() + "/avaliacoes";
		mvc.perform(req(HttpMethod.POST, path, null).content(nota("3.75"))).andExpect(status().isUnauthorized());
		mvc.perform(req(HttpMethod.GET, path + "/minha", null)).andExpect(status().isUnauthorized());
		mvc.perform(req(HttpMethod.GET, path + "/resumo", null)).andExpect(status().isUnauthorized());
	}
	@Test void consultaMinhaNaoAceitaIdentidadeForjadaOuExibeNotaDeTerceiro() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO); var terceiro = usuario(PerfilUsuario.ADMINISTRADOR);
		var material = material(dono, base(), StatusMaterial.PUBLICADO); var original = avaliar(material, autor, "1.25");
		mvc.perform(req(HttpMethod.GET, path(material) + "/minha", terceiro).param("usuarioId", autor.getId().toString()).param("id", original.getId().toString()))
				.andExpect(status().isNotFound());
		var propria = avaliar(material, terceiro, "4.50");
		mvc.perform(req(HttpMethod.GET, path(material) + "/minha", terceiro).param("usuarioId", autor.getId().toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(propria.getId().toString())).andExpect(jsonPath("$.nota").value(4.50));
	}
	@Test void jsonNaoEscolheAutorMaterialIdDataNemEstatisticas() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO); var base = base();
		var material = material(dono, base, StatusMaterial.PUBLICADO); var outro = material(dono, base, StatusMaterial.PUBLICADO);
		UUID forjado = UUID.randomUUID();
		var response = mvc.perform(req(HttpMethod.POST, path(material), autor).content(json(Map.of("nota", new BigDecimal("3.75"),
				"id", forjado, "usuarioId", dono.getId(), "materialId", outro.getId(), "dataCadastro", "2000-01-01T00:00:00Z", "media", 5, "quantidade", 100))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.usuarioId").value(autor.getId().toString()))
				.andExpect(jsonPath("$.materialId").value(material.getId().toString())).andReturn().getResponse();
		var node = mapper.readTree(response.getContentAsString()); assertNotEquals(forjado.toString(), node.get("id").asText());
		assertTrue(java.time.Instant.parse(node.get("dataCadastro").asText()).isAfter(java.time.Instant.parse("2020-01-01T00:00:00Z")));
		assertFalse(avaliacoes.existsByUsuarioIdAndMaterialId(dono.getId(), material.getId()));
		assertFalse(avaliacoes.existsByUsuarioIdAndMaterialId(autor.getId(), outro.getId()));
	}
	@Test void resumoSemNotasTemMediaNulaEDiferenciaNotaZero() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO); var material = material(dono, base(), StatusMaterial.PUBLICADO);
		var vazio = mvc.perform(req(HttpMethod.GET, path(material) + "/resumo", dono)).andExpect(status().isOk()).andExpect(jsonPath("$.quantidade").value(0))
				.andReturn().getResponse();
		var node = mapper.readTree(vazio.getContentAsString()); assertEquals(2, node.size()); assertTrue(node.has("media")); assertTrue(node.get("media").isNull());
		mvc.perform(req(HttpMethod.GET, path(material) + "/minha", autor)).andExpect(status().isNotFound());
		avaliar(material, autor, "0.00");
		mvc.perform(req(HttpMethod.GET, path(material) + "/resumo", dono)).andExpect(status().isOk()).andExpect(jsonPath("$.quantidade").value(1))
				.andExpect(jsonPath("$.media").value(0));
	}
	@Test void resumoCalculaMediaDecimalArredondadaEQuantidadeSemExporAvaliacoesIndividuais() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var material = material(dono, base(), StatusMaterial.PUBLICADO);
		for (String valor : List.of("4.00", "4.01")) { avaliar(material, usuario(PerfilUsuario.USUARIO), valor); }
		for (var leitor : List.of(dono, usuario(PerfilUsuario.USUARIO), usuario(PerfilUsuario.MODERADOR), usuario(PerfilUsuario.ADMINISTRADOR))) {
			var response = mvc.perform(req(HttpMethod.GET, path(material) + "/resumo", leitor))
					.andExpect(status().isOk()).andExpect(jsonPath("$.quantidade").value(2)).andExpect(jsonPath("$.media").value(4.01))
					.andReturn().getResponse();
			assertEquals(2, mapper.readTree(response.getContentAsString()).size()); assertFalse(response.getContentAsString().contains("usuarioId"));
		}
		assertEquals(StatusMaterial.PUBLICADO, material.getStatus());
	}
	@Test void resumoTemEscopoFixoDeMaterialEIncluiTodosOsAvaliadoresApesarDosParametros() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO); var base = base();
		var material = material(dono, base, StatusMaterial.PUBLICADO); var outro = material(dono, base, StatusMaterial.PUBLICADO);
		avaliar(material, autor, "1.00"); avaliar(material, usuario(PerfilUsuario.USUARIO), "3.00"); avaliar(outro, autor, "5.00");
		mvc.perform(req(HttpMethod.GET, path(material) + "/resumo", autor).param("usuarioId", autor.getId().toString())
				.param("materialId", outro.getId().toString()).param("status", "REMOVIDO"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.media").value(2.00)).andExpect(jsonPath("$.quantidade").value(2));
	}
	@Test void consultasDoRepositoryTambemImpedemLeituraDeMaterialNaoPublicado() {
		var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO); var material = material(dono, base(), StatusMaterial.PUBLICADO);
		avaliar(material, autor, "4.75");
		assertEquals(1, avaliacoes.resumir(material.getId(), StatusMaterial.PUBLICADO).getQuantidade());
		material.alterarStatus(StatusMaterial.BLOQUEADO); materiais.flush();
		assertTrue(avaliacoes.findByUsuarioIdAndMaterialIdAndMaterialStatus(autor.getId(), material.getId(), StatusMaterial.PUBLICADO).isEmpty());
		var totais = avaliacoes.resumir(material.getId(), StatusMaterial.PUBLICADO); assertEquals(0, totais.getQuantidade()); assertNull(totais.getSoma());
	}
	@Test void materialInexistenteOuUuidInvalidoRetorna404Ou400() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO);
		for (String id : List.of(UUID.randomUUID().toString(), "invalido")) {
			int expected = id.equals("invalido") ? 400 : 404; String path = "/" + id + "/avaliacoes";
			mvc.perform(req(HttpMethod.POST, path, autor).content(nota("3.75"))).andExpect(status().is(expected));
			mvc.perform(req(HttpMethod.GET, path + "/minha", autor)).andExpect(status().is(expected));
			mvc.perform(req(HttpMethod.GET, path + "/resumo", autor)).andExpect(status().is(expected));
		}
	}
	@Test void naoExistemEscritasGenericasOuListagemIndividualDeTerceiros() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var material = material(usuario(PerfilUsuario.USUARIO), base(), StatusMaterial.PUBLICADO);
		var avaliacao = avaliar(material, autor, "3.75");
		for (var perfil : PerfilUsuario.values()) {
			var leitor = perfil == PerfilUsuario.USUARIO ? autor : usuario(perfil);
			for (String path : List.of(path(material), path(material) + "/resumo")) {
				for (var method : List.of(HttpMethod.PUT, HttpMethod.DELETE)) {
					mvc.perform(req(method, path, leitor).content(nota("5.00"))).andExpect(status().isMethodNotAllowed());
				}
			}
			mvc.perform(req(HttpMethod.GET, path(material), leitor)).andExpect(status().isMethodNotAllowed());
			mvc.perform(req(HttpMethod.GET, path(material) + "/" + avaliacao.getId(), leitor)).andExpect(status().isNotFound());
		}
		assertEquals(new BigDecimal("3.75"), avaliacao.getNota()); assertTrue(avaliacoes.existsById(avaliacao.getId()));
	}
	@Test void aceitaVendorJsonERejeitaJsonInvalidoVirgulaDecimalETipoIncorreto() throws Exception {
		var material = material(usuario(PerfilUsuario.USUARIO), base(), StatusMaterial.PUBLICADO); var autor = usuario(PerfilUsuario.USUARIO);
		for (String body : List.of("{invalido", "{\"nota\":4,75}", "{\"nota\":\"4,75\"}", "{\"nota\":true}", "{\"nota\":{}}")) {
			mvc.perform(req(HttpMethod.POST, path(material), autor).content(body)).andExpect(status().isBadRequest());
		}
		mvc.perform(req(HttpMethod.POST, path(material), autor).contentType("application/pdf").content("%PDF-1.7")).andExpect(status().isUnsupportedMediaType());
		mvc.perform(req(HttpMethod.POST, path(material), autor).contentType("application/vnd.unescshare+json").content(nota("4.75")))
				.andExpect(status().isCreated());
	}
	@Test void usuarioNaoPersistidoNaoInsereAvaliacao() throws Exception {
		var material = material(usuario(PerfilUsuario.USUARIO), base(), StatusMaterial.PUBLICADO); var autor = usuario(PerfilUsuario.USUARIO);
		var request = req(HttpMethod.POST, path(material), autor).content(nota("3.75")); em.remove(autor); em.flush();
		mvc.perform(request).andExpect(status().isUnauthorized()); assertFalse(avaliacoes.existsByUsuarioIdAndMaterialId(autor.getId(), material.getId()));
	}
	@Test void serviceExigeAutenticacaoSemDependerDoController() {
		UUID id = UUID.randomUUID();
		assertThrows(AuthenticationCredentialsNotFoundException.class, () -> service.cadastrar(id, new AvaliacaoRequest(new BigDecimal("3.75"))));
		assertThrows(AuthenticationCredentialsNotFoundException.class, () -> service.minha(id));
		assertThrows(AuthenticationCredentialsNotFoundException.class, () -> service.resumo(id));
		assertThrows(AuthenticationCredentialsNotFoundException.class, () -> service.atualizar(id, new AvaliacaoRequest(new BigDecimal("3.75"))));
		assertThrows(AuthenticationCredentialsNotFoundException.class, () -> service.remover(id));
	}
}
