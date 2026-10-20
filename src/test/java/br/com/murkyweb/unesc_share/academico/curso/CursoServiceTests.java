package br.com.murkyweb.unesc_share.academico.curso;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import br.com.murkyweb.unesc_share.academico.curso.domain.Curso;
import br.com.murkyweb.unesc_share.academico.curso.dto.CursoRequest;
import br.com.murkyweb.unesc_share.academico.curso.repository.CursoRepository;
import br.com.murkyweb.unesc_share.academico.curso.service.CursoService;
import br.com.murkyweb.unesc_share.academico.disciplina.repository.DisciplinaRepository;
import br.com.murkyweb.unesc_share.academico.instituicao.domain.Instituicao;
import br.com.murkyweb.unesc_share.academico.instituicao.repository.InstituicaoRepository;

class CursoServiceTests {

	private CursoRepository repository;
	private InstituicaoRepository instituicoes;
	private DisciplinaRepository disciplinas;
	private CursoService service;
	private Instituicao instituicao;

	@BeforeEach
	void configurar() {
		repository = mock(CursoRepository.class);
		instituicoes = mock(InstituicaoRepository.class);
		disciplinas = mock(DisciplinaRepository.class);
		service = new CursoService(repository, instituicoes, disciplinas);
		instituicao = new Instituicao("Instituicao", "SIGLA");
		ReflectionTestUtils.setField(instituicao, "id", UUID.randomUUID());
	}

	@Test
	void conflitoDeConstraintNoCadastroRetorna409SemExporSql() {
		when(instituicoes.findById(instituicao.getId())).thenReturn(Optional.of(instituicao));
		when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("SQL interno sensivel"));
		var exception = assertThrows(ResponseStatusException.class,
				() -> service.cadastrar(new CursoRequest("Curso", null, instituicao.getId())));
		assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
		assertEquals("Nao foi possivel salvar o curso. Verifique a instituicao e a duplicidade de nome nela.", exception.getReason());
		assertNull(exception.getCause());
	}

	@Test
	void conflitoDeConstraintNaEdicaoRetorna409() {
		UUID id = UUID.randomUUID();
		when(repository.findById(id)).thenReturn(Optional.of(new Curso("Curso", null, instituicao)));
		when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("constraint"));
		var exception = assertThrows(ResponseStatusException.class,
				() -> service.atualizar(id, new CursoRequest("Outro Nome", null, instituicao.getId())));
		assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
		assertNull(exception.getCause());
	}

	@Test
	void fkConcorrenteNaExclusaoRetorna409SemExcluirDisciplinas() {
		UUID id = UUID.randomUUID();
		var curso = new Curso("Curso", null, instituicao);
		when(repository.findById(id)).thenReturn(Optional.of(curso));
		when(disciplinas.existsByCursoId(id)).thenReturn(false);
		doThrow(new DataIntegrityViolationException("fk_disciplina_curso")).when(repository).flush();
		var exception = assertThrows(ResponseStatusException.class, () -> service.excluir(id));
		assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
		assertEquals("Curso possui disciplinas vinculadas e nao pode ser excluido.", exception.getReason());
		verify(repository).delete(curso);
		verify(repository).flush();
		verify(disciplinas, never()).delete(any());
		verifyNoInteractions(instituicoes);
	}

	@Test
	void naoTentaExcluirQuandoJaHaDisciplinas() {
		UUID id = UUID.randomUUID();
		when(repository.findById(id)).thenReturn(Optional.of(new Curso("Curso", null, instituicao)));
		when(disciplinas.existsByCursoId(id)).thenReturn(true);
		assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, () -> service.excluir(id)).getStatusCode());
		verify(repository, never()).delete(any());
		verify(repository, never()).flush();
	}

	@Test
	void listagemUsaConsultaPaginadaComOuSemFiltroEDevolveDto() {
		var pageable = PageRequest.of(0, 20, Sort.by("nome", "id"));
		var page = new PageImpl<>(List.of(new Curso("Curso", "Descricao", instituicao)), pageable, 1);
		when(repository.findAll(pageable)).thenReturn(page);
		when(repository.findAllByInstituicaoId(instituicao.getId(), pageable)).thenReturn(page);
		for (var resultado : List.of(service.listar(null, pageable), service.listar(instituicao.getId(), pageable))) {
			assertEquals(1, resultado.getTotalElements());
			assertEquals(pageable, resultado.getPageable());
			assertEquals("Curso", resultado.getContent().getFirst().nome());
			assertEquals(instituicao.getId(), resultado.getContent().getFirst().instituicaoId());
		}
		verify(repository).findAll(pageable);
		verify(repository).findAllByInstituicaoId(instituicao.getId(), pageable);
		verifyNoInteractions(instituicoes, disciplinas);
	}

	@Test
	void transferenciaERecusadaAntesDeMudarNomeOuSalvarMesmoSemDisciplinas() {
		UUID id = UUID.randomUUID();
		var curso = new Curso("Nome original", "Descricao original", instituicao);
		when(repository.findById(id)).thenReturn(Optional.of(curso));
		assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
				() -> service.atualizar(id, new CursoRequest("Nome alterado", null, UUID.randomUUID()))).getStatusCode());
		assertEquals("Nome original", curso.getNome());
		assertEquals("Descricao original", curso.getDescricao());
		assertSame(instituicao, curso.getInstituicao());
		verify(repository, never()).saveAndFlush(any());
		verifyNoInteractions(instituicoes, disciplinas);
	}
}
