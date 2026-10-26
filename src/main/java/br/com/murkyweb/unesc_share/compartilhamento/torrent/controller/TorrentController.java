package br.com.murkyweb.unesc_share.compartilhamento.torrent.controller;

import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import br.com.murkyweb.unesc_share.compartilhamento.torrent.dto.TorrentResponse;
import br.com.murkyweb.unesc_share.compartilhamento.torrent.service.TorrentService;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequiredArgsConstructor
@RequestMapping("/materiais/{materialId}/torrents")
@Tag(name = "Torrents", description = "Cadastro e download de metadados .torrent; nunca recebe o arquivo academico compartilhado.")
@SecurityRequirement(name = "bearerAuth")
public class TorrentController {

	private final TorrentService service;

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<TorrentResponse> adicionar(@PathVariable UUID materialId,
			@RequestParam("arquivo") MultipartFile arquivo, MultipartHttpServletRequest request) {
		var files = request.getMultiFileMap();
		if (files.size() != 1 || files.get("arquivo").size() != 1 || !request.getParameterMap().isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Envie somente um arquivo .torrent no campo arquivo.");
		}
		var response = service.adicionar(materialId, arquivo);
		var location = ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}").buildAndExpand(response.id()).toUri();
		return ResponseEntity.created(location).body(response);
	}

	@GetMapping("/{torrentId}")
	public ResponseEntity<byte[]> baixar(@PathVariable UUID materialId, @PathVariable UUID torrentId) {
		byte[] bytes = service.baixar(materialId, torrentId);
		return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/x-bittorrent"))
				.contentLength(bytes.length).cacheControl(CacheControl.noStore())
				.header("Content-Disposition", ContentDisposition.attachment().filename(torrentId + ".torrent").build().toString())
				.body(bytes);
	}
}
