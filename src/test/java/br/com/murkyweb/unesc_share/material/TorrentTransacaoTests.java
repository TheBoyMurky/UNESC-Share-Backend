package br.com.murkyweb.unesc_share.material;

import static br.com.murkyweb.unesc_share.compartilhamento.torrent.TorrentFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.transaction.TestTransaction;

import br.com.murkyweb.unesc_share.compartilhamento.torrent.domain.Torrent;
import br.com.murkyweb.unesc_share.compartilhamento.torrent.repository.TorrentRepository;
import br.com.murkyweb.unesc_share.compartilhamento.torrent.storage.TorrentStorage;
import br.com.murkyweb.unesc_share.material.dto.MaterialCadastroTorrentRequest;
import br.com.murkyweb.unesc_share.material.domain.StatusMaterial;
import br.com.murkyweb.unesc_share.usuario.domain.PerfilUsuario;

class TorrentTransacaoTests extends MaterialApiSupport {
	@MockitoSpyBean TorrentRepository torrents;
	@MockitoSpyBean TorrentStorage storage;
	private static final Path ROOT = temporary();
	private static Path temporary() {
		try { return Files.createTempDirectory("unescshare-torrent-rollback-"); }
		catch (IOException exception) { throw new IllegalStateException(exception); }
	}
	@DynamicPropertySource static void storage(DynamicPropertyRegistry registry) {
		registry.add("unesc-share.torrent.storage-directory", ROOT::toString);
	}
	@Test void falhaAposInsercaoReverteRegistroELimpaArquivoSemExporSql() throws Exception {
		long before = torrents.count(); var autor = usuario(PerfilUsuario.USUARIO);
		var material = material(autor, base(), StatusMaterial.PUBLICADO);
		var id = new AtomicReference<UUID>(); var path = new AtomicReference<Path>();
		doAnswer(invocation -> {
			// O repository e uma interface/proxy; executa a insercao JPA antes da falha injetada.
			Torrent inserted = invocation.getArgument(0); em.persist(inserted); em.flush();
			id.set(inserted.getId()); path.set(ROOT.resolve(inserted.getCaminhoArquivo()));
			assertTrue(Files.exists(path.get())); assertTrue(torrents.existsById(id.get()));
			throw new DataIntegrityViolationException("SQL privado que nunca deve aparecer na resposta");
		}).when(torrents).saveAndFlush(any(Torrent.class));
		String route = "/materiais/" + material.getId() + "/torrents";
		var response = mvc.perform(multipart("/api/v1" + route)
				.file(new MockMultipartFile("arquivo", "exemplo.torrent", "application/x-bittorrent", torrent(UUID.randomUUID().toString())))
				.contextPath("/api/v1").servletPath(route).header(HttpHeaders.AUTHORIZATION, "Bearer " + token(autor)))
				.andExpect(status().isConflict()).andReturn().getResponse().getContentAsString();
		assertFalse(response.contains("SQL privado")); assertNotNull(id.get());
		TestTransaction.flagForRollback(); TestTransaction.end(); TestTransaction.start();
		assertFalse(torrents.existsById(id.get())); assertEquals(before, torrents.count()); assertFalse(Files.exists(path.get()));
	}

	@Test void cadastroInicialComFalhaAposInsercaoReverteMaterialTorrentEArquivo() throws Exception {
		long materiaisAntes = materiais.count(), torrentsAntes = torrents.count();
		var autor = usuario(PerfilUsuario.USUARIO); var base = base();
		var materialId = new AtomicReference<UUID>(); var torrentId = new AtomicReference<UUID>(); var path = new AtomicReference<Path>();
		doAnswer(invocation -> {
			Torrent inserted = invocation.getArgument(0); em.persist(inserted); em.flush();
			materialId.set(inserted.getMaterial().getId()); torrentId.set(inserted.getId());
			path.set(ROOT.resolve(inserted.getCaminhoArquivo()));
			assertTrue(materiais.existsById(materialId.get())); assertTrue(torrents.existsById(torrentId.get())); assertTrue(Files.exists(path.get()));
			throw new DataIntegrityViolationException("SQL privado para simular falha apos insercao");
		}).when(torrents).saveAndFlush(any(Torrent.class));
		var metadata = new MockPart("material", bytes(json(new MaterialCadastroTorrentRequest("Titulo", "Descricao", base.disciplina().getId(), base.categoria().getId()))));
		metadata.getHeaders().setContentType(MediaType.APPLICATION_JSON);
		var file = new MockPart("arquivo", "exemplo.torrent", torrent(UUID.randomUUID().toString()));
		var response = mvc.perform(multipart("/api/v1/materiais").part(metadata, file).contextPath("/api/v1").servletPath("/materiais")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token(autor))).andExpect(status().isConflict()).andReturn().getResponse();
		assertFalse(response.getContentAsString().contains("SQL privado")); assertNotNull(materialId.get());
		TestTransaction.flagForRollback(); TestTransaction.end(); TestTransaction.start();
		assertFalse(materiais.existsById(materialId.get())); assertFalse(torrents.existsById(torrentId.get())); assertFalse(Files.exists(path.get()));
		assertEquals(materiaisAntes, materiais.count()); assertEquals(torrentsAntes, torrents.count());
	}

	@Test void storageIndisponivelReverteMaterialInicialSemCriarTorrent() throws Exception {
		long materiaisAntes = materiais.count(), torrentsAntes = torrents.count();
		var autor = usuario(PerfilUsuario.USUARIO); var base = base();
		doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Armazenamento indisponivel."))
				.when(storage).salvar(any(byte[].class));
		var metadata = new MockPart("material", bytes(json(new MaterialCadastroTorrentRequest("Titulo", "Descricao", base.disciplina().getId(), base.categoria().getId()))));
		metadata.getHeaders().setContentType(MediaType.APPLICATION_JSON);
		mvc.perform(multipart("/api/v1/materiais").part(metadata, new MockPart("arquivo", "exemplo.torrent", torrent(UUID.randomUUID().toString())))
				.contextPath("/api/v1").servletPath("/materiais").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(autor)))
				.andExpect(status().isServiceUnavailable());
		TestTransaction.flagForRollback(); TestTransaction.end(); TestTransaction.start();
		assertEquals(materiaisAntes, materiais.count()); assertEquals(torrentsAntes, torrents.count());
	}
}
