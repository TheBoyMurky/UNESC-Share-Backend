package br.com.murkyweb.unesc_share.material.service;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import br.com.murkyweb.unesc_share.academico.disciplina.repository.DisciplinaRepository;
import br.com.murkyweb.unesc_share.categoria.repository.CategoriaRepository;
import br.com.murkyweb.unesc_share.compartilhamento.magnet.repository.MagnetRepository;
import br.com.murkyweb.unesc_share.compartilhamento.magnet.dto.MagnetAdicaoRequest;
import br.com.murkyweb.unesc_share.compartilhamento.magnet.service.MagnetService;
import br.com.murkyweb.unesc_share.compartilhamento.torrent.repository.TorrentRepository;
import br.com.murkyweb.unesc_share.compartilhamento.torrent.service.TorrentService;
import br.com.murkyweb.unesc_share.material.domain.Material;
import br.com.murkyweb.unesc_share.material.domain.StatusMaterial;
import br.com.murkyweb.unesc_share.material.dto.MaterialCadastroRequest;
import br.com.murkyweb.unesc_share.material.dto.MaterialCadastroTorrentRequest;
import br.com.murkyweb.unesc_share.material.dto.MaterialDetalheResponse;
import br.com.murkyweb.unesc_share.material.dto.MaterialEdicaoRequest;
import br.com.murkyweb.unesc_share.material.repository.MaterialRepository;
import br.com.murkyweb.unesc_share.security.UsuarioAutenticado;
import br.com.murkyweb.unesc_share.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;

@Service
@PreAuthorize("isAuthenticated()")
@Transactional
@RequiredArgsConstructor
public class MaterialService {

	private final MaterialRepository repository;
	private final UsuarioRepository usuarios;
	private final DisciplinaRepository disciplinas;
	private final CategoriaRepository categorias;
	private final MagnetService magnetService;
	private final MagnetRepository magnets;
	private final TorrentRepository torrents;
	private final TorrentService torrentService;
	private final MaterialQueryService consultas;
	private final UsuarioAutenticado usuario;

	public MaterialDetalheResponse cadastrar(MaterialCadastroRequest request) {
		var material = novoMaterial(request.titulo(), request.descricao(), request.disciplinaId(), request.categoriaId());
		var validados = magnetService.preparar(request.magnetUris());
		salvar(material);
		// A associacao participa da mesma transacao: qualquer falha reverte tambem o Material.
		magnetService.associar(material, validados);
		return consultas.consultar(material.getId());
	}

	public MaterialDetalheResponse cadastrarComTorrent(MaterialCadastroTorrentRequest request, MultipartFile arquivo) {
		var material = novoMaterial(request.titulo(), request.descricao(), request.disciplinaId(), request.categoriaId());
		// O registro so fica visivel apos commit. Validacao, arquivo e associacao usam a mesma transacao.
		salvar(material);
		torrentService.adicionar(material.getId(), arquivo);
		return consultas.consultar(material.getId());
	}

	public MaterialDetalheResponse adicionarMagnets(UUID id, MagnetAdicaoRequest request) {
		var material = buscarProprio(id);
		if (material.getStatus() != StatusMaterial.PENDENTE && material.getStatus() != StatusMaterial.PUBLICADO) {
			throw conflito("Material bloqueado ou removido nao aceita novos Magnets.");
		}
		var validados = magnetService.preparar(request.magnetUris(), id);
		// O lock do Material tambem serializa a contagem entre adicoes concorrentes.
		if (magnets.countByMaterialId(id) > 10 - validados.size()) {
			throw conflito("Limite de 10 Magnets por material atingido.");
		}
		magnetService.associar(material, validados);
		material.alterarStatus(StatusMaterial.PENDENTE);
		salvar(material);
		return consultas.consultar(id);
	}

	public MaterialDetalheResponse atualizar(UUID id, MaterialEdicaoRequest request) {
		var material = buscarProprio(id);
		if (material.getStatus() != StatusMaterial.PENDENTE && material.getStatus() != StatusMaterial.PUBLICADO) {
			throw conflito("Material bloqueado ou removido nao pode ser editado.");
		}
		var disciplina = disciplinas.findById(request.disciplinaId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Disciplina nao encontrada."));
		var categoria = categorias.findById(request.categoriaId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoria nao encontrada."));
		material.atualizar(request.titulo(), request.descricao(), disciplina, categoria);
		material.alterarStatus(StatusMaterial.PENDENTE);
		salvar(material);
		return consultas.consultar(id);
	}

	public void remover(UUID id) {
		var material = buscarProprio(id);
		if (material.getStatus() != StatusMaterial.REMOVIDO) {
			material.alterarStatus(StatusMaterial.REMOVIDO);
			salvar(material);
		}
	}

	@PreAuthorize("hasAnyRole('MODERADOR', 'ADMINISTRADOR')")
	public MaterialDetalheResponse publicar(UUID id) {
		var material = buscarComLock(id);
		if (material.getStatus() != StatusMaterial.PENDENTE) { throw conflito("Somente material pendente pode ser publicado."); }
		if (!magnets.existsByMaterialId(id) && !torrents.existsByMaterialId(id)) {
			throw conflito("Material deve possuir ao menos um mecanismo de compartilhamento para publicar.");
		}
		material.alterarStatus(StatusMaterial.PUBLICADO);
		salvar(material);
		return consultas.consultar(id);
	}

	@PreAuthorize("hasAnyRole('MODERADOR', 'ADMINISTRADOR')")
	public MaterialDetalheResponse bloquear(UUID id) {
		var material = buscarComLock(id);
		if (material.getStatus() == StatusMaterial.REMOVIDO) { throw conflito("Material removido nao pode ser bloqueado."); }
		material.alterarStatus(StatusMaterial.BLOQUEADO);
		salvar(material);
		return consultas.consultar(id);
	}

	private Material buscarProprio(UUID id) {
		var material = buscarComLock(id);
		if (!material.getUsuario().getId().equals(usuario.id())) { throw naoEncontrado(); }
		return material;
	}

	private Material buscarComLock(UUID id) {
		return repository.buscarParaAtualizacao(id).orElseThrow(this::naoEncontrado);
	}

	private void salvar(Material material) {
		try {
			repository.saveAndFlush(material);
		} catch (DataIntegrityViolationException exception) {
			throw conflito("Nao foi possivel salvar o material. Verifique suas referencias.");
		}
	}

	private Material novoMaterial(String titulo, String descricao, UUID disciplinaId, UUID categoriaId) {
		var autor = usuarios.findById(usuario.id())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario nao disponivel."));
		var disciplina = disciplinas.findById(disciplinaId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Disciplina nao encontrada."));
		var categoria = categorias.findById(categoriaId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoria nao encontrada."));
		return new Material(titulo, descricao, autor, disciplina, categoria);
	}

	private ResponseStatusException naoEncontrado() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Material nao encontrado."); }

	private ResponseStatusException conflito(String mensagem) { return new ResponseStatusException(HttpStatus.CONFLICT, mensagem); }
}
