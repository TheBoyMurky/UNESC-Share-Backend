package br.com.murkyweb.unesc_share.interacao.comentario.service;

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

import br.com.murkyweb.unesc_share.interacao.comentario.domain.ModeracaoComentario;
import br.com.murkyweb.unesc_share.interacao.comentario.domain.StatusComentario;
import br.com.murkyweb.unesc_share.interacao.comentario.dto.ComentarioModeracaoRequest;
import br.com.murkyweb.unesc_share.interacao.comentario.dto.ComentarioModeracaoResponse;
import br.com.murkyweb.unesc_share.interacao.comentario.dto.ComentarioResponse;
import br.com.murkyweb.unesc_share.interacao.comentario.repository.ComentarioRepository;
import br.com.murkyweb.unesc_share.interacao.comentario.repository.ModeracaoComentarioRepository;
import br.com.murkyweb.unesc_share.material.domain.StatusMaterial;
import br.com.murkyweb.unesc_share.material.repository.MaterialRepository;
import br.com.murkyweb.unesc_share.security.UsuarioAutenticado;
import br.com.murkyweb.unesc_share.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MODERADOR', 'ADMINISTRADOR')")
@Transactional(readOnly = true)
public class ComentarioModeracaoService {
	private final ComentarioRepository comentarios;
	private final ModeracaoComentarioRepository historico;
	private final MaterialRepository materiais;
	private final UsuarioRepository usuarios;
	private final UsuarioAutenticado usuario;

	public Page<ComentarioResponse> listar(UUID materialId, Pageable pageable) {
		if (!materiais.existsById(materialId)) { throw naoEncontrado(); }
		// Predicado fixo na consulta E na contagem, sem aceitar REMOVIDO por parametro.
		return comentarios.findAllByMaterialIdAndStatusIn(materialId,
				List.of(StatusComentario.ATIVO, StatusComentario.OCULTO), pageable).map(ComentarioResponse::from);
	}

	public Page<ComentarioModeracaoResponse> historico(UUID materialId, UUID comentarioId, Pageable pageable) {
		if (!comentarios.existsByIdAndMaterialId(comentarioId, materialId)) { throw naoEncontrado(); }
		// Tambem acessivel apos remocao pelo autor, mas nunca copia/devolve o texto do comentario.
		return historico.findAllByComentarioIdAndComentarioMaterialId(comentarioId, materialId, pageable)
				.map(ComentarioModeracaoResponse::from);
	}

	@Transactional
	public ComentarioResponse ocultar(UUID materialId, UUID comentarioId, ComentarioModeracaoRequest request) {
		return alterar(materialId, comentarioId, request, StatusComentario.ATIVO, StatusComentario.OCULTO);
	}

	@Transactional
	public ComentarioResponse reexibir(UUID materialId, UUID comentarioId, ComentarioModeracaoRequest request) {
		return alterar(materialId, comentarioId, request, StatusComentario.OCULTO, StatusComentario.ATIVO);
	}

	private ComentarioResponse alterar(UUID materialId, UUID comentarioId, ComentarioModeracaoRequest request,
			StatusComentario esperado, StatusComentario destino) {
		var responsavel = usuarios.findById(usuario.id())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario nao disponivel."));
		// Mesma ordem dos fluxos de autor e Material para coordenar remocao/bloqueio/edicao.
		var material = materiais.buscarParaAtualizacao(materialId).orElseThrow(this::naoEncontrado);
		var comentario = comentarios.buscarParaAtualizacao(comentarioId, materialId).orElseThrow(this::naoEncontrado);
		if (comentario.getStatus() != esperado) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Estado do comentario nao permite esta acao de moderacao.");
		}
		if (destino == StatusComentario.ATIVO && material.getStatus() != StatusMaterial.PUBLICADO) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Somente material publicado permite reexibir comentarios.");
		}
		try {
			comentario.alterarStatus(destino);
			comentarios.saveAndFlush(comentario);
			historico.saveAndFlush(new ModeracaoComentario(comentario, responsavel, request.motivo(), esperado, destino));
			return ComentarioResponse.from(comentario);
		} catch (DataIntegrityViolationException exception) {
			// A excecao sai da transacao e reverte status E historico, sem propagar SQL ou motivo.
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Nao foi possivel registrar a moderacao do comentario.");
		}
	}

	private ResponseStatusException naoEncontrado() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "Comentario ou material nao encontrado.");
	}
}
