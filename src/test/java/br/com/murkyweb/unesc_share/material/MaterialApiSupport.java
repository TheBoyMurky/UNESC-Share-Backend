package br.com.murkyweb.unesc_share.material;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import br.com.murkyweb.unesc_share.academico.curso.domain.Curso;
import br.com.murkyweb.unesc_share.academico.disciplina.domain.Disciplina;
import br.com.murkyweb.unesc_share.academico.instituicao.domain.Instituicao;
import br.com.murkyweb.unesc_share.categoria.domain.Categoria;
import br.com.murkyweb.unesc_share.compartilhamento.magnet.domain.Magnet;
import br.com.murkyweb.unesc_share.compartilhamento.magnet.repository.MagnetRepository;
import br.com.murkyweb.unesc_share.material.domain.Material;
import br.com.murkyweb.unesc_share.material.domain.StatusMaterial;
import br.com.murkyweb.unesc_share.material.dto.MaterialCadastroRequest;
import br.com.murkyweb.unesc_share.material.dto.MaterialEdicaoRequest;
import br.com.murkyweb.unesc_share.material.repository.MaterialRepository;
import br.com.murkyweb.unesc_share.security.JwtProperties;
import br.com.murkyweb.unesc_share.usuario.domain.PerfilUsuario;
import br.com.murkyweb.unesc_share.usuario.domain.Usuario;
import jakarta.persistence.EntityManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
abstract class MaterialApiSupport {
	@Autowired protected MockMvc mvc;
	@Autowired protected ObjectMapper mapper;
	@Autowired protected EntityManager em;
	@Autowired protected MaterialRepository materiais;
	@Autowired protected MagnetRepository magnets;
	@Autowired private JwtEncoder encoder;
	@Autowired private JwtProperties properties;

	protected Usuario usuario(PerfilUsuario perfil) {
		var usuario = new Usuario("Usuario Materiais", UUID.randomUUID() + "@example.test", "hash-sintetico-apenas-para-persistencia", perfil);
		em.persist(usuario);
		em.flush();
		return usuario;
	}

	protected Base base() {
		String sufixo = UUID.randomUUID().toString();
		var instituicao = new Instituicao("Instituicao Materiais " + sufixo, "M" + sufixo.substring(0, 20));
		em.persist(instituicao);
		var curso = new Curso("Curso Materiais", null, instituicao);
		em.persist(curso);
		var disciplina = new Disciplina("Disciplina Materiais", null, "MAT-TEST", curso);
		em.persist(disciplina);
		var categoria = new Categoria("Categoria Materiais " + sufixo, null);
		em.persist(categoria);
		em.flush();
		return new Base(instituicao, curso, disciplina, categoria);
	}

	protected Material material(Usuario autor, Base base, StatusMaterial status) {
		var material = new Material("Material " + UUID.randomUUID(), "Descricao para pesquisa.", autor, base.disciplina(), base.categoria());
		material.alterarStatus(status);
		materiais.saveAndFlush(material);
		String uri = uri();
		magnets.saveAndFlush(new Magnet(uri, uri.substring("magnet:?xt=urn:btih:".length()), material));
		return material;
	}

	protected String uri() { return "magnet:?xt=urn:btih:" + UUID.randomUUID().toString().replace("-", "") + "01234567"; }

	protected MaterialCadastroRequest cadastro(Base base, List<String> uris) {
		return new MaterialCadastroRequest("Titulo de teste", "Descricao de teste", base.disciplina().getId(), base.categoria().getId(), uris);
	}

	protected String edicao(Base base, String titulo) {
		return json(new MaterialEdicaoRequest(titulo, "Descricao editada", base.disciplina().getId(), base.categoria().getId()));
	}

	protected JsonNode cadastrar(Usuario autor, Base base, List<String> uris) throws Exception {
		var result = mvc.perform(req(HttpMethod.POST, "", autor).content(json(cadastro(base, uris)))).andExpect(status().isCreated()).andReturn();
		return mapper.readTree(result.getResponse().getContentAsString());
	}

	protected String json(Object objeto) { return mapper.writeValueAsString(objeto); }

	protected MockHttpServletRequestBuilder req(HttpMethod method, String path, Usuario usuario) {
		var request = request(method, "/api/v1/materiais" + path).contextPath("/api/v1").servletPath("/materiais" + path)
				.contentType(MediaType.APPLICATION_JSON);
		if (usuario != null) { request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token(usuario)); }
		return request;
	}

	protected String token(Usuario usuario) {
		var claims = JwtClaimsSet.builder().issuer(properties.issuer()).audience(List.of(properties.audience()))
				.subject(usuario.getId().toString()).issuedAt(Instant.now()).notBefore(Instant.now().minusSeconds(1))
				.expiresAt(Instant.now().plusSeconds(900)).claim("roles", List.of(usuario.getPerfil().name())).build();
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	protected record Base(Instituicao instituicao, Curso curso, Disciplina disciplina, Categoria categoria) { }
}
