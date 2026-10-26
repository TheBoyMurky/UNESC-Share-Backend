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

import br.com.murkyweb.unesc_share.compartilhamento.torrent.domain.Torrent;
import br.com.murkyweb.unesc_share.interacao.avaliacao.domain.Avaliacao;
import br.com.murkyweb.unesc_share.interacao.avaliacao.dto.AvaliacaoRequest;
import br.com.murkyweb.unesc_share.interacao.avaliacao.repository.AvaliacaoRepository;
import br.com.murkyweb.unesc_share.interacao.comentario.domain.Comentario;
import br.com.murkyweb.unesc_share.material.domain.Material;
import br.com.murkyweb.unesc_share.material.domain.StatusMaterial;
import br.com.murkyweb.unesc_share.usuario.domain.PerfilUsuario;
import br.com.murkyweb.unesc_share.usuario.domain.Usuario;

class AvaliacaoEdicaoRemocaoApiTests extends MaterialApiSupport {
	@Autowired AvaliacaoRepository avaliacoes;
	private Avaliacao avaliar(Material material, Usuario autor, String nota) {
		return avaliacoes.saveAndFlush(new Avaliacao(new BigDecimal(nota), autor, material));
	}
	private String path(Material material) { return "/" + material.getId() + "/avaliacoes/minha"; }
	private String nota(String nota) { return json(new AvaliacaoRequest(nota == null ? null : new BigDecimal(nota))); }
	private String resumo(Material material) { return "/" + material.getId() + "/avaliacoes/resumo"; }

	@Test void autorDeQualquerPerfilEditaNotaPreservandoIdentidadeDataMaterialEOutrasAvaliacoes() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var material = material(dono, base(), StatusMaterial.PUBLICADO);
		var titulo = material.getTitulo(); var dataMaterial = material.getDataCadastro();
		for (var perfil : PerfilUsuario.values()) {
			var autor = usuario(perfil); var avaliacao = avaliar(material, autor, "3.75"); var data = avaliacao.getDataCadastro();
			mvc.perform(req(HttpMethod.PUT, path(material), autor).content(nota("4.5")))
					.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(avaliacao.getId().toString()))
					.andExpect(jsonPath("$.nota").value(4.50)).andExpect(jsonPath("$.usuarioId").value(autor.getId().toString()))
					.andExpect(jsonPath("$.materialId").value(material.getId().toString()));
			em.flush(); em.refresh(avaliacao); assertEquals(data, avaliacao.getDataCadastro()); assertEquals(new BigDecimal("4.50"), avaliacao.getNota());
		}
		assertEquals(StatusMaterial.PUBLICADO, material.getStatus()); assertEquals(titulo, material.getTitulo());
		assertEquals(dataMaterial, material.getDataCadastro()); assertEquals(0, material.getVisualizacoes());
		assertEquals(1, magnets.countByMaterialId(material.getId()));
		mvc.perform(req(HttpMethod.GET, resumo(material), dono)).andExpect(status().isOk())
				.andExpect(jsonPath("$.quantidade").value(3)).andExpect(jsonPath("$.media").value(4.50));
	}
	@Test void edicaoAtualizaMediaSemMudarQuantidadeERemocaoAtualizaAmbas() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO); var outro = usuario(PerfilUsuario.USUARIO);
		var material = material(dono, base(), StatusMaterial.PUBLICADO); avaliar(material, autor, "1.00"); var preservada = avaliar(material, outro, "3.00");
		mvc.perform(req(HttpMethod.PUT, path(material), autor).content(nota("5.00"))).andExpect(status().isOk());
		mvc.perform(req(HttpMethod.GET, resumo(material), dono)).andExpect(status().isOk()).andExpect(jsonPath("$.quantidade").value(2)).andExpect(jsonPath("$.media").value(4.00));
		mvc.perform(req(HttpMethod.DELETE, path(material), autor)).andExpect(status().isNoContent());
		mvc.perform(req(HttpMethod.GET, resumo(material), dono)).andExpect(status().isOk()).andExpect(jsonPath("$.quantidade").value(1)).andExpect(jsonPath("$.media").value(3.00));
		assertTrue(avaliacoes.existsById(preservada.getId())); assertEquals(new BigDecimal("3.00"), preservada.getNota());
		mvc.perform(req(HttpMethod.DELETE, path(material), outro)).andExpect(status().isNoContent());
		var response = mvc.perform(req(HttpMethod.GET, resumo(material), dono)).andExpect(status().isOk()).andExpect(jsonPath("$.quantidade").value(0)).andReturn().getResponse();
		assertTrue(mapper.readTree(response.getContentAsString()).get("media").isNull());
	}
	@Test void remocaoFisicaIdempotenteEmQualquerEstadoPreservaMaterialVinculosEOutrasNotas() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO); var outro = usuario(PerfilUsuario.USUARIO); var base = base();
		for (var estado : StatusMaterial.values()) {
			var material = material(dono, base, estado); var avaliacao = avaliar(material, autor, "3.75"); var preservada = avaliar(material, outro, "4.00");
			var comentario = new Comentario("Comentario preservado", autor, material); em.persist(comentario);
			var torrent = new Torrent("fixture.torrent", "testes/fixture-sem-arquivo.torrent", UUID.randomUUID().toString().replace("-", "") + "01234567", 100, material);
			em.persist(torrent); em.flush();
			for (int i = 0; i < 2; i++) {
				mvc.perform(req(HttpMethod.DELETE, path(material), autor)).andExpect(status().isNoContent()).andExpect(content().string(""));
			}
			em.flush(); em.clear(); assertFalse(avaliacoes.existsById(avaliacao.getId())); assertTrue(avaliacoes.existsById(preservada.getId()));
			assertEquals(estado, materiais.findById(material.getId()).orElseThrow().getStatus());
			assertEquals(1, magnets.countByMaterialId(material.getId())); assertNotNull(em.find(Torrent.class, torrent.getId())); assertNotNull(em.find(Comentario.class, comentario.getId()));
			assertNotNull(em.find(Usuario.class, autor.getId())); assertNotNull(em.find(base.disciplina().getClass(), base.disciplina().getId()));
		}
	}
	@Test void todosOsPerfisPodemExcluirSomenteSuaPropriaNota() throws Exception {
		var material = material(usuario(PerfilUsuario.USUARIO), base(), StatusMaterial.PUBLICADO);
		for (var perfil : PerfilUsuario.values()) {
			var autor = usuario(perfil); var avaliacao = avaliar(material, autor, "4.00");
			mvc.perform(req(HttpMethod.DELETE, path(material), autor)).andExpect(status().isNoContent()); assertFalse(avaliacoes.existsById(avaliacao.getId()));
		}
	}
	@Test void aposExcluirPodeAvaliarNovamenteComNovoUuidEData() throws Exception {
		var material = material(usuario(PerfilUsuario.USUARIO), base(), StatusMaterial.PUBLICADO); var autor = usuario(PerfilUsuario.USUARIO);
		var original = avaliar(material, autor, "1.00"); var data = original.getDataCadastro();
		mvc.perform(req(HttpMethod.DELETE, path(material), autor)).andExpect(status().isNoContent());
		mvc.perform(req(HttpMethod.GET, path(material), autor)).andExpect(status().isNotFound());
		mvc.perform(req(HttpMethod.PUT, path(material), autor).content(nota("5.00"))).andExpect(status().isNotFound());
		mvc.perform(req(HttpMethod.POST, "/" + material.getId() + "/avaliacoes", autor).content(nota("5.00"))).andExpect(status().isCreated());
		var nova = avaliacoes.findByUsuarioIdAndMaterialId(autor.getId(), material.getId()).orElseThrow();
		assertNotEquals(original.getId(), nova.getId()); assertTrue(nova.getDataCadastro().isAfter(data));
		assertEquals(new BigDecimal("5.00"), nova.getNota()); assertFalse(avaliacoes.existsById(original.getId()));
	}
	@Test void putNaoCriaNotaAusenteEDeleteAusenteRetorna204SemExporTerceiros() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO); var material = material(dono, base(), StatusMaterial.PUBLICADO);
		var avaliacao = avaliar(material, autor, "3.75");
		for (var terceiro : List.of(usuario(PerfilUsuario.USUARIO), usuario(PerfilUsuario.MODERADOR), usuario(PerfilUsuario.ADMINISTRADOR))) {
			mvc.perform(req(HttpMethod.PUT, path(material), terceiro).param("usuarioId", autor.getId().toString())
					.content(json(Map.of("nota", 5, "usuarioId", autor.getId(), "id", avaliacao.getId())))).andExpect(status().isNotFound());
			mvc.perform(req(HttpMethod.DELETE, path(material), terceiro).param("usuarioId", autor.getId().toString())
					.content(json(Map.of("id", avaliacao.getId(), "usuarioId", autor.getId()))))
					.andExpect(status().isNoContent()).andExpect(content().string(""));
			assertFalse(avaliacoes.existsByUsuarioIdAndMaterialId(terceiro.getId(), material.getId()));
		}
		mvc.perform(req(HttpMethod.DELETE, path(material), dono).param("usuarioId", autor.getId().toString())).andExpect(status().isNoContent());
		assertTrue(avaliacoes.existsById(avaliacao.getId())); assertEquals(new BigDecimal("3.75"), avaliacao.getNota());
	}
	@Test void camposExtrasNaoTrocamAutorMaterialIdentidadeDataOuNotaDeTerceiro() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var autor = usuario(PerfilUsuario.USUARIO); var outroAutor = usuario(PerfilUsuario.ADMINISTRADOR); var base = base();
		var material = material(dono, base, StatusMaterial.PUBLICADO); var outro = material(dono, base, StatusMaterial.PUBLICADO);
		var propria = avaliar(material, autor, "1.00"); var alheia = avaliar(material, outroAutor, "2.00"); var data = propria.getDataCadastro();
		mvc.perform(req(HttpMethod.PUT, path(material), autor).param("usuarioId", outroAutor.getId().toString()).content(json(Map.of(
				"nota", 5, "id", alheia.getId(), "usuarioId", outroAutor.getId(), "materialId", outro.getId(), "dataCadastro", "2000-01-01T00:00:00Z"))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(propria.getId().toString()))
				.andExpect(jsonPath("$.usuarioId").value(autor.getId().toString())).andExpect(jsonPath("$.materialId").value(material.getId().toString()));
		assertEquals(data, propria.getDataCadastro()); assertEquals(new BigDecimal("2.00"), alheia.getNota());
		assertFalse(avaliacoes.existsByUsuarioIdAndMaterialId(autor.getId(), outro.getId()));
	}
	@Test void putEmMaterialNaoPublicadoRetorna404ParaTodosSemAlterarNota() throws Exception {
		var dono = usuario(PerfilUsuario.USUARIO); var base = base();
		for (var perfil : PerfilUsuario.values()) {
			var autor = usuario(perfil);
			for (var estado : List.of(StatusMaterial.PENDENTE, StatusMaterial.BLOQUEADO, StatusMaterial.REMOVIDO)) {
				var material = material(dono, base, estado); var avaliacao = avaliar(material, autor, "3.75");
				mvc.perform(req(HttpMethod.PUT, path(material), autor).content(nota("4.00"))).andExpect(status().isNotFound());
				assertEquals(new BigDecimal("3.75"), avaliacao.getNota());
			}
		}
	}
	@Test void removerEmMaterialPrivadoNaoAbreConsultasNemDevolveNota() throws Exception {
		var material = material(usuario(PerfilUsuario.USUARIO), base(), StatusMaterial.BLOQUEADO); var autor = usuario(PerfilUsuario.USUARIO);
		var avaliacao = avaliar(material, autor, "3.75");
		mvc.perform(req(HttpMethod.DELETE, path(material), autor)).andExpect(status().isNoContent()).andExpect(content().string(""));
		mvc.perform(req(HttpMethod.GET, path(material), autor)).andExpect(status().isNotFound());
		mvc.perform(req(HttpMethod.GET, resumo(material), autor)).andExpect(status().isNotFound());
		assertFalse(avaliacoes.existsById(avaliacao.getId()));
	}
	@Test void naoPermiteEditarAutoavaliacaoLegadaMasPermiteRetiraLa() throws Exception {
		for (var perfil : PerfilUsuario.values()) {
			var dono = usuario(perfil); var material = material(dono, base(), StatusMaterial.PUBLICADO); var legada = avaliar(material, dono, "5.00");
			mvc.perform(req(HttpMethod.PUT, path(material), dono).content(nota("4.00"))).andExpect(status().isForbidden());
			assertEquals(new BigDecimal("5.00"), legada.getNota());
			mvc.perform(req(HttpMethod.DELETE, path(material), dono)).andExpect(status().isNoContent()); assertFalse(avaliacoes.existsById(legada.getId()));
		}
	}
	@Test void putValidaNotaSemArredondarEPermiteZeroCincoEVendorJson() throws Exception {
		var material = material(usuario(PerfilUsuario.USUARIO), base(), StatusMaterial.PUBLICADO); var autor = usuario(PerfilUsuario.USUARIO);
		var avaliacao = avaliar(material, autor, "3.75");
		for (String valor : new String[]{null, "-0.01", "5.01", "10", "3.755", "4.000"}) {
			mvc.perform(req(HttpMethod.PUT, path(material), autor).content(nota(valor))).andExpect(status().isBadRequest())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		}
		mvc.perform(req(HttpMethod.PUT, path(material), autor).content("{}")).andExpect(status().isBadRequest());
		assertEquals(new BigDecimal("3.75"), avaliacao.getNota());
		for (String valor : List.of("0.00", "5.00")) {
			mvc.perform(req(HttpMethod.PUT, path(material), autor).contentType("application/vnd.unescshare+json").content(nota(valor))).andExpect(status().isOk());
			assertEquals(new BigDecimal(valor), avaliacao.getNota());
		}
	}
	@Test void jsonTipoEUuidInvalidosRetornamErrosNativosSemModificarNota() throws Exception {
		var material = material(usuario(PerfilUsuario.USUARIO), base(), StatusMaterial.PUBLICADO); var autor = usuario(PerfilUsuario.USUARIO);
		var avaliacao = avaliar(material, autor, "3.75");
		mvc.perform(req(HttpMethod.PUT, path(material), autor).content("{invalido")).andExpect(status().isBadRequest());
		mvc.perform(req(HttpMethod.PUT, path(material), autor).contentType("text/plain").content("4.00")).andExpect(status().isUnsupportedMediaType());
		for (var method : List.of(HttpMethod.PUT, HttpMethod.DELETE)) {
			mvc.perform(req(method, "/invalido/avaliacoes/minha", autor).content(nota("4.00"))).andExpect(status().isBadRequest());
			mvc.perform(req(method, "/" + UUID.randomUUID() + "/avaliacoes/minha", autor).content(nota("4.00"))).andExpect(status().isNotFound());
		}
		assertTrue(avaliacoes.existsById(avaliacao.getId())); assertEquals(new BigDecimal("3.75"), avaliacao.getNota());
	}
	@Test void ambasAsRotasExigemJwt() throws Exception {
		var material = material(usuario(PerfilUsuario.USUARIO), base(), StatusMaterial.PUBLICADO); var autor = usuario(PerfilUsuario.USUARIO);
		var avaliacao = avaliar(material, autor, "3.75");
		for (var method : List.of(HttpMethod.PUT, HttpMethod.DELETE)) {
			mvc.perform(req(method, path(material), null).content(nota("4.00"))).andExpect(status().isUnauthorized());
		}
		assertTrue(avaliacoes.existsById(avaliacao.getId())); assertEquals(new BigDecimal("3.75"), avaliacao.getNota());
	}
}
