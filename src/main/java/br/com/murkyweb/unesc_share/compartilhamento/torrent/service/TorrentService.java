package br.com.murkyweb.unesc_share.compartilhamento.torrent.service;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import br.com.murkyweb.unesc_share.compartilhamento.service.InfoHashGuard;
import br.com.murkyweb.unesc_share.compartilhamento.torrent.domain.Torrent;
import br.com.murkyweb.unesc_share.compartilhamento.torrent.dto.TorrentResponse;
import br.com.murkyweb.unesc_share.compartilhamento.torrent.repository.TorrentRepository;
import br.com.murkyweb.unesc_share.compartilhamento.torrent.storage.TorrentStorage;
import br.com.murkyweb.unesc_share.material.domain.StatusMaterial;
import br.com.murkyweb.unesc_share.material.repository.MaterialRepository;
import br.com.murkyweb.unesc_share.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Transactional
public class TorrentService {

	private final TorrentRepository torrents;
	private final MaterialRepository materiais;
	private final UsuarioAutenticado usuario;
	private final TorrentParser parser;
	private final TorrentStorage storage;
	private final InfoHashGuard hashes;

	public TorrentResponse adicionar(UUID materialId, MultipartFile file) {
		var material = materiais.buscarParaAtualizacao(materialId).orElseThrow(this::naoEncontrado);
		if (!material.getUsuario().getId().equals(usuario.id())) { throw naoEncontrado(); }
		if (material.getStatus() != StatusMaterial.PENDENTE && material.getStatus() != StatusMaterial.PUBLICADO) {
			throw conflito("Material bloqueado ou removido nao aceita novos torrents.");
		}
		String name = nome(file);
		byte[] bytes = lerUpload(file);
		String hash = parser.infoHash(bytes);
		hashes.bloquear(List.of(hash));
		hashes.verificarMaterial(hash, materialId);
		if (torrents.existsByInfoHashIgnoreCase(hash)) { throw conflito("Torrent ja cadastrado."); }
		if (torrents.countByMaterialId(materialId) >= 10) { throw conflito("Limite de 10 torrents por material atingido."); }
		String key = storage.salvar(bytes);
		try {
			var torrent = torrents.saveAndFlush(new Torrent(name, key, hash, bytes.length, material));
			material.alterarStatus(StatusMaterial.PENDENTE);
			materiais.flush();
			return TorrentResponse.from(torrent);
		} catch (DataIntegrityViolationException exception) { throw conflito("Nao foi possivel associar o torrent ao material."); }
	}

	@Transactional(readOnly = true)
	public byte[] baixar(UUID materialId, UUID torrentId) {
		var material = materiais.findById(materialId).orElseThrow(this::naoEncontrado);
		if (material.getStatus() != StatusMaterial.PUBLICADO && !material.getUsuario().getId().equals(usuario.id()) && !usuario.podeModerar()) {
			throw naoEncontrado();
		}
		var torrent = torrents.findByIdAndMaterialId(torrentId, materialId).orElseThrow(this::naoEncontrado);
		byte[] bytes = storage.ler(torrent.getCaminhoArquivo(), torrent.getTamanho());
		String hash;
		try { hash = parser.infoHash(bytes); }
		catch (ResponseStatusException exception) { throw indisponivel(); }
		if (!hash.equalsIgnoreCase(torrent.getInfoHash())) { throw indisponivel(); }
		return bytes;
	}

	private String nome(MultipartFile file) {
		String name = file == null ? null : file.getOriginalFilename();
		if (name == null || name.isBlank() || name.length() > 255 || name.startsWith(".") || !name.equals(name.strip())
				|| !name.toLowerCase(Locale.ROOT).endsWith(".torrent")
				|| name.codePoints().anyMatch(c -> Character.isISOControl(c) || c == '/' || c == '\\' || c == ':' || c == '%' || c >= 0xd800 && c <= 0xdfff)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Envie um arquivo com nome .torrent valido.");
		}
		return name;
	}
	private byte[] lerUpload(MultipartFile file) {
		if (file.getSize() > TorrentParser.MAX_BYTES) {
			throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "Arquivo .torrent excede 2 MiB.");
		}
		try (var input = file.getInputStream()) {
			byte[] bytes = input.readNBytes(TorrentParser.MAX_BYTES + 1);
			if (bytes.length > TorrentParser.MAX_BYTES) {
				throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "Arquivo .torrent excede 2 MiB.");
			}
			return bytes;
		} catch (IOException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nao foi possivel ler o upload."); }
	}
	private ResponseStatusException naoEncontrado() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Material ou torrent nao encontrado."); }
	private ResponseStatusException conflito(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
	private ResponseStatusException indisponivel() {
		return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Arquivo .torrent indisponivel no armazenamento.");
	}
}
