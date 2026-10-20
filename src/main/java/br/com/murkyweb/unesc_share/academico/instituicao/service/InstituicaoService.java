package br.com.murkyweb.unesc_share.academico.instituicao.service;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.murkyweb.unesc_share.academico.curso.repository.CursoRepository;
import br.com.murkyweb.unesc_share.academico.instituicao.domain.Instituicao;
import br.com.murkyweb.unesc_share.academico.instituicao.dto.InstituicaoRequest;
import br.com.murkyweb.unesc_share.academico.instituicao.dto.InstituicaoResponse;
import br.com.murkyweb.unesc_share.academico.instituicao.repository.InstituicaoRepository;

@Service
@PreAuthorize("isAuthenticated()")
@Transactional(readOnly = true)
public class InstituicaoService {

	private final InstituicaoRepository repository;
	private final CursoRepository cursos;

	public InstituicaoService(InstituicaoRepository repository, CursoRepository cursos) {
		this.repository = repository;
		this.cursos = cursos;
	}

	public Page<InstituicaoResponse> listar(Pageable pageable) {
		return repository.findAll(pageable).map(InstituicaoResponse::from);
	}

	public InstituicaoResponse consultar(UUID id) {
		return InstituicaoResponse.from(buscar(id));
	}

	@Transactional
	@PreAuthorize("hasRole('ADMINISTRADOR')")
	public InstituicaoResponse cadastrar(InstituicaoRequest request) {
		if (repository.existsByNomeIgnoreCase(request.nome()) || repository.existsBySiglaIgnoreCase(request.sigla())) {
			throw duplicada();
		}
		return salvar(new Instituicao(request.nome(), request.sigla()));
	}

	@Transactional
	@PreAuthorize("hasRole('ADMINISTRADOR')")
	public InstituicaoResponse atualizar(UUID id, InstituicaoRequest request) {
		var instituicao = buscar(id);
		if (repository.existsByNomeIgnoreCaseAndIdNot(request.nome(), id)
				|| repository.existsBySiglaIgnoreCaseAndIdNot(request.sigla(), id)) {
			throw duplicada();
		}
		instituicao.atualizar(request.nome(), request.sigla());
		return salvar(instituicao);
	}

	@Transactional
	@PreAuthorize("hasRole('ADMINISTRADOR')")
	public void excluir(UUID id) {
		var instituicao = buscar(id);
		if (cursos.existsByInstituicaoId(id)) {
			throw comCursos();
		}
		try {
			repository.delete(instituicao);
			// A FK protege tambem contra um curso vinculado apos a consulta acima.
			repository.flush();
		} catch (DataIntegrityViolationException exception) {
			throw comCursos();
		}
	}

	private Instituicao buscar(UUID id) {
		return repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Instituicao nao encontrada."));
	}

	private InstituicaoResponse salvar(Instituicao instituicao) {
		try {
			return InstituicaoResponse.from(repository.saveAndFlush(instituicao));
		} catch (DataIntegrityViolationException exception) {
			// As constraints existentes continuam sendo a ultima protecao de integridade.
			throw duplicada();
		}
	}

	private ResponseStatusException duplicada() {
		return new ResponseStatusException(HttpStatus.CONFLICT, "Ja existe instituicao com esse nome ou sigla.");
	}

	private ResponseStatusException comCursos() {
		return new ResponseStatusException(HttpStatus.CONFLICT, "Instituicao possui cursos vinculados e nao pode ser excluida.");
	}
}
