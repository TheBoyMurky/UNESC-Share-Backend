package br.com.murkyweb.unesc_share.compartilhamento.torrent.storage;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import br.com.murkyweb.unesc_share.compartilhamento.torrent.service.TorrentParser;

/** Diretorio privado. Nunca usa o nome enviado ou um caminho contido no metainfo. */
@Service
public class TorrentStorage {

	private static final Logger log = LoggerFactory.getLogger(TorrentStorage.class);
	private final Path root;

	public TorrentStorage(@Value("${unesc-share.torrent.storage-directory}") String directory) {
		if (directory == null || directory.isBlank()) { throw new IllegalArgumentException("Diretorio de torrents obrigatorio."); }
		this.root = Path.of(directory).toAbsolutePath().normalize();
		if (root.getParent() == null) { throw new IllegalArgumentException("Diretorio de torrents deve ser dedicado."); }
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public String salvar(byte[] bytes) {
		if (bytes == null || bytes.length == 0 || bytes.length > TorrentParser.MAX_BYTES) {
			throw new IllegalArgumentException("Metainfo fora dos limites.");
		}
		if (!TransactionSynchronizationManager.isSynchronizationActive()
				|| !TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException("Armazenamento exige transacao ativa.");
		}
		String key = UUID.randomUUID() + ".torrent";
		try {
			prepararDiretorio();
			Path target = resolver(key);
			if (Files.getFileStore(root).supportsFileAttributeView("posix")) {
				Files.createFile(target, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
			} else { Files.createFile(target); }
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override public void afterCompletion(int status) {
					if (status == STATUS_ROLLED_BACK) {
						try { Files.deleteIfExists(target); }
						catch (IOException exception) { log.error("Falha ao limpar metainfo apos rollback; reconciliacao local necessaria."); }
					}
				}
			});
			Files.write(target, bytes, StandardOpenOption.WRITE, NOFOLLOW_LINKS);
			return key;
		} catch (IOException exception) { throw indisponivel(); }
	}

	public byte[] ler(String key, long expectedSize) {
		Path target = resolver(key);
		try {
			verificarDiretorio();
			if (!Files.isRegularFile(target, NOFOLLOW_LINKS) || expectedSize <= 0 || expectedSize > TorrentParser.MAX_BYTES) {
				throw indisponivel();
			}
			try (var input = Files.newInputStream(target, StandardOpenOption.READ, NOFOLLOW_LINKS)) {
				byte[] bytes = input.readNBytes(TorrentParser.MAX_BYTES + 1);
				if (bytes.length != expectedSize) { throw indisponivel(); }
				return bytes;
			}
		} catch (IOException exception) { throw indisponivel(); }
	}

	private Path resolver(String key) {
		if (key == null || !key.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.torrent")) {
			throw indisponivel();
		}
		return root.resolve(key);
	}
	private void prepararDiretorio() throws IOException {
		if (!Files.exists(root, NOFOLLOW_LINKS)) {
			if (root.getFileSystem().supportedFileAttributeViews().contains("posix")) {
				Files.createDirectories(root, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
			} else { Files.createDirectories(root); }
		}
		verificarDiretorio();
	}
	private void verificarDiretorio() throws IOException {
		if (!Files.isDirectory(root, NOFOLLOW_LINKS) || !root.toRealPath().equals(root)) { throw indisponivel(); }
	}
	private ResponseStatusException indisponivel() {
		return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Arquivo .torrent indisponivel no armazenamento.");
	}
}
