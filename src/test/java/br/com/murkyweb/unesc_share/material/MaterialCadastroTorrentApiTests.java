package br.com.murkyweb.unesc_share.material;

import static br.com.murkyweb.unesc_share.compartilhamento.torrent.TorrentFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockPart;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import br.com.murkyweb.unesc_share.compartilhamento.magnet.domain.Magnet;
import br.com.murkyweb.unesc_share.compartilhamento.torrent.repository.TorrentRepository;
import br.com.murkyweb.unesc_share.compartilhamento.torrent.service.TorrentParser;
import br.com.murkyweb.unesc_share.material.dto.MaterialCadastroTorrentRequest;
import br.com.murkyweb.unesc_share.usuario.domain.PerfilUsuario;
import br.com.murkyweb.unesc_share.usuario.domain.Usuario;

class MaterialCadastroTorrentApiTests extends MaterialApiSupport {
	private static final Path ROOT = temporary();
	@Autowired private TorrentRepository torrents;
	@DynamicPropertySource static void storage(DynamicPropertyRegistry registry) {
		registry.add("unesc-share.torrent.storage-directory", ROOT::toString);
	}
	private static Path temporary() {
		try { return Files.createTempDirectory("unescshare-cadastro-torrent-"); }
		catch (IOException exception) { throw new IllegalStateException(exception); }
	}
	private MockPart metadata(Object value) {
		var part = new MockPart("material", bytes(json(value))); part.getHeaders().setContentType(MediaType.APPLICATION_JSON); return part;
	}
	private MockPart file(String name, byte[] bytes) {
		var part = new MockPart("arquivo", name, bytes); part.getHeaders().setContentType(MediaType.APPLICATION_OCTET_STREAM); return part;
	}
	private MockMultipartHttpServletRequestBuilder upload(Usuario autor, MockPart... parts) {
		var request = multipart("/api/v1/materiais").part(parts);
		request.contextPath("/api/v1").servletPath("/materiais");
		if (autor != null) { request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token(autor)); }
		return request;
	}
	private MaterialCadastroTorrentRequest dados(Base base) {
		return new MaterialCadastroTorrentRequest(" Titulo com torrent ", " Descricao de teste ", base.disciplina().getId(), base.categoria().getId());
	}
	private void rollback() { TestTransaction.flagForRollback(); TestTransaction.end(); TestTransaction.start(); }

	@Test void todosOsPerfisCriamMaterialComTorrentSemMagnetEDownloadFunciona() throws Exception {
		var base = base();
		for (var perfil : PerfilUsuario.values()) {
			var autor = usuario(perfil); String name = UUID.randomUUID().toString();
			var result = mvc.perform(upload(autor, metadata(dados(base)), file("exemplo.torrent", torrent(name))))
					.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDENTE"))
					.andExpect(jsonPath("$.visualizacoes").value(0)).andExpect(jsonPath("$.magnets").isEmpty())
					.andExpect(jsonPath("$.torrents.length()").value(1)).andExpect(jsonPath("$.titulo").value("Titulo com torrent"))
					.andExpect(jsonPath("$.descricao").value("Descricao de teste")).andReturn();
			var body = mapper.readTree(result.getResponse().getContentAsString()); UUID id = UUID.fromString(body.get("id").asText());
			assertEquals(autor.getId().toString(), body.get("usuarioId").asText());
			assertEquals("http://localhost/api/v1/materiais/" + id, result.getResponse().getHeader("Location"));
			assertTrue(magnets.findAllByMaterialIdOrderByDataCadastroAsc(id).isEmpty());
			var torrent = torrents.findAllByMaterialIdOrderByDataCadastroAsc(id).getFirst();
			assertEquals(hash(name), torrent.getInfoHash()); assertNotEquals(id, torrent.getId());
			assertArrayEquals(torrent(name), Files.readAllBytes(ROOT.resolve(torrent.getCaminhoArquivo())));
			mvc.perform(req(HttpMethod.GET, "/" + id + "/torrents/" + torrent.getId(), autor))
					.andExpect(status().isOk()).andExpect(content().bytes(torrent(name)));
			assertFalse(result.getResponse().getContentAsString().contains("caminhoArquivo"));
		}
	}
	@Test void autorEstadoEHashNaoPodemSerImpostosPeloJsonENaoCriaMagnetsExtras() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var outro = usuario(PerfilUsuario.ADMINISTRADOR); var base = base();
		String name = UUID.randomUUID().toString();
		mvc.perform(upload(autor, metadata(Map.of("titulo", "Titulo", "descricao", "Descricao", "disciplinaId", base.disciplina().getId(),
				"categoriaId", base.categoria().getId(), "usuarioId", outro.getId(), "status", "PUBLICADO", "visualizacoes", 100,
				"infoHash", "hash-forjado", "magnetUris", List.of(uri()))), file("exemplo.torrent", torrent(name))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.usuarioId").value(autor.getId().toString()))
				.andExpect(jsonPath("$.status").value("PENDENTE")).andExpect(jsonPath("$.visualizacoes").value(0))
				.andExpect(jsonPath("$.torrents[0].infoHash").value(hash(name))).andExpect(jsonPath("$.magnets").isEmpty());
	}
	@Test void cadastroComTorrentPermitePublicacaoPelaEquipeSemMagnet() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var base = base(); var mod = usuario(PerfilUsuario.MODERADOR);
		var response = mvc.perform(upload(autor, metadata(dados(base)), file("exemplo.torrent", torrent(UUID.randomUUID().toString()))))
				.andExpect(status().isCreated()).andReturn().getResponse();
		String id = mapper.readTree(response.getContentAsString()).get("id").asText();
		var outro = usuario(PerfilUsuario.USUARIO);
		mvc.perform(req(HttpMethod.GET, "/" + id, outro)).andExpect(status().isNotFound());
		mvc.perform(req(HttpMethod.POST, "/" + id + "/publicacao", mod)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLICADO"));
		mvc.perform(req(HttpMethod.GET, "/" + id, outro)).andExpect(status().isOk()).andExpect(jsonPath("$.magnets").isEmpty());
	}
	@Test void suportaParteJsonComoBlobComFilenameSemTratarComoMetainfo() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var base = base();
		var blob = new MockPart("material", "blob", bytes(json(dados(base)))); blob.getHeaders().setContentType(MediaType.APPLICATION_JSON);
		mvc.perform(upload(autor, blob, file("exemplo.torrent", torrent(UUID.randomUUID().toString())))).andExpect(status().isCreated());
	}
	@Test void exigeJwtESemIdentidadePersistidaRetorna401SemCriarMaterial() throws Exception {
		long before = materiais.count(); var autor = usuario(PerfilUsuario.USUARIO); var base = base();
		mvc.perform(upload(null, metadata(dados(base)), file("exemplo.torrent", torrent("teste")))).andExpect(status().isUnauthorized());
		em.remove(autor); em.flush();
		mvc.perform(upload(autor, metadata(dados(base)), file("exemplo.torrent", torrent("teste")))).andExpect(status().isUnauthorized());
		assertEquals(before, materiais.count());
	}
	@Test void validaMetadadosJsonNativamenteAntesDaPersistencia() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var base = base(); long before = materiais.count();
		for (var value : List.of(new MaterialCadastroTorrentRequest(null, null, null, null),
				new MaterialCadastroTorrentRequest(" ", "Descricao", base.disciplina().getId(), base.categoria().getId()),
				new MaterialCadastroTorrentRequest("T".repeat(201), "Descricao", base.disciplina().getId(), base.categoria().getId()),
				new MaterialCadastroTorrentRequest("Titulo", "D".repeat(4001), base.disciplina().getId(), base.categoria().getId()))) {
			mvc.perform(upload(autor, metadata(value), file("exemplo.torrent", torrent("teste"))))
					.andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		}
		assertEquals(before, materiais.count());
	}
	@Test void partesAusentesDuplicadasOuExtrasSaoRejeitadasSemMaterial() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var base = base(); long before = materiais.count();
		for (MockPart[] parts : List.of(new MockPart[]{metadata(dados(base))}, new MockPart[]{file("exemplo.torrent", torrent("teste"))},
				new MockPart[]{metadata(dados(base)), metadata(dados(base)), file("exemplo.torrent", torrent("teste"))},
				new MockPart[]{metadata(dados(base)), file("exemplo.torrent", torrent("teste")), file("outro.torrent", torrent("outro"))},
				new MockPart[]{metadata(dados(base)), file("exemplo.torrent", torrent("teste")), new MockPart("extra", bytes("extra"))},
				new MockPart[]{metadata(dados(base)), file("exemplo.torrent", torrent("teste")), new MockPart("pdf", "arquivo.pdf", bytes("PDF"))})) {
			mvc.perform(upload(autor, parts)).andExpect(status().isBadRequest());
		}
		assertEquals(before, materiais.count());
	}
	@Test void jsonMalformadoUuidInvalidoOuContentTypeErradoDaParteNaoCriaMaterial() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); long before = materiais.count();
		var malformed = new MockPart("material", bytes("{invalido")); malformed.getHeaders().setContentType(MediaType.APPLICATION_JSON);
		mvc.perform(upload(autor, malformed, file("exemplo.torrent", torrent("teste")))).andExpect(status().isBadRequest());
		var idInvalido = metadata(Map.of("titulo", "Titulo", "descricao", "Descricao", "disciplinaId", "uuid-invalido", "categoriaId", UUID.randomUUID()));
		mvc.perform(upload(autor, idInvalido, file("exemplo.torrent", torrent("teste")))).andExpect(status().isBadRequest());
		var text = new MockPart("material", bytes("{}")); text.getHeaders().setContentType(MediaType.TEXT_PLAIN);
		mvc.perform(upload(autor, text, file("exemplo.torrent", torrent("teste")))).andExpect(status().isUnsupportedMediaType());
		assertEquals(before, materiais.count());
	}
	@Test void referenciasInexistentesNaoCriamMaterial() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var base = base(); long before = materiais.count();
		for (var value : List.of(new MaterialCadastroTorrentRequest("Titulo", "Descricao", UUID.randomUUID(), base.categoria().getId()),
				new MaterialCadastroTorrentRequest("Titulo", "Descricao", base.disciplina().getId(), UUID.randomUUID()))) {
			mvc.perform(upload(autor, metadata(value), file("exemplo.torrent", torrent("teste")))).andExpect(status().isNotFound());
		}
		assertEquals(before, materiais.count());
	}
	@ParameterizedTest @MethodSource("arquivosInvalidos")
	void torrentInvalidoOuExcessivoReverteMaterialSemOrfao(String filename, byte[] bytes, int expected) throws Exception {
		long before = materiais.count(), torrentsBefore = torrents.count(), magnetsBefore = magnets.count();
		var autor = usuario(PerfilUsuario.USUARIO); var base = base();
		mvc.perform(upload(autor, metadata(dados(base)), file(filename, bytes))).andExpect(status().is(expected));
		rollback(); assertEquals(before, materiais.count()); assertEquals(torrentsBefore, torrents.count()); assertEquals(magnetsBefore, magnets.count());
		try (var files = Files.list(ROOT)) { assertEquals(0, files.count()); }
	}
	static Stream<Object[]> arquivosInvalidos() {
		return Stream.of(new Object[]{"arquivo.pdf", torrent("teste"), 400}, new Object[]{"../arquivo.torrent", torrent("teste"), 400},
				new Object[]{"arquivo.torrent", bytes("%PDF-1.7"), 400}, new Object[]{"arquivo.torrent", new byte[0], 400},
				new Object[]{"arquivo.torrent", new byte[TorrentParser.MAX_BYTES + 1], 413});
	}
	@Test void hashDeOutroMaterialEmMagnetRetorna409EReverteNovoMaterial() throws Exception {
		long before = materiais.count(); var autor = usuario(PerfilUsuario.USUARIO); var base = base();
		var anterior = material(autor, base, br.com.murkyweb.unesc_share.material.domain.StatusMaterial.REMOVIDO);
		String name = UUID.randomUUID().toString(), hash = hash(name); magnets.saveAndFlush(new Magnet("magnet:?xt=urn:btih:" + hash, hash, anterior));
		mvc.perform(upload(autor, metadata(dados(base)), file("exemplo.torrent", torrent(name)))).andExpect(status().isConflict());
		rollback(); assertEquals(before, materiais.count());
	}
	@Test void hashDeTorrentJaExistenteReverteSegundoCadastroSemReutilizarArquivo() throws Exception {
		long before = materiais.count(); var autor = usuario(PerfilUsuario.USUARIO); var base = base(); String name = UUID.randomUUID().toString();
		mvc.perform(upload(autor, metadata(dados(base)), file("exemplo.torrent", torrent(name)))).andExpect(status().isCreated());
		mvc.perform(upload(autor, metadata(dados(base)), file("outro.torrent", torrent(name)))).andExpect(status().isConflict());
		rollback(); assertEquals(before, materiais.count()); try (var files = Files.list(ROOT)) { assertEquals(0, files.count()); }
	}
	@Test void endpointJsonComMagnetContinuaCompativelIncluindoTipoVendorJson() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var base = base();
		mvc.perform(req(HttpMethod.POST, "", autor).contentType("application/vnd.unescshare+json")
				.content(json(cadastro(base, List.of(uri()))))).andExpect(status().isCreated())
				.andExpect(jsonPath("$.magnets.length()").value(1)).andExpect(jsonPath("$.torrents").isEmpty());
	}
}
