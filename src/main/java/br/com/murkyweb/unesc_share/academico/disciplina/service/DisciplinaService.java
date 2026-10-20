package br.com.murkyweb.unesc_share.academico.disciplina.service;

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
import br.com.murkyweb.unesc_share.academico.disciplina.domain.Disciplina;
import br.com.murkyweb.unesc_share.academico.disciplina.dto.DisciplinaRequest;
import br.com.murkyweb.unesc_share.academico.disciplina.dto.DisciplinaResponse;
import br.com.murkyweb.unesc_share.academico.disciplina.repository.DisciplinaRepository;
import br.com.murkyweb.unesc_share.material.repository.MaterialRepository;

@Service
@PreAuthorize("isAuthenticated()")
@Transactional(readOnly = true)
public class DisciplinaService {

	private final DisciplinaRepository repository;
	private final CursoRepository cursos;
	private final MaterialRepository materiais;

	public DisciplinaService(DisciplinaRepository repository, CursoRepository cursos, MaterialRepository materiais) {
		this.repository = repository;
		this.cursos = cursos;
		this.materiais = materiais;
	}

	public Page<DisciplinaResponse> listar(UUID cursoId, Pageable pageable) {
		var disciplinas = cursoId == null ? repository.findAll(pageable) : repository.findAllByCursoId(cursoId, pageable);
		return disciplinas.map(DisciplinaResponse::from);
	}

	public DisciplinaResponse consultar(UUID id) {
		return DisciplinaResponse.from(buscar(id));
	}

	@Transactional
	@PreAuthorize("hasRole('ADMINISTRADOR')")
	public DisciplinaResponse cadastrar(DisciplinaRequest request) {
		var curso = cursos.findById(request.cursoId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Curso nao encontrado."));
		if (repository.existsByCursoIdAndCodigoIgnoreCase(request.cursoId(), request.codigo())) {
			throw duplicada();
		}
		return salvar(new Disciplina(request.nome(), request.descricao(), request.codigo(), curso));
	}

	@Transactional
	@PreAuthorize("hasRole('ADMINISTRADOR')")
	public DisciplinaResponse atualizar(UUID id, DisciplinaRequest request) {
		var disciplina = buscar(id);
		if (!disciplina.getCurso().getId().equals(request.cursoId())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "O curso da disciplina nao pode ser alterado neste incremento.");
		}
		if (repository.existsByCursoIdAndCodigoIgnoreCaseAndIdNot(request.cursoId(), request.codigo(), id)) {
			throw duplicada();
		}
		disciplina.atualizar(request.nome(), request.descricao(), request.codigo(), disciplina.getCurso());
		return salvar(disciplina);
	}

	@Transactional
	@PreAuthorize("hasRole('ADMINISTRADOR')")
	public void excluir(UUID id) {
		var disciplina = buscar(id);
		// Inclui materiais REMOVIDO: a exclusao logica preserva auditoria e referencias.
		if (materiais.existsByDisciplinaId(id)) {
			throw comMateriais();
		}
		try {
			repository.delete(disciplina);
			// A FK protege tambem contra um material vinculado apos a consulta acima.
			repository.flush();
		} catch (DataIntegrityViolationException exception) {
			throw comMateriais();
		}
	}

	private Disciplina buscar(UUID id) {
		return repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Disciplina nao encontrada."));
	}

	private DisciplinaResponse salvar(Disciplina disciplina) {
		try {
			return DisciplinaResponse.from(repository.saveAndFlush(disciplina));
		} catch (DataIntegrityViolationException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Nao foi possivel salvar a disciplina. Verifique o curso e a duplicidade de codigo nele.");
		}
	}

	private ResponseStatusException duplicada() {
		return new ResponseStatusException(HttpStatus.CONFLICT, "Ja existe disciplina com esse codigo no curso.");
	}

	private ResponseStatusException comMateriais() {
		return new ResponseStatusException(HttpStatus.CONFLICT, "Disciplina possui materiais vinculados e nao pode ser excluida.");
	}
}
