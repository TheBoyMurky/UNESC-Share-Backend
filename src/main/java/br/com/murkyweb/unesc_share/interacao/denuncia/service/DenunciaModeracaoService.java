package br.com.murkyweb.unesc_share.interacao.denuncia.service;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.murkyweb.unesc_share.interacao.denuncia.domain.ModeracaoDenuncia;
import br.com.murkyweb.unesc_share.interacao.denuncia.domain.StatusDenuncia;
import br.com.murkyweb.unesc_share.interacao.denuncia.dto.DenunciaModeracaoRequest;
import br.com.murkyweb.unesc_share.interacao.denuncia.dto.DenunciaModeracaoResponse;
import br.com.murkyweb.unesc_share.interacao.denuncia.dto.DenunciaResponse;
import br.com.murkyweb.unesc_share.interacao.denuncia.repository.DenunciaRepository;
import br.com.murkyweb.unesc_share.interacao.denuncia.repository.ModeracaoDenunciaRepository;
import br.com.murkyweb.unesc_share.material.repository.MaterialRepository;
import br.com.murkyweb.unesc_share.security.UsuarioAutenticado;
import br.com.murkyweb.unesc_share.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MODERADOR', 'ADMINISTRADOR')")
@Transactional(readOnly = true)
public class DenunciaModeracaoService {
	private final DenunciaRepository denuncias;
	private final ModeracaoDenunciaRepository historico;
	private final MaterialRepository materiais;
	private final UsuarioRepository usuarios;
	private final UsuarioAutenticado usuario;

	public Page<DenunciaResponse> listar(StatusDenuncia status, Pageable pageable) {
		return denuncias.findAllByStatus(status, pageable).map(DenunciaResponse::from);
	}

	public DenunciaResponse buscar(UUID id) {
		return denuncias.findById(id).map(DenunciaResponse::from).orElseThrow(this::naoEncontrada);
	}

	public Page<DenunciaModeracaoResponse> historico(UUID id, Pageable pageable) {
		if (!denuncias.existsById(id)) { throw naoEncontrada(); }
		return historico.findAllByDenunciaId(id, pageable).map(DenunciaModeracaoResponse::from);
	}

	@Transactional
	public DenunciaResponse analisar(UUID id, DenunciaModeracaoRequest request) {
		return alterar(id, request, StatusDenuncia.PENDENTE, StatusDenuncia.EM_ANALISE);
	}

	@Transactional
	public DenunciaResponse proceder(UUID id, DenunciaModeracaoRequest request) {
		return alterar(id, request, StatusDenuncia.EM_ANALISE, StatusDenuncia.PROCEDENTE);
	}

	@Transactional
	public DenunciaResponse improceder(UUID id, DenunciaModeracaoRequest request) {
		return alterar(id, request, StatusDenuncia.EM_ANALISE, StatusDenuncia.IMPROCEDENTE);
	}

	private DenunciaResponse alterar(UUID id, DenunciaModeracaoRequest request, StatusDenuncia esperado, StatusDenuncia destino) {
		var responsavel = usuarios.findById(usuario.id())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario nao disponivel."));
		// Projecao escalar: nao carregar/cachear uma Denuncia potencialmente desatualizada antes dos locks.
		var materialId = denuncias.buscarMaterialId(id).orElseThrow(this::naoEncontrada);
		var material = materiais.buscarParaAtualizacao(materialId).orElseThrow(this::naoEncontrada);
		var denuncia = denuncias.buscarParaAtualizacao(id, materialId).orElseThrow(this::naoEncontrada);
		if (responsavel.getId().equals(denuncia.getUsuario().getId()) || responsavel.getId().equals(material.getUsuario().getId())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Conflito de interesse impede moderar esta denuncia.");
		}
		if (denuncia.getStatus() != esperado) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Estado da denuncia nao permite esta acao de moderacao.");
		}
		try {
			denuncia.alterarStatus(destino);
			denuncias.saveAndFlush(denuncia);
			historico.saveAndFlush(new ModeracaoDenuncia(denuncia, responsavel, request.justificativa(), esperado, destino));
			return DenunciaResponse.from(denuncia);
		} catch (DataIntegrityViolationException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Nao foi possivel registrar a moderacao da denuncia.");
		}
	}

	private ResponseStatusException naoEncontrada() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "Denuncia nao encontrada.");
	}
}
