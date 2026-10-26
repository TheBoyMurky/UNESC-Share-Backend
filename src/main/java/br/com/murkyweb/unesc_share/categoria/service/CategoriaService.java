package br.com.murkyweb.unesc_share.categoria.service;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.murkyweb.unesc_share.categoria.domain.Categoria;
import br.com.murkyweb.unesc_share.categoria.dto.CategoriaRequest;
import br.com.murkyweb.unesc_share.categoria.dto.CategoriaResponse;
import br.com.murkyweb.unesc_share.categoria.repository.CategoriaRepository;
import br.com.murkyweb.unesc_share.material.repository.MaterialRepository;

@Service
@PreAuthorize("isAuthenticated()")
@Transactional(readOnly = true)
public class CategoriaService {

	private final CategoriaRepository repository;
	private final MaterialRepository materiais;

	public CategoriaService(CategoriaRepository repository, MaterialRepository materiais) {
		this.repository = repository;
		this.materiais = materiais;
	}

	public Page<CategoriaResponse> listar(Pageable pageable) {
		return repository.findAll(pageable).map(CategoriaResponse::from);
	}

	public CategoriaResponse consultar(UUID id) {
		return CategoriaResponse.from(buscar(id));
	}

	@Transactional
	@PreAuthorize("hasRole('ADMINISTRADOR')")
	public CategoriaResponse cadastrar(CategoriaRequest request) {
		if (repository.existsByNomeIgnoreCase(request.nome())) {
			throw duplicada();
		}
		return salvar(new Categoria(request.nome(), request.descricao()));
	}

	@Transactional
	@PreAuthorize("hasRole('ADMINISTRADOR')")
	public CategoriaResponse atualizar(UUID id, CategoriaRequest request) {
		var categoria = buscar(id);
		if (repository.existsByNomeIgnoreCaseAndIdNot(request.nome(), id)) {
			throw duplicada();
		}
		categoria.atualizar(request.nome(), request.descricao());
		return salvar(categoria);
	}

	@Transactional
	@PreAuthorize("hasRole('ADMINISTRADOR')")
	public void excluir(UUID id) {
		var categoria = buscar(id);
		// Inclui materiais REMOVIDO, que continuam mantendo suas referencias.
		if (materiais.existsByCategoriaId(id)) {
			throw comMateriais();
		}
		try {
			repository.delete(categoria);
			// A FK tambem protege um material vinculado depois da consulta acima.
			repository.flush();
		} catch (DataIntegrityViolationException exception) {
			throw comMateriais();
		}
	}

	private Categoria buscar(UUID id) {
		return repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoria nao encontrada."));
	}

	private CategoriaResponse salvar(Categoria categoria) {
		try {
			return CategoriaResponse.from(repository.saveAndFlush(categoria));
		} catch (DataIntegrityViolationException exception) {
			throw duplicada();
		}
	}

	private ResponseStatusException duplicada() {
		return new ResponseStatusException(HttpStatus.CONFLICT, "Ja existe categoria com esse nome.");
	}

	private ResponseStatusException comMateriais() {
		return new ResponseStatusException(HttpStatus.CONFLICT, "Categoria possui materiais vinculados e nao pode ser excluida.");
	}
}
