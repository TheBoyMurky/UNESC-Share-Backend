package br.com.murkyweb.unesc_share.interacao.avaliacao.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.murkyweb.unesc_share.interacao.avaliacao.domain.Avaliacao;
import br.com.murkyweb.unesc_share.interacao.avaliacao.dto.AvaliacaoRequest;
import br.com.murkyweb.unesc_share.interacao.avaliacao.dto.AvaliacaoResponse;
import br.com.murkyweb.unesc_share.interacao.avaliacao.dto.AvaliacaoResumoResponse;
import br.com.murkyweb.unesc_share.interacao.avaliacao.repository.AvaliacaoRepository;
import br.com.murkyweb.unesc_share.material.domain.StatusMaterial;
import br.com.murkyweb.unesc_share.material.repository.MaterialRepository;
import br.com.murkyweb.unesc_share.security.UsuarioAutenticado;
import br.com.murkyweb.unesc_share.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Transactional(readOnly = true)
public class AvaliacaoService {
	private final AvaliacaoRepository avaliacoes;
	private final MaterialRepository materiais;
	private final UsuarioRepository usuarios;
	private final UsuarioAutenticado usuario;

	@Transactional
	public AvaliacaoResponse cadastrar(UUID materialId, AvaliacaoRequest request) {
		var autor = usuarios.findById(usuario.id())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario nao disponivel."));
		// Serializa com mutacoes do Material e outros cadastros, inclusive antes de existir a Avaliacao.
		var material = materiais.buscarParaAtualizacao(materialId)
				.filter(m -> m.getStatus() == StatusMaterial.PUBLICADO).orElseThrow(this::materialNaoEncontrado);
		if (material.getUsuario().getId().equals(autor.getId())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nao e permitido avaliar o proprio material.");
		}
		if (avaliacoes.existsByUsuarioIdAndMaterialId(autor.getId(), materialId)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Voce ja avaliou este material.");
		}
		// Somente normaliza a escala: nunca arredonda notas. Validacao MVC antecede esta etapa.
		var nota = request.nota().setScale(2, RoundingMode.UNNECESSARY);
		return AvaliacaoResponse.from(salvar(new Avaliacao(nota, autor, material)));
	}

	@Transactional
	public AvaliacaoResponse atualizar(UUID materialId, AvaliacaoRequest request) {
		var material = materiais.buscarParaAtualizacao(materialId)
				.filter(m -> m.getStatus() == StatusMaterial.PUBLICADO).orElseThrow(this::materialNaoEncontrado);
		UUID autorId = usuario.id();
		if (material.getUsuario().getId().equals(autorId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nao e permitido avaliar o proprio material.");
		}
		// Ordem fixa Material -> Avaliacao; identidade nunca vem do corpo ou de parametros.
		var avaliacao = avaliacoes.buscarPropriaParaAtualizacao(autorId, materialId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Avaliacao nao encontrada."));
		avaliacao.atualizarNota(request.nota().setScale(2, RoundingMode.UNNECESSARY));
		return AvaliacaoResponse.from(salvar(avaliacao));
	}

	@Transactional
	public void remover(UUID materialId) {
		// O autor pode retirar a nota em qualquer estado, sem abrir uma consulta privada.
		materiais.buscarParaAtualizacao(materialId).orElseThrow(this::materialNaoEncontrado);
		var propria = avaliacoes.buscarPropriaParaAtualizacao(usuario.id(), materialId);
		if (propria.isEmpty()) { return; }
		try {
			avaliacoes.delete(propria.orElseThrow());
			avaliacoes.flush();
		} catch (DataIntegrityViolationException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Nao foi possivel remover a avaliacao.");
		}
	}

	public AvaliacaoResponse minha(UUID materialId) {
		verificarPublicado(materialId);
		// O predicado de publicacao tambem esta na consulta, nao somente no pre-check.
		return avaliacoes.findByUsuarioIdAndMaterialIdAndMaterialStatus(usuario.id(), materialId, StatusMaterial.PUBLICADO)
				.map(AvaliacaoResponse::from)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Avaliacao nao encontrada."));
	}

	public AvaliacaoResumoResponse resumo(UUID materialId) {
		verificarPublicado(materialId);
		var totais = avaliacoes.resumir(materialId, StatusMaterial.PUBLICADO);
		long quantidade = totais.getQuantidade();
		BigDecimal media = quantidade == 0 ? null
				: totais.getSoma().divide(BigDecimal.valueOf(quantidade), 2, RoundingMode.HALF_UP);
		return new AvaliacaoResumoResponse(media, quantidade);
	}

	private void verificarPublicado(UUID materialId) {
		if (!materiais.existsByIdAndStatus(materialId, StatusMaterial.PUBLICADO)) { throw materialNaoEncontrado(); }
	}

	private Avaliacao salvar(Avaliacao avaliacao) {
		try { return avaliacoes.saveAndFlush(avaliacao); }
		catch (DataIntegrityViolationException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Nao foi possivel salvar a avaliacao. Verifique duplicidade e referencias.");
		}
	}

	private ResponseStatusException materialNaoEncontrado() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "Material nao encontrado.");
	}
}
