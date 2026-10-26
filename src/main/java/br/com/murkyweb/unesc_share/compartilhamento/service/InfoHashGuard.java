package br.com.murkyweb.unesc_share.compartilhamento.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.murkyweb.unesc_share.compartilhamento.magnet.repository.MagnetRepository;
import br.com.murkyweb.unesc_share.compartilhamento.torrent.repository.TorrentRepository;
import lombok.RequiredArgsConstructor;

/** Uma constraint por tabela nao impede conflito entre Torrent e Magnet. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class InfoHashGuard {

	private final JdbcTemplate jdbc;
	private final MagnetRepository magnets;
	private final TorrentRepository torrents;

	public void bloquear(Collection<String> hashes) {
		// Ordem unica evita deadlock entre lotes. Lock vive na transacao, inclusive em varias instancias.
		for (String hash : hashes.stream().distinct().sorted().toList()) {
			long lock = lockKey(hash);
			jdbc.execute("select pg_advisory_xact_lock(?)", (PreparedStatementCallback<Void>) statement -> {
				statement.setLong(1, lock);
				statement.execute();
				return null;
			});
		}
	}

	public void verificarMaterial(String hash, UUID materialId) {
		boolean conflito = magnets.findByInfoHashIgnoreCase(hash)
				.map(m -> !m.getMaterial().getId().equals(materialId)).orElse(false)
				|| torrents.findByInfoHashIgnoreCase(hash)
				.map(t -> !t.getMaterial().getId().equals(materialId)).orElse(false);
		if (conflito) { throw new ResponseStatusException(HttpStatus.CONFLICT, "Info Hash ja associado a outro material."); }
	}

	private long lockKey(String hash) {
		try {
			return ByteBuffer.wrap(MessageDigest.getInstance("SHA-256")
					.digest(("unescshare:infohash:" + hash).getBytes(StandardCharsets.US_ASCII))).getLong();
		} catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 indisponivel."); }
	}
}
