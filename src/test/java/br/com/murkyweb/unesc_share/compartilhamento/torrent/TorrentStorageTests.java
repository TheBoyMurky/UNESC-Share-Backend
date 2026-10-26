package br.com.murkyweb.unesc_share.compartilhamento.torrent;

import static br.com.murkyweb.unesc_share.compartilhamento.torrent.TorrentFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import br.com.murkyweb.unesc_share.compartilhamento.torrent.storage.TorrentStorage;

class TorrentStorageTests {
	@TempDir Path temp;
	@AfterEach void cleanTransaction() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
			TransactionSynchronizationManager.clearSynchronization();
		}
		TransactionSynchronizationManager.setActualTransactionActive(false);
	}
	private void transaction() {
		TransactionSynchronizationManager.initSynchronization(); TransactionSynchronizationManager.setActualTransactionActive(true);
	}
	@Test void exigeTransacaoMesmoForaDoProxy() {
		assertThrows(IllegalStateException.class, () -> new TorrentStorage(temp.toString()).salvar(torrent("teste")));
	}
	@Test void nomesAleatoriosPreservamBytesECommitPreservaArquivo() throws Exception {
		transaction();
		var storage = new TorrentStorage(temp.toString()); byte[] bytes = torrent("teste");
		String a = storage.salvar(bytes), b = storage.salvar(bytes);
		assertNotEquals(a, b); assertTrue(a.matches("[a-f0-9-]{36}\\.torrent"));
		assertArrayEquals(bytes, storage.ler(a, bytes.length));
		TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
		TransactionSynchronizationManager.clearSynchronization();
		assertTrue(Files.exists(temp.resolve(a)));
	}
	@Test void rollbackRemoveSomenteArquivoCriadoNaTransacao() throws Exception {
		transaction(); var storage = new TorrentStorage(temp.toString());
		Path preserved = Files.write(temp.resolve("preservado.txt"), bytes("preservado"));
		String key = storage.salvar(torrent("teste")); cleanTransaction();
		assertFalse(Files.exists(temp.resolve(key))); assertTrue(Files.exists(preserved));
	}
	@Test void rejeitaCaminhosExternosArquivoAusenteTamanhoIncorretoESymlink() throws Exception {
		var storage = new TorrentStorage(temp.toString());
		for (String key : java.util.List.of("../segredo", "/tmp/segredo", "antigo/arquivo.torrent", UUID.randomUUID() + ".torrent")) {
			assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ResponseStatusException.class, () -> storage.ler(key, 1)).getStatusCode());
		}
		transaction(); String key = storage.salvar(torrent("teste"));
		assertThrows(ResponseStatusException.class, () -> storage.ler(key, 1));
		Path link = temp.resolve(UUID.randomUUID() + ".torrent"); Files.createSymbolicLink(link, temp.resolve(key));
		assertThrows(ResponseStatusException.class, () -> storage.ler(link.getFileName().toString(), torrent("teste").length));
	}
	@Test void rejeitaRaizSymlinkEArquivoNoLugarDoDiretorio() throws Exception {
		transaction(); Path real = Files.createDirectory(temp.resolve("real"));
		Path link = Files.createSymbolicLink(temp.resolve("link"), real);
		assertThrows(ResponseStatusException.class, () -> new TorrentStorage(link.toString()).salvar(torrent("teste")));
		Path file = Files.write(temp.resolve("file"), new byte[]{1});
		assertThrows(ResponseStatusException.class, () -> new TorrentStorage(file.toString()).salvar(torrent("teste")));
	}
}
