package br.com.murkyweb.unesc_share.material.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.murkyweb.unesc_share.compartilhamento.magnet.dto.MagnetResponse;
import br.com.murkyweb.unesc_share.compartilhamento.magnet.repository.MagnetRepository;
import br.com.murkyweb.unesc_share.compartilhamento.torrent.repository.TorrentRepository;
import br.com.murkyweb.unesc_share.compartilhamento.torrent.dto.TorrentResponse;
import br.com.murkyweb.unesc_share.material.domain.StatusMaterial;
import br.com.murkyweb.unesc_share.material.dto.MaterialDetalheResponse;
import br.com.murkyweb.unesc_share.material.dto.MaterialFiltro;
import br.com.murkyweb.unesc_share.material.dto.MaterialResumoResponse;
import br.com.murkyweb.unesc_share.material.repository.MaterialRepository;
import br.com.murkyweb.unesc_share.material.repository.MaterialSpecifications;
import br.com.murkyweb.unesc_share.security.UsuarioAutenticado;

@Service
@PreAuthorize("isAuthenticated()")
@Transactional(readOnly = true)
public class MaterialQueryService {

	private final MaterialRepository repository;
	private final MagnetRepository magnets;
	private final TorrentRepository torrents;
	private final UsuarioAutenticado usuario;

	public MaterialQueryService(MaterialRepository repository, MagnetRepository magnets, TorrentRepository torrents, UsuarioAutenticado usuario) {
		this.repository = repository;
		this.magnets = magnets;
		this.torrents = torrents;
		this.usuario = usuario;
	}

	public Page<MaterialResumoResponse> listarPublicados(MaterialFiltro filtro, Pageable pageable) {
		return repository.findAll(MaterialSpecifications.filtrar(StatusMaterial.PUBLICADO, null, filtro), pageable)
				.map(MaterialResumoResponse::from);
	}

	public Page<MaterialResumoResponse> listarMeus(StatusMaterial status, MaterialFiltro filtro, Pageable pageable) {
		return repository.findAll(MaterialSpecifications.filtrar(status, usuario.id(), filtro), pageable)
				.map(MaterialResumoResponse::from);
	}

	@PreAuthorize("hasAnyRole('MODERADOR', 'ADMINISTRADOR')")
	public Page<MaterialResumoResponse> listarModeracao(StatusMaterial status, MaterialFiltro filtro, Pageable pageable) {
		return repository.findAll(MaterialSpecifications.filtrar(status == null ? StatusMaterial.PENDENTE : status, null, filtro), pageable)
				.map(MaterialResumoResponse::from);
	}

	public MaterialDetalheResponse consultar(UUID id) {
		var material = repository.findById(id).orElseThrow(this::naoEncontrado);
		if (material.getStatus() != StatusMaterial.PUBLICADO && !material.getUsuario().getId().equals(usuario.id()) && !usuario.podeModerar()) {
			throw naoEncontrado();
		}
		return MaterialDetalheResponse.from(material,
				magnets.findAllByMaterialIdOrderByDataCadastroAsc(id).stream().map(MagnetResponse::from).toList(),
				torrents.findAllByMaterialIdOrderByDataCadastroAsc(id).stream().map(TorrentResponse::from).toList());
	}

	private ResponseStatusException naoEncontrado() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "Material nao encontrado.");
	}
}
