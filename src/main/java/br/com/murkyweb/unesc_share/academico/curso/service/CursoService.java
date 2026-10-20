package br.com.murkyweb.unesc_share.academico.curso.service;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.murkyweb.unesc_share.academico.curso.domain.Curso;
import br.com.murkyweb.unesc_share.academico.curso.dto.CursoRequest;
import br.com.murkyweb.unesc_share.academico.curso.dto.CursoResponse;
import br.com.murkyweb.unesc_share.academico.curso.repository.CursoRepository;
import br.com.murkyweb.unesc_share.academico.disciplina.repository.DisciplinaRepository;
import br.com.murkyweb.unesc_share.academico.instituicao.repository.InstituicaoRepository;

@Service
@PreAuthorize("isAuthenticated()")
@Transactional(readOnly = true)
public class CursoService {

	private final CursoRepository repository;
	private final InstituicaoRepository instituicoes;
	private final DisciplinaRepository disciplinas;

	public CursoService(CursoRepository repository, InstituicaoRepository instituicoes, DisciplinaRepository disciplinas) {
		this.repository = repository;
		this.instituicoes = instituicoes;
		this.disciplinas = disciplinas;
	}

	public Page<CursoResponse> listar(UUID instituicaoId, Pageable pageable) {
		var cursos = instituicaoId == null ? repository.findAll(pageable) : repository.findAllByInstituicaoId(instituicaoId, pageable);
		return cursos.map(CursoResponse::from);
	}

	public CursoResponse consultar(UUID id) {
		return CursoResponse.from(buscar(id));
	}

	@Transactional
	@PreAuthorize("hasRole('ADMINISTRADOR')")
	public CursoResponse cadastrar(CursoRequest request) {
		var instituicao = instituicoes.findById(request.instituicaoId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Instituicao nao encontrada."));
		if (repository.existsByInstituicaoIdAndNomeIgnoreCase(request.instituicaoId(), request.nome())) {
			throw duplicado();
		}
		return salvar(new Curso(request.nome(), request.descricao(), instituicao));
	}

	@Transactional
	@PreAuthorize("hasRole('ADMINISTRADOR')")
	public CursoResponse atualizar(UUID id, CursoRequest request) {
		var curso = buscar(id);
		if (!curso.getInstituicao().getId().equals(request.instituicaoId())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "A instituicao do curso nao pode ser alterada neste incremento.");
		}
		if (repository.existsByInstituicaoIdAndNomeIgnoreCaseAndIdNot(request.instituicaoId(), request.nome(), id)) {
			throw duplicado();
		}
		curso.atualizar(request.nome(), request.descricao(), curso.getInstituicao());
		return salvar(curso);
	}

	@Transactional
	@PreAuthorize("hasRole('ADMINISTRADOR')")
	public void excluir(UUID id) {
		var curso = buscar(id);
		if (disciplinas.existsByCursoId(id)) {
			throw comDisciplinas();
		}
		try {
			repository.delete(curso);
			// A FK tambem protege contra uma disciplina vinculada apos a consulta acima.
			repository.flush();
		} catch (DataIntegrityViolationException exception) {
			throw comDisciplinas();
		}
	}

	private Curso buscar(UUID id) {
		return repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Curso nao encontrado."));
	}

	private CursoResponse salvar(Curso curso) {
		try {
			return CursoResponse.from(repository.saveAndFlush(curso));
		} catch (DataIntegrityViolationException exception) {
			// Uma FK concorrente tambem pode falhar; nao assumir que todo conflito e duplicidade.
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Nao foi possivel salvar o curso. Verifique a instituicao e a duplicidade de nome nela.");
		}
	}

	private ResponseStatusException duplicado() {
		return new ResponseStatusException(HttpStatus.CONFLICT, "Ja existe curso com esse nome na instituicao.");
	}

	private ResponseStatusException comDisciplinas() {
		return new ResponseStatusException(HttpStatus.CONFLICT, "Curso possui disciplinas vinculadas e nao pode ser excluido.");
	}
}
