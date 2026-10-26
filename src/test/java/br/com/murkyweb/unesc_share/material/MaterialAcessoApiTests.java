package br.com.murkyweb.unesc_share.material;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpMethod;

import br.com.murkyweb.unesc_share.material.domain.StatusMaterial;
import br.com.murkyweb.unesc_share.usuario.domain.PerfilUsuario;

class MaterialAcessoApiTests extends MaterialApiSupport {

	@Test
	void todosOsEndpointsExigemAutenticacao() throws Exception {
		String id = "/" + UUID.randomUUID();
		for (var request : List.of(req(HttpMethod.GET, "", null), req(HttpMethod.GET, "/meus", null), req(HttpMethod.GET, "/moderacao", null),
				req(HttpMethod.GET, id, null), req(HttpMethod.POST, "", null), req(HttpMethod.PUT, id, null), req(HttpMethod.DELETE, id, null),
				req(HttpMethod.POST, id + "/publicacao", null), req(HttpMethod.POST, id + "/bloqueio", null))) {
			mvc.perform(request).andExpect(status().isUnauthorized());
		}
	}

	@ParameterizedTest
	@EnumSource(StatusMaterial.class)
	void detalheSoExpoeNaoPublicadosAoAutorOuEquipe(StatusMaterial estado) throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO);
		var outro = usuario(PerfilUsuario.USUARIO);
		var moderador = usuario(PerfilUsuario.MODERADOR);
		var material = material(autor, base(), estado);
		mvc.perform(req(HttpMethod.GET, "/" + material.getId(), autor)).andExpect(status().isOk());
		mvc.perform(req(HttpMethod.GET, "/" + material.getId(), moderador)).andExpect(status().isOk());
		mvc.perform(req(HttpMethod.GET, "/" + material.getId(), outro))
				.andExpect(estado == StatusMaterial.PUBLICADO ? status().isOk() : status().isNotFound());
	}

	@Test
	void buscaGeralIgnoraStatusOuAutorForjadosERetornaSomentePublicados() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO);
		var base = base();
		for (var estado : StatusMaterial.values()) { material(autor, base, estado); }
		mvc.perform(req(HttpMethod.GET, "", autor).param("disciplinaId", base.disciplina().getId().toString())
				.param("status", "PENDENTE").param("usuarioId", UUID.randomUUID().toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].status").value("PUBLICADO"));
	}

	@Test
	void meusUsaSomenteIdentidadeDoTokenEPermiteFiltrarStatus() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO);
		var outro = usuario(PerfilUsuario.USUARIO);
		var base = base();
		material(autor, base, StatusMaterial.PENDENTE);
		material(autor, base, StatusMaterial.REMOVIDO);
		material(outro, base, StatusMaterial.PENDENTE);
		mvc.perform(req(HttpMethod.GET, "/meus", autor).param("usuarioId", outro.getId().toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(2))
				.andExpect(jsonPath("$.content[0].usuarioId").value(autor.getId().toString()));
		mvc.perform(req(HttpMethod.GET, "/meus", autor).param("status", "REMOVIDO"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].status").value("REMOVIDO"));
	}

	@Test
	void filtrosAcademicosCategoriaETextoCombinamSemExporPendentes() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO);
		var base = base();
		var publicado = material(autor, base, StatusMaterial.PUBLICADO);
		publicado.atualizar("ALGORITMOS e estruturas", "Descricao especial", base.disciplina(), base.categoria());
		material(autor, base, StatusMaterial.PENDENTE);
		material(autor, base(), StatusMaterial.PUBLICADO);
		em.flush();
		mvc.perform(req(HttpMethod.GET, "", autor).param("q", "algoritmos").param("instituicaoId", base.instituicao().getId().toString())
				.param("cursoId", base.curso().getId().toString()).param("disciplinaId", base.disciplina().getId().toString())
				.param("categoriaId", base.categoria().getId().toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].id").value(publicado.getId().toString()));
		mvc.perform(req(HttpMethod.GET, "", autor).param("q", "especial").param("disciplinaId", base.disciplina().getId().toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1));
	}

	@Test
	void pesquisaTrataPorcentagemESublinhadoComoLiterais() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO);
		var base = base();
		var especial = material(autor, base, StatusMaterial.PUBLICADO);
		especial.atualizar("Lista 100%_original", "Descricao", base.disciplina(), base.categoria());
		material(autor, base, StatusMaterial.PUBLICADO);
		em.flush();
		mvc.perform(req(HttpMethod.GET, "", autor).param("q", "%_").param("disciplinaId", base.disciplina().getId().toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].id").value(especial.getId().toString()));
	}

	@Test
	void paginacaoEOrdenacaoSaoEstaveisESemMagnetsNasListas() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO);
		var base = base();
		for (int i = 0; i < 4; i++) { material(autor, base, StatusMaterial.PUBLICADO); }
		mvc.perform(req(HttpMethod.GET, "", autor).param("disciplinaId", base.disciplina().getId().toString()).param("page", "1").param("size", "2"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.page.totalElements").value(4)).andExpect(jsonPath("$.page.number").value(1))
				.andExpect(jsonPath("$.content[0].magnets").doesNotExist());
		mvc.perform(req(HttpMethod.GET, "", autor).param("categoriaId", UUID.randomUUID().toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
	}

	@Test
	void parametrosInvalidosRetornam400() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO);
		for (var parametros : List.of(Map.of("page", "-1"), Map.of("size", "0"), Map.of("size", "101"),
				Map.of("q", "q".repeat(201)), Map.of("cursoId", "invalido"), Map.of("instituicaoId", "invalido"))) {
			var request = req(HttpMethod.GET, "", autor);
			parametros.forEach(request::param);
			mvc.perform(request).andExpect(status().isBadRequest());
		}
		mvc.perform(req(HttpMethod.GET, "/meus", autor).param("status", "NAO_EXISTE")).andExpect(status().isBadRequest());
	}

	@Test
	void consultarNaoIncrementaVisualizacoesNemExpoeDadosPessoais() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO);
		var material = material(autor, base(), StatusMaterial.PUBLICADO);
		mvc.perform(req(HttpMethod.GET, "/" + material.getId(), autor)).andExpect(status().isOk())
				.andExpect(jsonPath("$.magnets[0].infoHash").isNotEmpty()).andExpect(jsonPath("$.visualizacoes").value(0))
				.andExpect(jsonPath("$.usuario").doesNotExist()).andExpect(jsonPath("$.email").doesNotExist()).andExpect(jsonPath("$.senha").doesNotExist());
		assertEquals(0, materiais.findById(material.getId()).orElseThrow().getVisualizacoes());
	}
}
