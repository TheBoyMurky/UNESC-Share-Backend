package br.com.murkyweb.unesc_share.interacao.comentario.service;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.murkyweb.unesc_share.interacao.comentario.domain.Comentario;
import br.com.murkyweb.unesc_share.interacao.comentario.domain.StatusComentario;
import br.com.murkyweb.unesc_share.interacao.comentario.dto.ComentarioRequest;
import br.com.murkyweb.unesc_share.interacao.comentario.dto.ComentarioResponse;
import br.com.murkyweb.unesc_share.interacao.comentario.repository.ComentarioRepository;
import br.com.murkyweb.unesc_share.material.domain.StatusMaterial;
import br.com.murkyweb.unesc_share.material.repository.MaterialRepository;
import br.com.murkyweb.unesc_share.security.UsuarioAutenticado;
import br.com.murkyweb.unesc_share.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Transactional(readOnly = true)
public class ComentarioService {
	private final ComentarioRepository comentarios;
	private final MaterialRepository materiais;
	private final UsuarioRepository usuarios;
	private final UsuarioAutenticado usuario;

	public Page<ComentarioResponse> listar(UUID materialId, Pageable pageable) {
		if (!materiais.existsByIdAndStatus(materialId, StatusMaterial.PUBLICADO)) { throw naoEncontrado(); }
		// O predicado do Material tambem integra a consulta: nao depender apenas do pre-check.
		return comentarios.findAllByMaterialIdAndMaterialStatusAndStatus(materialId, StatusMaterial.PUBLICADO, StatusComentario.ATIVO, pageable)
				.map(ComentarioResponse::from);
	}

	@Transactional
	public ComentarioResponse cadastrar(UUID materialId, ComentarioRequest request) {
		var autor = usuarios.findById(usuario.id())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario nao disponivel."));
		// Mesmo lock das mutacoes de Material: nao inserir depois de uma mudanca de estado concorrente.
		var material = materiais.buscarParaAtualizacao(materialId)
				.filter(m -> m.getStatus() == StatusMaterial.PUBLICADO).orElseThrow(this::naoEncontrado);
		return ComentarioResponse.from(salvar(new Comentario(request.conteudo(), autor, material)));
	}

	@Transactional
	public ComentarioResponse atualizar(UUID materialId, UUID comentarioId, ComentarioRequest request) {
		var comentario = buscarProprio(materialId, comentarioId);
		if (comentario.getMaterial().getStatus() != StatusMaterial.PUBLICADO) { throw comentarioNaoEncontrado(); }
		if (comentario.getStatus() != StatusComentario.ATIVO) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Somente comentario ativo pode ser editado.");
		}
		comentario.editar(request.conteudo());
		return ComentarioResponse.from(salvar(comentario));
	}

	@Transactional
	public void remover(UUID materialId, UUID comentarioId) {
		var comentario = buscarProprio(materialId, comentarioId);
		// Remocao logica permitida ao autor em qualquer estado, sem devolver texto privado.
		if (comentario.getStatus() != StatusComentario.REMOVIDO) {
			comentario.alterarStatus(StatusComentario.REMOVIDO);
			salvar(comentario);
		}
	}

	private Comentario buscarProprio(UUID materialId, UUID comentarioId) {
		// Ordem fixa para todas as mutacoes: Material primeiro, Comentario depois.
		materiais.buscarParaAtualizacao(materialId).orElseThrow(this::comentarioNaoEncontrado);
		var comentario = comentarios.buscarParaAtualizacao(comentarioId, materialId).orElseThrow(this::comentarioNaoEncontrado);
		if (!comentario.getUsuario().getId().equals(usuario.id())) { throw comentarioNaoEncontrado(); }
		return comentario;
	}

	private Comentario salvar(Comentario comentario) {
		try { return comentarios.saveAndFlush(comentario); }
		catch (DataIntegrityViolationException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Nao foi possivel salvar o comentario. Verifique suas referencias.");
		}
	}

	private ResponseStatusException comentarioNaoEncontrado() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "Comentario nao encontrado.");
	}

	private ResponseStatusException naoEncontrado() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "Material nao encontrado.");
	}
}
