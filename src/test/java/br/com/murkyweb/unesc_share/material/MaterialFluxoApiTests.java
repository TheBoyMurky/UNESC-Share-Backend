package br.com.murkyweb.unesc_share.material;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import br.com.murkyweb.unesc_share.material.domain.Material;
import br.com.murkyweb.unesc_share.material.domain.StatusMaterial;
import br.com.murkyweb.unesc_share.material.dto.MaterialFiltro;
import br.com.murkyweb.unesc_share.material.service.MaterialQueryService;
import br.com.murkyweb.unesc_share.material.service.MaterialService;
import br.com.murkyweb.unesc_share.usuario.domain.PerfilUsuario;

class MaterialFluxoApiTests extends MaterialApiSupport {
	@Autowired private MaterialService service;
	@Autowired private MaterialQueryService consultas;

	@Test
	void somenteEquipeAcessaFilaEPublicaOuBloqueia() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO);
		var base = base();
		var pendente = material(autor, base, StatusMaterial.PENDENTE);
		mvc.perform(req(HttpMethod.GET, "/moderacao", autor)).andExpect(status().isForbidden());
		mvc.perform(req(HttpMethod.POST, "/" + pendente.getId() + "/publicacao", autor)).andExpect(status().isForbidden());
		mvc.perform(req(HttpMethod.POST, "/" + pendente.getId() + "/bloqueio", autor)).andExpect(status().isForbidden());
		for (var perfil : List.of(PerfilUsuario.MODERADOR, PerfilUsuario.ADMINISTRADOR)) {
			var equipe = usuario(perfil);
			var material = material(autor, base, StatusMaterial.PENDENTE);
			mvc.perform(req(HttpMethod.GET, "/moderacao", equipe).param("disciplinaId", base.disciplina().getId().toString()))
					.andExpect(status().isOk()).andExpect(jsonPath("$.content[0].status").value("PENDENTE"));
			mvc.perform(req(HttpMethod.POST, "/" + material.getId() + "/publicacao", equipe))
					.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLICADO"));
			mvc.perform(req(HttpMethod.POST, "/" + material.getId() + "/bloqueio", equipe))
					.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("BLOQUEADO"));
		}
	}

	@ParameterizedTest
	@EnumSource(value = PerfilUsuario.class, names = { "MODERADOR", "ADMINISTRADOR" })
	void equipePodePublicarProprioMaterialConformePoliticaAprovada(PerfilUsuario perfil) throws Exception {
		var autor = usuario(perfil);
		var material = material(autor, base(), StatusMaterial.PENDENTE);
		var mecanismo = magnets.findAllByMaterialIdOrderByDataCadastroAsc(material.getId()).getFirst();
		mvc.perform(req(HttpMethod.POST, "/" + material.getId() + "/publicacao", autor))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(material.getId().toString()))
				.andExpect(jsonPath("$.usuarioId").value(autor.getId().toString()))
				.andExpect(jsonPath("$.status").value("PUBLICADO"))
				.andExpect(jsonPath("$.magnets[0].id").value(mecanismo.getId().toString()));
		assertEquals(StatusMaterial.PUBLICADO, materiais.findById(material.getId()).orElseThrow().getStatus());
	}

	@Test
	void edicaoDePublicadoVoltaAPendenteEPreservaAutorEMagnets() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO);
		var outro = usuario(PerfilUsuario.USUARIO);
		var base = base();
		var material = material(autor, base, StatusMaterial.PUBLICADO);
		var antes = magnets.findAllByMaterialIdOrderByDataCadastroAsc(material.getId()).getFirst();
		mvc.perform(req(HttpMethod.PUT, "/" + material.getId(), autor).content(json(Map.of(
				"titulo", "Novo titulo", "descricao", "Nova descricao", "disciplinaId", base.disciplina().getId(), "categoriaId", base.categoria().getId(),
				"status", "PUBLICADO", "usuarioId", outro.getId(), "magnetUris", List.of("javascript:nao-permitido")))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDENTE"))
				.andExpect(jsonPath("$.usuarioId").value(autor.getId().toString()))
				.andExpect(jsonPath("$.magnets[0].id").value(antes.getId().toString()))
				.andExpect(jsonPath("$.magnets[0].magnetUri").value(antes.getMagnetUri()));
		mvc.perform(req(HttpMethod.GET, "/" + material.getId(), outro)).andExpect(status().isNotFound());
	}

	@Test
	void autorPodeCorrigirClassificacaoDePendenteSemTrocarIdentidade() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO);
		var material = material(autor, base(), StatusMaterial.PENDENTE);
		var outra = base();
		mvc.perform(req(HttpMethod.PUT, "/" + material.getId(), autor).content(edicao(outra, "Titulo corrigido")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(material.getId().toString()))
				.andExpect(jsonPath("$.disciplinaId").value(outra.disciplina().getId().toString()))
				.andExpect(jsonPath("$.categoriaId").value(outra.categoria().getId().toString()));
	}

	@ParameterizedTest
	@EnumSource(value = StatusMaterial.class, names = { "BLOQUEADO", "REMOVIDO" })
	void autorNaoEditaBloqueadosOuRemovidos(StatusMaterial estado) throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO);
		var base = base();
		var material = material(autor, base, estado);
		mvc.perform(req(HttpMethod.PUT, "/" + material.getId(), autor).content(edicao(base, "Tentativa")))
				.andExpect(status().isConflict());
		assertEquals(estado, materiais.findById(material.getId()).orElseThrow().getStatus());
	}

	@ParameterizedTest
	@EnumSource(StatusMaterial.class)
	void terceirosInclusiveAdministradorNaoEditamNemRemovemPeloEndpointDoAutor(StatusMaterial estado) throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO);
		var base = base();
		var material = material(autor, base, estado);
		for (var perfil : PerfilUsuario.values()) {
			var terceiro = usuario(perfil);
			mvc.perform(req(HttpMethod.PUT, "/" + material.getId(), terceiro).content(edicao(base, "Alteracao indevida")))
					.andExpect(status().isNotFound());
			mvc.perform(req(HttpMethod.DELETE, "/" + material.getId(), terceiro)).andExpect(status().isNotFound());
		}
	}

	@ParameterizedTest
	@EnumSource(StatusMaterial.class)
	void remocaoDoAutorELogicaIdempotenteEPreservaMagnet(StatusMaterial estado) throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO);
		var material = material(autor, base(), estado);
		UUID magnetId = magnets.findAllByMaterialIdOrderByDataCadastroAsc(material.getId()).getFirst().getId();
		for (int i = 0; i < 2; i++) {
			mvc.perform(req(HttpMethod.DELETE, "/" + material.getId(), autor)).andExpect(status().isNoContent());
		}
		em.clear();
		assertEquals(StatusMaterial.REMOVIDO, materiais.findById(material.getId()).orElseThrow().getStatus());
		assertTrue(magnets.existsById(magnetId));
		mvc.perform(req(HttpMethod.GET, "/" + material.getId(), autor)).andExpect(status().isOk());
	}

	@Test
	void naoPublicaOrfaoBloqueadoRemovidoOuJaPublicado() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO);
		var equipe = usuario(PerfilUsuario.MODERADOR);
		var base = base();
		var orfao = materiais.saveAndFlush(new Material("Legado sem mecanismo", "Descricao", autor, base.disciplina(), base.categoria()));
		mvc.perform(req(HttpMethod.POST, "/" + orfao.getId() + "/publicacao", equipe)).andExpect(status().isConflict());
		for (var estado : List.of(StatusMaterial.PUBLICADO, StatusMaterial.BLOQUEADO, StatusMaterial.REMOVIDO)) {
			var material = material(autor, base, estado);
			mvc.perform(req(HttpMethod.POST, "/" + material.getId() + "/publicacao", equipe)).andExpect(status().isConflict());
		}
	}

	@Test
	void bloqueioAceitaPendenteERepeticaoMasNaoRessuscitaRemovido() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO);
		var equipe = usuario(PerfilUsuario.MODERADOR);
		var base = base();
		var pendente = material(autor, base, StatusMaterial.PENDENTE);
		for (int i = 0; i < 2; i++) {
			mvc.perform(req(HttpMethod.POST, "/" + pendente.getId() + "/bloqueio", equipe))
					.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("BLOQUEADO"));
		}
		var removido = material(autor, base, StatusMaterial.REMOVIDO);
		mvc.perform(req(HttpMethod.POST, "/" + removido.getId() + "/bloqueio", equipe)).andExpect(status().isConflict());
	}

	@Test
	void referenciasInvalidasNaEdicaoNaoAlteramMetadados() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO);
		var base = base();
		var material = material(autor, base, StatusMaterial.PUBLICADO);
		String titulo = material.getTitulo();
		mvc.perform(req(HttpMethod.PUT, "/" + material.getId(), autor).content(json(Map.of("titulo", "Alteracao", "descricao", "Descricao",
				"disciplinaId", UUID.randomUUID(), "categoriaId", base.categoria().getId())))).andExpect(status().isNotFound());
		assertEquals(titulo, materiais.findById(material.getId()).orElseThrow().getTitulo());
		assertEquals(StatusMaterial.PUBLICADO, material.getStatus());
	}

	@Test
	void idsInexistentesOuMalformadosNaoExponemDetalhes() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO);
		var equipe = usuario(PerfilUsuario.ADMINISTRADOR);
		var base = base();
		String id = "/" + UUID.randomUUID();
		mvc.perform(req(HttpMethod.GET, id, autor)).andExpect(status().isNotFound());
		mvc.perform(req(HttpMethod.PUT, id, autor).content(edicao(base, "Titulo"))).andExpect(status().isNotFound());
		mvc.perform(req(HttpMethod.DELETE, id, autor)).andExpect(status().isNotFound());
		mvc.perform(req(HttpMethod.POST, id + "/publicacao", equipe)).andExpect(status().isNotFound());
		mvc.perform(req(HttpMethod.GET, "/invalido", autor)).andExpect(status().isBadRequest());
		mvc.perform(req(HttpMethod.PUT, "/invalido", autor).content(edicao(base, "Titulo"))).andExpect(status().isBadRequest());
		mvc.perform(req(HttpMethod.DELETE, "/invalido", autor)).andExpect(status().isBadRequest());
	}

	@Test
	@WithMockUser(roles = "USUARIO")
	void servicosDeModeracaoTambemExigemPerfilForaDoController() {
		assertThrows(AccessDeniedException.class, () -> service.publicar(UUID.randomUUID()));
		assertThrows(AccessDeniedException.class, () -> service.bloquear(UUID.randomUUID()));
		assertThrows(AccessDeniedException.class, () -> consultas.listarModeracao(StatusMaterial.PENDENTE,
				new MaterialFiltro(null, null, null, null, null), org.springframework.data.domain.PageRequest.of(0, 20)));
	}
}
