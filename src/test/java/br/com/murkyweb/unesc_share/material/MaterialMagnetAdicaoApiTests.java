package br.com.murkyweb.unesc_share.material;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import br.com.murkyweb.unesc_share.compartilhamento.magnet.dto.MagnetAdicaoRequest;
import br.com.murkyweb.unesc_share.compartilhamento.torrent.domain.Torrent;
import br.com.murkyweb.unesc_share.material.domain.Material;
import br.com.murkyweb.unesc_share.material.domain.StatusMaterial;
import br.com.murkyweb.unesc_share.usuario.domain.PerfilUsuario;

class MaterialMagnetAdicaoApiTests extends MaterialApiSupport {
	private String path(Material material) { return "/" + material.getId() + "/magnets"; }
	private String body(List<String> uris) { return json(new MagnetAdicaoRequest(uris)); }

	@Test void autorDeQualquerPerfilAdicionaLoteEPreservacaoDeMetadadosEMagnetsAnteriores() throws Exception {
		var base = base();
		for (var perfil : PerfilUsuario.values()) {
			var autor = usuario(perfil);
			for (var state : List.of(StatusMaterial.PENDENTE, StatusMaterial.PUBLICADO)) {
				var material = material(autor, base, state); var antigo = magnets.findAllByMaterialIdOrderByDataCadastroAsc(material.getId()).getFirst();
				String titulo = material.getTitulo(); var cadastro = material.getDataCadastro();
				mvc.perform(req(HttpMethod.POST, path(material), autor).content(body(List.of(uri(), uri()))))
						.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(material.getId().toString()))
						.andExpect(jsonPath("$.status").value("PENDENTE")).andExpect(jsonPath("$.magnets.length()").value(3))
						.andExpect(jsonPath("$.usuarioId").value(autor.getId().toString())).andExpect(header().doesNotExist("Location"))
						.andExpect(jsonPath("$.senha").doesNotExist()).andExpect(jsonPath("$.email").doesNotExist());
				assertEquals(StatusMaterial.PENDENTE, material.getStatus()); assertEquals(titulo, material.getTitulo());
				assertEquals(cadastro, material.getDataCadastro()); assertEquals(0, material.getVisualizacoes());
				assertTrue(magnets.existsById(antigo.getId())); assertEquals(antigo.getMagnetUri(), magnets.findById(antigo.getId()).orElseThrow().getMagnetUri());
			}
		}
	}
	@Test void permiteAdicionarPrimeiroMagnetComHashDeTorrentDoMesmoMaterial() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var base = base();
		var material = materiais.saveAndFlush(new Material("Material so com Torrent", "Descricao", autor, base.disciplina(), base.categoria()));
		String uri = uri(), hash = uri.substring("magnet:?xt=urn:btih:".length());
		var torrent = new Torrent("exemplo.torrent", UUID.randomUUID() + ".torrent", hash, 1, material); em.persist(torrent); em.flush();
		mvc.perform(req(HttpMethod.POST, path(material), autor).content(body(List.of(uri))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.magnets.length()").value(1))
				.andExpect(jsonPath("$.torrents[0].id").value(torrent.getId().toString()));
		assertEquals(1, magnets.countByMaterialId(material.getId())); assertTrue(em.contains(torrent));
	}
	@Test void hashDeTorrentDeOutroMaterialMesmoRemovidoRetorna409SemMudancas() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var base = base();
		var origem = material(autor, base, StatusMaterial.REMOVIDO); var destino = material(autor, base, StatusMaterial.PUBLICADO);
		String uri = uri(), hash = uri.substring("magnet:?xt=urn:btih:".length());
		em.persist(new Torrent("exemplo.torrent", UUID.randomUUID() + ".torrent", hash, 1, origem)); em.flush();
		mvc.perform(req(HttpMethod.POST, path(destino), autor).content(body(List.of(uri)))).andExpect(status().isConflict());
		assertEquals(StatusMaterial.PUBLICADO, destino.getStatus()); assertEquals(1, magnets.countByMaterialId(destino.getId()));
	}
	@Test void magnetExistenteMesmoMaterialOuDeOutroRetorna409SemMudarStatus() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var base = base();
		var material = material(autor, base, StatusMaterial.PUBLICADO); var outro = material(autor, base, StatusMaterial.BLOQUEADO);
		for (var origem : List.of(material, outro)) {
			String uri = magnets.findAllByMaterialIdOrderByDataCadastroAsc(origem.getId()).getFirst().getMagnetUri();
			mvc.perform(req(HttpMethod.POST, path(material), autor).content(body(List.of(uri + "&dn=NovoNome")))).andExpect(status().isConflict());
		}
		assertEquals(StatusMaterial.PUBLICADO, material.getStatus()); assertEquals(1, magnets.countByMaterialId(material.getId()));
	}
	@Test void duplicidadeHexBase32NoLoteNaoGravaNenhum() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var material = material(autor, base(), StatusMaterial.PUBLICADO);
		mvc.perform(req(HttpMethod.POST, path(material), autor).content(body(List.of("magnet:?xt=urn:btih:" + "0".repeat(40),
				"magnet:?xt=urn:btih:" + "A".repeat(32))))).andExpect(status().isConflict());
		assertEquals(StatusMaterial.PUBLICADO, material.getStatus()); assertEquals(1, magnets.countByMaterialId(material.getId()));
	}
	@Test void loteInvalidoOuComHashExistenteNaoInsereParteValida() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var material = material(autor, base(), StatusMaterial.PUBLICADO);
		String existente = magnets.findAllByMaterialIdOrderByDataCadastroAsc(material.getId()).getFirst().getMagnetUri();
		mvc.perform(req(HttpMethod.POST, path(material), autor).content(body(List.of(uri(), "https://example.test/arquivo.pdf"))))
				.andExpect(status().isBadRequest());
		mvc.perform(req(HttpMethod.POST, path(material), autor).content(body(List.of(uri(), existente))))
				.andExpect(status().isConflict());
		assertEquals(StatusMaterial.PUBLICADO, material.getStatus()); assertEquals(1, magnets.countByMaterialId(material.getId()));
	}
	@Test void limiteAcumuladoDeDezImpedeNovasAdicoesSemAlterarEstadoOuRegistros() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var material = material(autor, base(), StatusMaterial.PUBLICADO);
		var uris = new ArrayList<String>(); for (int i = 0; i < 9; i++) { uris.add(uri()); }
		mvc.perform(req(HttpMethod.POST, path(material), autor).content(body(uris))).andExpect(status().isOk());
		assertEquals(10, magnets.countByMaterialId(material.getId()));
		material.alterarStatus(StatusMaterial.PUBLICADO); materiais.flush();
		mvc.perform(req(HttpMethod.POST, path(material), autor).content(body(List.of(uri())))).andExpect(status().isConflict());
		assertEquals(StatusMaterial.PUBLICADO, material.getStatus()); assertEquals(10, magnets.countByMaterialId(material.getId()));
	}
	@Test void loteQueExcedeSaldoNaoETruncadoNemInseridoParcialmente() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var material = material(autor, base(), StatusMaterial.PENDENTE);
		var uris = new ArrayList<String>(); for (int i = 0; i < 10; i++) { uris.add(uri()); }
		mvc.perform(req(HttpMethod.POST, path(material), autor).content(body(uris))).andExpect(status().isConflict());
		assertEquals(1, magnets.countByMaterialId(material.getId()));
	}
	@Test void validaTamanhoDaListaEDasUrisPeloBeanValidation() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var material = material(autor, base(), StatusMaterial.PENDENTE);
		for (var request : List.of(new MagnetAdicaoRequest(null), new MagnetAdicaoRequest(List.of()),
				new MagnetAdicaoRequest(Collections.singletonList(null)), new MagnetAdicaoRequest(List.of(" ")),
				new MagnetAdicaoRequest(List.of("x".repeat(4001))), new MagnetAdicaoRequest(Collections.nCopies(11, uri())))) {
			mvc.perform(req(HttpMethod.POST, path(material), autor).content(json(request))).andExpect(status().isBadRequest())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		}
		assertEquals(1, magnets.countByMaterialId(material.getId()));
	}
	@Test void usuarioAnonimoRecebe401SemGravarMagnet() throws Exception {
		var material = material(usuario(PerfilUsuario.USUARIO), base(), StatusMaterial.PENDENTE);
		mvc.perform(req(HttpMethod.POST, path(material), null).content(body(List.of(uri())))).andExpect(status().isUnauthorized());
		assertEquals(1, magnets.countByMaterialId(material.getId()));
	}
	@Test void terceirosInclusiveEquipeNaoPodemAdicionarEmQualquerEstado() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var base = base();
		for (var state : StatusMaterial.values()) {
			var material = material(autor, base, state);
			for (var perfil : PerfilUsuario.values()) {
				mvc.perform(req(HttpMethod.POST, path(material), usuario(perfil)).content(body(List.of(uri())))).andExpect(status().isNotFound());
			}
			assertEquals(state, material.getStatus()); assertEquals(1, magnets.countByMaterialId(material.getId()));
		}
	}
	@Test void autorNaoAdicionaEmBloqueadoOuRemovido() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var base = base();
		for (var state : List.of(StatusMaterial.BLOQUEADO, StatusMaterial.REMOVIDO)) {
			var material = material(autor, base, state);
			mvc.perform(req(HttpMethod.POST, path(material), autor).content(body(List.of(uri())))).andExpect(status().isConflict());
			assertEquals(state, material.getStatus()); assertEquals(1, magnets.countByMaterialId(material.getId()));
		}
	}
	@Test void ignoraTentativasDeAlterarAutorMaterialStatusOuMetadadosPeloJson() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var material = material(autor, base(), StatusMaterial.PUBLICADO); String titulo = material.getTitulo();
		mvc.perform(req(HttpMethod.POST, path(material), autor).content(json(Map.of("magnetUris", List.of(uri()),
				"usuarioId", UUID.randomUUID(), "materialId", UUID.randomUUID(), "status", "PUBLICADO", "titulo", "forjado"))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.titulo").value(titulo)).andExpect(jsonPath("$.status").value("PENDENTE"))
				.andExpect(jsonPath("$.usuarioId").value(autor.getId().toString()));
	}
	@Test void aceitaV2ECanonizaV1SemContatoComTrackers() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var material = material(autor, base(), StatusMaterial.PENDENTE);
		String hash = UUID.randomUUID().toString().replace("-", "").toUpperCase(java.util.Locale.ROOT) + "01234567";
		String v2 = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
		mvc.perform(req(HttpMethod.POST, path(material), autor).contentType("application/vnd.unescshare+json")
				.content(body(List.of("magnet:?xt=urn:btih:" + hash + "&tr=http%3A%2F%2F127.0.0.1%3A1%2Fnao-acessar",
						"magnet:?xt=urn:btmh:1220" + v2)))).andExpect(status().isOk());
		assertTrue(magnets.existsByInfoHashIgnoreCase(hash.toLowerCase(java.util.Locale.ROOT)));
		assertTrue(magnets.existsByInfoHashIgnoreCase(v2));
	}
	@Test void ausenteOuUuidInvalidoJsonMalformadoETipoErrado() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var material = material(autor, base(), StatusMaterial.PENDENTE);
		mvc.perform(req(HttpMethod.POST, "/" + UUID.randomUUID() + "/magnets", autor).content(body(List.of(uri())))).andExpect(status().isNotFound());
		mvc.perform(req(HttpMethod.POST, "/invalido/magnets", autor).content(body(List.of(uri())))).andExpect(status().isBadRequest());
		mvc.perform(req(HttpMethod.POST, path(material), autor).content("{invalido")).andExpect(status().isBadRequest());
		mvc.perform(req(HttpMethod.POST, path(material), autor).contentType("application/pdf").content("%PDF-1.7"))
				.andExpect(status().isUnsupportedMediaType());
		assertEquals(1, magnets.countByMaterialId(material.getId()));
	}
	@Test void dtoNaoExpoeUriPrivadaNoToString() {
		assertFalse(new MagnetAdicaoRequest(List.of("magnet:?xt=urn:btih:" + "0".repeat(40) + "&tr=CANARY_PRIVADO")).toString().contains("CANARY_PRIVADO"));
	}
	@Test void erroDeValidacaoNaoEcoaParametrosPrivadosDaUri() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var material = material(autor, base(), StatusMaterial.PUBLICADO);
		var response = mvc.perform(req(HttpMethod.POST, path(material), autor)
				.content(body(List.of("magnet:?xt=urn:btih:invalido&tr=CANARY_PRIVADO"))))
				.andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
		assertFalse(response.contains("CANARY_PRIVADO")); assertEquals(StatusMaterial.PUBLICADO, material.getStatus());
	}
}
