package br.com.murkyweb.unesc_share.material;

import static br.com.murkyweb.unesc_share.compartilhamento.torrent.TorrentFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import br.com.murkyweb.unesc_share.compartilhamento.magnet.domain.Magnet;
import br.com.murkyweb.unesc_share.compartilhamento.torrent.domain.Torrent;
import br.com.murkyweb.unesc_share.compartilhamento.torrent.repository.TorrentRepository;
import br.com.murkyweb.unesc_share.compartilhamento.torrent.service.TorrentParser;
import br.com.murkyweb.unesc_share.material.domain.Material;
import br.com.murkyweb.unesc_share.material.domain.StatusMaterial;
import br.com.murkyweb.unesc_share.usuario.domain.PerfilUsuario;
import br.com.murkyweb.unesc_share.usuario.domain.Usuario;

class TorrentApiTests extends MaterialApiSupport {
	private static final Path ROOT = temporary();
	@Autowired private TorrentRepository torrents;
	@DynamicPropertySource static void storage(DynamicPropertyRegistry registry) {
		registry.add("unesc-share.torrent.storage-directory", ROOT::toString);
	}
	private static Path temporary() {
		try { return Files.createTempDirectory("unescshare-torrent-api-"); }
		catch (IOException exception) { throw new IllegalStateException(exception); }
	}
	private MockMultipartHttpServletRequestBuilder upload(Material material, Usuario user, String filename, byte[] bytes) {
		String path = "/materiais/" + material.getId() + "/torrents";
		var builder = multipart("/api/v1" + path).file(new MockMultipartFile("arquivo", filename, "application/octet-stream", bytes));
		builder.contextPath("/api/v1").servletPath(path);
		if (user != null) { builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token(user)); }
		return builder;
	}
	private UUID adicionar(Material material, Usuario user, String name) throws Exception {
		var result = mvc.perform(upload(material, user, "exemplo.torrent", torrent(name)))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.caminhoArquivo").doesNotExist()).andReturn();
		UUID id = UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
		assertEquals("http://localhost/api/v1/materiais/" + material.getId() + "/torrents/" + id, result.getResponse().getHeader("Location"));
		return id;
	}

	@Test void todosOsPerfisAdicionamAoProprioMaterialEPublicadoVoltaAPendente() throws Exception {
		var base = base();
		for (var perfil : PerfilUsuario.values()) {
			var autor = usuario(perfil); var material = material(autor, base, StatusMaterial.PUBLICADO);
			String name = UUID.randomUUID().toString(); UUID id = adicionar(material, autor, name);
			var torrent = torrents.findById(id).orElseThrow();
			assertEquals(StatusMaterial.PENDENTE, material.getStatus()); assertEquals(hash(name), torrent.getInfoHash());
			assertEquals(torrent(name).length, torrent.getTamanho()); assertNotEquals(id, material.getId());
			assertArrayEquals(torrent(name), Files.readAllBytes(ROOT.resolve(torrent.getCaminhoArquivo())));
			mvc.perform(req(HttpMethod.GET, "/" + material.getId(), autor))
					.andExpect(jsonPath("$.torrents[0].id").value(id.toString()))
					.andExpect(jsonPath("$.torrents[0].caminhoArquivo").doesNotExist());
		}
	}
	@Test void mesmoHashDeTorrentEMagnetEPermitidoNoMesmoMaterial() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var material = material(autor, base(), StatusMaterial.PENDENTE);
		String name = UUID.randomUUID().toString(), hash = hash(name);
		magnets.saveAndFlush(new Magnet("magnet:?xt=urn:btih:" + hash, hash, material));
		adicionar(material, autor, name); assertEquals(1, torrents.countByMaterialId(material.getId()));
	}
	@Test void hashDeMagnetDeOutroMaterialImpedeUploadInclusiveRemovido() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var base = base();
		var anterior = material(autor, base, StatusMaterial.REMOVIDO); var destino = material(autor, base, StatusMaterial.PUBLICADO);
		String name = UUID.randomUUID().toString(), hash = hash(name);
		magnets.saveAndFlush(new Magnet("magnet:?xt=urn:btih:" + hash, hash, anterior));
		mvc.perform(upload(destino, autor, "arquivo.torrent", torrent(name))).andExpect(status().isConflict());
		assertEquals(0, torrents.countByMaterialId(destino.getId())); assertEquals(StatusMaterial.PUBLICADO, destino.getStatus());
	}
	@Test void hashDeTorrentImpedeNovoMaterialComMagnetMesmoComParametrosDiferentes() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var base = base(); var material = material(autor, base, StatusMaterial.PENDENTE);
		String name = UUID.randomUUID().toString(); adicionar(material, autor, name); long count = materiais.count();
		mvc.perform(req(HttpMethod.POST, "", autor).content(json(cadastro(base, List.of("magnet:?xt=urn:btih:" + hash(name) + "&dn=Outro")))))
				.andExpect(status().isConflict());
		assertEquals(count, materiais.count());
	}
	@Test void duplicarTorrentNoMesmoOuOutroMaterialRetorna409SemAlterarPublicado() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var base = base();
		var a = material(autor, base, StatusMaterial.PENDENTE); var b = material(autor, base, StatusMaterial.PUBLICADO);
		String name = UUID.randomUUID().toString(); adicionar(a, autor, name);
		mvc.perform(upload(a, autor, "outro.torrent", torrent(name))).andExpect(status().isConflict());
		mvc.perform(upload(b, autor, "outro.torrent", torrent(name))).andExpect(status().isConflict());
		assertEquals(1, torrents.countByMaterialId(a.getId())); assertEquals(StatusMaterial.PUBLICADO, b.getStatus());
	}
	@Test void autorBloqueadoOuRemovidoNaoPodeAdicionarEEquipeNaoImpersona() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var base = base();
		for (var state : StatusMaterial.values()) {
			var material = material(autor, base, state);
			if (state == StatusMaterial.BLOQUEADO || state == StatusMaterial.REMOVIDO) {
				mvc.perform(upload(material, autor, "arquivo.torrent", torrent("teste"))).andExpect(status().isConflict());
			}
			for (var perfil : PerfilUsuario.values()) {
				mvc.perform(upload(material, usuario(perfil), "arquivo.torrent", torrent("teste"))).andExpect(status().isNotFound());
			}
		}
	}
	@Test void uploadEDownloadExigemJwt() throws Exception {
		var material = material(usuario(PerfilUsuario.USUARIO), base(), StatusMaterial.PENDENTE);
		mvc.perform(upload(material, null, "arquivo.torrent", torrent("teste"))).andExpect(status().isUnauthorized());
		mvc.perform(req(HttpMethod.GET, "/" + material.getId() + "/torrents/" + UUID.randomUUID(), null)).andExpect(status().isUnauthorized());
	}
	@Test void downloadCrossOriginExpoeContentDispositionSemAlterarArquivoOuAutenticacao() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO);
		var material = material(autor, base(), StatusMaterial.PENDENTE);
		String name = UUID.randomUUID().toString();
		UUID id = adicionar(material, autor, name);
		String path = "/" + material.getId() + "/torrents/" + id;
		mvc.perform(req(HttpMethod.GET, path, autor).header(HttpHeaders.ORIGIN, "http://localhost:5173"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Location, Content-Disposition"))
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS))
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + id + ".torrent\""))
				.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(content().contentType("application/x-bittorrent")).andExpect(content().bytes(torrent(name)));
		mvc.perform(req(HttpMethod.GET, path, null).header(HttpHeaders.ORIGIN, "http://localhost:5173"))
				.andExpect(status().isUnauthorized());
	}
	@Test void downloadRespeitaVisibilidadeTodosOsEstadosEPossuiHeadersSeguros() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var outro = usuario(PerfilUsuario.USUARIO); var base = base();
		var material = material(autor, base, StatusMaterial.PENDENTE); String name = UUID.randomUUID().toString();
		UUID id = adicionar(material, autor, name); String path = "/" + material.getId() + "/torrents/" + id;
		var mod = usuario(PerfilUsuario.MODERADOR); var admin = usuario(PerfilUsuario.ADMINISTRADOR);
		for (var state : StatusMaterial.values()) {
			material.alterarStatus(state); materiais.flush();
			for (var allowed : List.of(autor, mod, admin)) {
				mvc.perform(req(HttpMethod.GET, path, allowed)).andExpect(status().isOk())
						.andExpect(content().bytes(torrent(name))).andExpect(content().contentType("application/x-bittorrent"))
						.andExpect(header().string("Content-Disposition", "attachment; filename=\"" + id + ".torrent\""))
						.andExpect(header().string("Cache-Control", "no-store"))
						.andExpect(header().string("X-Content-Type-Options", "nosniff"));
			}
			mvc.perform(req(HttpMethod.GET, path, outro)).andExpect(state == StatusMaterial.PUBLICADO ? status().isOk() : status().isNotFound());
		}
	}
	@Test void downloadNaoAceitaTorrentDeOutroMaterialNemCaminhoDoBanco() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var base = base(); var a = material(autor, base, StatusMaterial.PUBLICADO);
		var b = material(autor, base, StatusMaterial.PUBLICADO); UUID id = adicionar(a, autor, UUID.randomUUID().toString());
		mvc.perform(req(HttpMethod.GET, "/" + b.getId() + "/torrents/" + id, autor)).andExpect(status().isNotFound());
		var legado = torrents.saveAndFlush(new Torrent("legado.torrent", "../../segredo", UUID.randomUUID().toString(), 1, a));
		mvc.perform(req(HttpMethod.GET, "/" + a.getId() + "/torrents/" + legado.getId(), autor)).andExpect(status().isServiceUnavailable());
	}
	@Test void arquivoAusenteOuAlteradoNaoEDevolvidoNemVazaCaminho() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var material = material(autor, base(), StatusMaterial.PENDENTE);
		String name = UUID.randomUUID().toString(); UUID id = adicionar(material, autor, name);
		Path path = ROOT.resolve(torrents.findById(id).orElseThrow().getCaminhoArquivo());
		byte[] modified = torrent(name); modified[modified.length - 3] = 'x'; Files.write(path, modified);
		var response = mvc.perform(req(HttpMethod.GET, "/" + material.getId() + "/torrents/" + id, autor))
				.andExpect(status().isServiceUnavailable()).andReturn().getResponse().getContentAsString();
		assertFalse(response.contains(ROOT.toString()));
		Files.delete(path);
		mvc.perform(req(HttpMethod.GET, "/" + material.getId() + "/torrents/" + id, autor)).andExpect(status().isServiceUnavailable());
	}
	@Test void rejeitaNomesPerigososArquivosAcademicosDisfarcadosEUploadVazio() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var material = material(autor, base(), StatusMaterial.PUBLICADO);
		for (String filename : List.of("arquivo.pdf", "../arquivo.torrent", "C:\\arquivo.torrent", "arquivo\r\n.torrent",
				".torrent", "arquivo%0a.torrent", "a".repeat(250) + ".torrent")) {
			mvc.perform(upload(material, autor, filename, torrent("teste"))).andExpect(status().isBadRequest());
		}
		for (byte[] bytes : List.of(bytes("%PDF-1.7"), bytes("PK\u0003\u0004"), new byte[0])) {
			mvc.perform(upload(material, autor, "arquivo.torrent", bytes)).andExpect(status().isBadRequest());
		}
		assertEquals(0, torrents.countByMaterialId(material.getId())); assertEquals(StatusMaterial.PUBLICADO, material.getStatus());
	}
	@Test void limiteDeDoisMiBEDeDezTorrents() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var material = material(autor, base(), StatusMaterial.PUBLICADO);
		mvc.perform(upload(material, autor, "arquivo.torrent", new byte[TorrentParser.MAX_BYTES + 1])).andExpect(status().isContentTooLarge());
		for (int i = 0; i < 10; i++) { adicionar(material, autor, UUID.randomUUID().toString()); }
		mvc.perform(upload(material, autor, "arquivo.torrent", torrent(UUID.randomUUID().toString()))).andExpect(status().isConflict());
		assertEquals(10, torrents.countByMaterialId(material.getId()));
	}
	@Test void multipartExigeUmUnicoCampoArquivoSemOutrosUploads() throws Exception {
		var autor = usuario(PerfilUsuario.USUARIO); var material = material(autor, base(), StatusMaterial.PENDENTE);
		mvc.perform(upload(material, autor, "arquivo.torrent", torrent("teste"))
				.file(new MockMultipartFile("arquivo", "outro.pdf", "application/pdf", bytes("PDF")))).andExpect(status().isBadRequest());
		mvc.perform(upload(material, autor, "arquivo.torrent", torrent("teste"))
				.file(new MockMultipartFile("extra", "outro.pdf", "application/pdf", bytes("PDF")))).andExpect(status().isBadRequest());
		mvc.perform(upload(material, autor, "arquivo.torrent", torrent("teste")).param("caminhoArquivo", "../injetado"))
				.andExpect(status().isBadRequest());
		mvc.perform(req(HttpMethod.POST, "/" + material.getId() + "/torrents", autor).content("{}"))
				.andExpect(status().isUnsupportedMediaType());
	}
	@Test void rollbackReverteTorrentEstadoEArquivoFisico() throws Exception {
		long before = torrents.count(); var autor = usuario(PerfilUsuario.USUARIO); var material = material(autor, base(), StatusMaterial.PUBLICADO);
		UUID id = adicionar(material, autor, UUID.randomUUID().toString());
		Path path = ROOT.resolve(torrents.findById(id).orElseThrow().getCaminhoArquivo()); assertTrue(Files.exists(path));
		TestTransaction.flagForRollback(); TestTransaction.end(); TestTransaction.start();
		assertFalse(Files.exists(path)); assertFalse(torrents.existsById(id)); assertEquals(before, torrents.count());
	}
}
