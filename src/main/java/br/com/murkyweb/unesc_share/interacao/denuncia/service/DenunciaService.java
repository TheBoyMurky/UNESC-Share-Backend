package br.com.murkyweb.unesc_share.interacao.denuncia.service;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.murkyweb.unesc_share.interacao.denuncia.domain.Denuncia;
import br.com.murkyweb.unesc_share.interacao.denuncia.domain.StatusDenuncia;
import br.com.murkyweb.unesc_share.interacao.denuncia.dto.DenunciaRequest;
import br.com.murkyweb.unesc_share.interacao.denuncia.dto.DenunciaResponse;
import br.com.murkyweb.unesc_share.interacao.denuncia.repository.DenunciaRepository;
import br.com.murkyweb.unesc_share.material.domain.StatusMaterial;
import br.com.murkyweb.unesc_share.material.repository.MaterialRepository;
import br.com.murkyweb.unesc_share.security.UsuarioAutenticado;
import br.com.murkyweb.unesc_share.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Transactional(readOnly = true)
public class DenunciaService {
	private static final List<StatusDenuncia> ABERTAS = List.of(StatusDenuncia.PENDENTE, StatusDenuncia.EM_ANALISE);
	private final DenunciaRepository denuncias;
	private final MaterialRepository materiais;
	private final UsuarioRepository usuarios;
	private final UsuarioAutenticado usuario;

	@Transactional
	public DenunciaResponse cadastrar(UUID materialId, DenunciaRequest request) {
		var denunciante = usuarios.findById(usuario.id())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario nao disponivel."));
		// Serializa mesmo quando ainda nao existe Denuncia, coordenando tambem mutacoes do Material.
		var material = materiais.buscarParaAtualizacao(materialId)
				.filter(m -> m.getStatus() == StatusMaterial.PUBLICADO)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Material nao encontrado."));
		if (denuncias.existsByUsuarioIdAndMaterialIdAndStatusIn(denunciante.getId(), materialId, ABERTAS)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Voce ja possui uma denuncia aberta para este material.");
		}
		try {
			return DenunciaResponse.from(denuncias.saveAndFlush(new Denuncia(request.motivo(), request.descricao(), denunciante, material)));
		} catch (DataIntegrityViolationException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Nao foi possivel salvar a denuncia. Verifique suas referencias.");
		}
	}

	public Page<DenunciaResponse> minhas(Pageable pageable) {
		// Autor e predicado da consulta E da contagem; sem filtro de estado do Material.
		return denuncias.findAllByUsuarioId(usuario.id(), pageable).map(DenunciaResponse::from);
	}

	public DenunciaResponse buscar(UUID denunciaId) {
		return denuncias.findByIdAndUsuarioId(denunciaId, usuario.id()).map(DenunciaResponse::from)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Denuncia nao encontrada."));
	}
}
