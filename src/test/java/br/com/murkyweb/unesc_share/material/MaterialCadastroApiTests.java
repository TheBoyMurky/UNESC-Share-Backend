package br.com.murkyweb.unesc_share.material;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import br.com.murkyweb.unesc_share.material.dto.MaterialCadastroRequest;
import br.com.murkyweb.unesc_share.usuario.domain.PerfilUsuario;

class MaterialCadastroApiTests extends MaterialApiSupport {

	@Test
	void cadastroCrossOriginExpoeLocationDoMaterialParaFrontendPermitido() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO);
		var base = base();
		var result = mvc.perform(req(HttpMethod.POST, "", autor)
				.header(HttpHeaders.ORIGIN, "http://localhost:5173").content(json(cadastro(base, List.of(uri())))))
				.andExpect(status().isCreated())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Location, Content-Disposition"))
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).andReturn();
		String id = mapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
		assertEquals("http://localhost/api/v1/materiais/" + id, result.getResponse().getHeader(HttpHeaders.LOCATION));
	}

	@Test
	void todosOsPerfisPodemCadastrarMaterialEMagnetsIndependentes() throws Exception {
		var base = base();
		for (var perfil : PerfilUsuario.values()) {
			var autor = usuario(perfil);
			String a = uri(), b = uri();
			var resultado = cadastrar(autor, base, List.of(a, b));
			UUID id = UUID.fromString(resultado.get("id").asText());
			assertEquals(autor.getId().toString(), resultado.get("usuarioId").asText());
			assertEquals("PENDENTE", resultado.get("status").asText());
			assertEquals(0, resultado.get("visualizacoes").asLong());
			assertEquals(2, resultado.get("magnets").size());
			assertNotEquals(id.toString(), resultado.get("magnets").get(0).get("id").asText());
			assertEquals(2, magnets.findAllByMaterialIdOrderByDataCadastroAsc(id).size());
			assertNotNull(materiais.findById(id).orElseThrow().getDataCadastro());
		}
	}

	@Test
	void cadastroRetornaLocationENormalizaMetadadosIgnorandoAutorStatusEContadorDoJson() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO);
		var outro = usuario(PerfilUsuario.ADMINISTRADOR);
		var base = base();
		var resultado = mvc.perform(req(HttpMethod.POST, "", autor).content(json(Map.of(
				"titulo", " Titulo normalizado ", "descricao", " Descricao normalizada ",
				"disciplinaId", base.disciplina().getId(), "categoriaId", base.categoria().getId(), "magnetUris", List.of(uri()),
				"usuarioId", outro.getId(), "status", "PUBLICADO", "visualizacoes", 999))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.titulo").value("Titulo normalizado"))
				.andExpect(jsonPath("$.descricao").value("Descricao normalizada"))
				.andExpect(jsonPath("$.usuarioId").value(autor.getId().toString()))
				.andExpect(jsonPath("$.status").value("PENDENTE")).andExpect(jsonPath("$.visualizacoes").value(0))
				.andExpect(jsonPath("$.usuario").doesNotExist()).andExpect(jsonPath("$.senha").doesNotExist()).andReturn();
		String id = mapper.readTree(resultado.getResponse().getContentAsString()).get("id").asText();
		assertEquals("http://localhost/api/v1/materiais/" + id, resultado.getResponse().getHeader("Location"));
	}

	@Test
	void camposInvalidosOuAusenciaDeMagnetsRetornam400SemMaterial() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO);
		var base = base();
		long antes = materiais.count();
		for (var corpo : List.of(
				new MaterialCadastroRequest(null, null, null, null, null),
				new MaterialCadastroRequest(" ", "Descricao", base.disciplina().getId(), base.categoria().getId(), List.of(uri())),
				new MaterialCadastroRequest("T".repeat(201), "Descricao", base.disciplina().getId(), base.categoria().getId(), List.of(uri())),
				new MaterialCadastroRequest("Titulo", "D".repeat(4001), base.disciplina().getId(), base.categoria().getId(), List.of(uri())),
				cadastro(base, List.of()), cadastro(base, null), cadastro(base, List.of(" ")),
				cadastro(base, Collections.singletonList(null)), cadastro(base, Collections.nCopies(11, uri())),
				cadastro(base, List.of("x".repeat(4001))))) {
			mvc.perform(req(HttpMethod.POST, "", autor).content(json(corpo))).andExpect(status().isBadRequest())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		}
		assertEquals(antes, materiais.count());
	}

	@Test
	void magnetInvalidoEntreValidosNaoGravaMaterialNemQualquerMagnet() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO);
		var base = base();
		long materiaisAntes = materiais.count(), magnetsAntes = magnets.count();
		mvc.perform(req(HttpMethod.POST, "", autor).content(json(cadastro(base, List.of(uri(), "https://example.com/arquivo.pdf")))))
				.andExpect(status().isBadRequest());
		assertEquals(materiaisAntes, materiais.count());
		assertEquals(magnetsAntes, magnets.count());
	}

	@Test
	void duplicidadeNaListaOuNoBancoRetorna409SemNovoMaterial() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO);
		var outro = usuario(PerfilUsuario.USUARIO);
		var base = base();
		String magnet = uri();
		long antes = materiais.count();
		mvc.perform(req(HttpMethod.POST, "", autor).content(json(cadastro(base, List.of(magnet, magnet + "&dn=OutroNome")))))
				.andExpect(status().isConflict());
		assertEquals(antes, materiais.count());
		cadastrar(autor, base, List.of(magnet));
		mvc.perform(req(HttpMethod.POST, "", outro).content(json(cadastro(base, List.of(magnet.toUpperCase(java.util.Locale.ROOT).replace("?XT=", "?xt="))))))
				.andExpect(status().isConflict());
		assertEquals(antes + 1, materiais.count());
	}

	@Test
	void disciplinaOuCategoriaInexistenteRetorna404() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO);
		var base = base();
		for (var corpo : List.of(new MaterialCadastroRequest("Titulo", "Descricao", UUID.randomUUID(), base.categoria().getId(), List.of(uri())),
				new MaterialCadastroRequest("Titulo", "Descricao", base.disciplina().getId(), UUID.randomUUID(), List.of(uri())))) {
			mvc.perform(req(HttpMethod.POST, "", autor).content(json(corpo))).andExpect(status().isNotFound());
		}
	}

	@Test
	void identidadeSemUsuarioPersistidoNaoCriaMaterial() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO);
		var base = base();
		em.remove(autor);
		em.flush();
		mvc.perform(req(HttpMethod.POST, "", autor).content(json(cadastro(base, List.of(uri()))))).andExpect(status().isUnauthorized());
	}

	@Test
	void rejeitaJsonMalformadoEConteudoNaoJson() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO);
		mvc.perform(req(HttpMethod.POST, "", autor).content("{invalido")).andExpect(status().isBadRequest());
		mvc.perform(req(HttpMethod.POST, "", autor).contentType("application/pdf").content("conteudo nao permitido"))
				.andExpect(status().isUnsupportedMediaType());
		mvc.perform(req(HttpMethod.POST, "", autor).contentType("multipart/form-data; boundary=teste").content("--teste--"))
				.andExpect(status().isBadRequest());
	}
}
