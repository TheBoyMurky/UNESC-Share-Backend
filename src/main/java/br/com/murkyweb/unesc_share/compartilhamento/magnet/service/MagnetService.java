package br.com.murkyweb.unesc_share.compartilhamento.magnet.service;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.murkyweb.unesc_share.compartilhamento.magnet.domain.Magnet;
import br.com.murkyweb.unesc_share.compartilhamento.magnet.repository.MagnetRepository;
import br.com.murkyweb.unesc_share.compartilhamento.magnet.service.MagnetParser.MagnetValidado;
import br.com.murkyweb.unesc_share.material.domain.Material;
import br.com.murkyweb.unesc_share.compartilhamento.service.InfoHashGuard;

/** Validacao/associacao internas aos casos de uso de Material; transacao e autoria no orquestrador. */
@Service
public class MagnetService {

	private final MagnetParser parser;
	private final MagnetRepository repository;
	private final InfoHashGuard hashes;

	public MagnetService(MagnetParser parser, MagnetRepository repository, InfoHashGuard hashes) {
		this.parser = parser;
		this.repository = repository;
		this.hashes = hashes;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public List<MagnetValidado> preparar(List<String> uris) {
		return preparar(uris, null);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public List<MagnetValidado> preparar(List<String> uris, UUID materialId) {
		if (uris == null || uris.isEmpty() || uris.size() > 10) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe de 1 a 10 Magnet URIs.");
		}
		var validados = uris.stream().map(parser::parse).toList();
		hashes.bloquear(validados.stream().map(MagnetValidado::infoHash).toList());
		var vistos = new HashSet<String>();
		for (var magnet : validados) {
			if (!vistos.add(magnet.infoHash()) || repository.existsByInfoHashIgnoreCase(magnet.infoHash())) { throw duplicado(); }
			// Cadastro novo passa null; adicao permite Torrent com o hash apenas neste mesmo Material.
			hashes.verificarMaterial(magnet.infoHash(), materialId);
		}
		return validados;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void associar(Material material, List<MagnetValidado> magnets) {
		try {
			repository.saveAllAndFlush(magnets.stream()
					.map(magnet -> new Magnet(magnet.magnetUri(), magnet.infoHash(), material)).toList());
		} catch (DataIntegrityViolationException exception) {
			throw duplicado();
		}
	}

	private ResponseStatusException duplicado() {
		return new ResponseStatusException(HttpStatus.CONFLICT, "Info Hash de Magnet repetido ou ja cadastrado.");
	}
}
