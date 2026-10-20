package br.com.murkyweb.unesc_share.academico.disciplina;

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
import br.com.murkyweb.unesc_share.academico.curso.repository.CursoRepository;
import br.com.murkyweb.unesc_share.academico.disciplina.domain.Disciplina;
import br.com.murkyweb.unesc_share.academico.disciplina.dto.DisciplinaRequest;
import br.com.murkyweb.unesc_share.academico.disciplina.repository.DisciplinaRepository;
import br.com.murkyweb.unesc_share.academico.disciplina.service.DisciplinaService;
import br.com.murkyweb.unesc_share.academico.instituicao.domain.Instituicao;
import br.com.murkyweb.unesc_share.material.domain.Material;
import br.com.murkyweb.unesc_share.material.repository.MaterialRepository;

class DisciplinaServiceTests {

	private DisciplinaRepository repository;
	private CursoRepository cursos;
	private MaterialRepository materiais;
	private DisciplinaService service;
	private Curso curso;

	@BeforeEach
	void configurar() {
		repository = mock(DisciplinaRepository.class);
		cursos = mock(CursoRepository.class);
		materiais = mock(MaterialRepository.class);
		service = new DisciplinaService(repository, cursos, materiais);
		curso = new Curso("Curso", null, new Instituicao("Instituicao", "SIGLA"));
		ReflectionTestUtils.setField(curso, "id", UUID.randomUUID());
	}

	@Test
	void conflitoDeConstraintNoCadastroRetorna409SemExporSql() {
		when(cursos.findById(curso.getId())).thenReturn(Optional.of(curso));
		when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("SQL interno sensivel"));
		var exception = assertThrows(ResponseStatusException.class,
				() -> service.cadastrar(new DisciplinaRequest("Disciplina", null, "COD-01", curso.getId())));
		assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
		assertEquals("Nao foi possivel salvar a disciplina. Verifique o curso e a duplicidade de codigo nele.", exception.getReason());
		assertNull(exception.getCause());
	}

	@Test
	void conflitoDeConstraintNaEdicaoRetorna409() {
		UUID id = UUID.randomUUID();
		when(repository.findById(id)).thenReturn(Optional.of(new Disciplina("Disciplina", null, "COD-01", curso)));
		when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("constraint"));
		var exception = assertThrows(ResponseStatusException.class,
				() -> service.atualizar(id, new DisciplinaRequest("Nome Editado", null, "COD-02", curso.getId())));
		assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
		assertNull(exception.getCause());
	}

	@Test
	void fkConcorrenteNaExclusaoRetorna409SemExcluirMateriais() {
		UUID id = UUID.randomUUID();
		var disciplina = new Disciplina("Disciplina", null, "COD-01", curso);
		when(repository.findById(id)).thenReturn(Optional.of(disciplina));
		when(materiais.existsByDisciplinaId(id)).thenReturn(false);
		doThrow(new DataIntegrityViolationException("fk_material_disciplina")).when(repository).flush();
		var exception = assertThrows(ResponseStatusException.class, () -> service.excluir(id));
		assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
		assertEquals("Disciplina possui materiais vinculados e nao pode ser excluida.", exception.getReason());
		verify(repository).delete(disciplina);
		verify(repository).flush();
		verify(materiais, never()).delete(any(Material.class));
		verifyNoInteractions(cursos);
	}

	@Test
	void naoTentaExcluirQuandoHaQualquerMaterialVinculado() {
		UUID id = UUID.randomUUID();
		when(repository.findById(id)).thenReturn(Optional.of(new Disciplina("Disciplina", null, "COD-01", curso)));
		when(materiais.existsByDisciplinaId(id)).thenReturn(true);
		assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, () -> service.excluir(id)).getStatusCode());
		verify(repository, never()).delete(any());
		verify(repository, never()).flush();
	}

	@Test
	void listagemUsaConsultaPaginadaComOuSemFiltroEDevolveDto() {
		var pageable = PageRequest.of(0, 20, Sort.by("nome", "id"));
		var page = new PageImpl<>(List.of(new Disciplina("Disciplina", "Descricao", "COD-01", curso)), pageable, 1);
		when(repository.findAll(pageable)).thenReturn(page);
		when(repository.findAllByCursoId(curso.getId(), pageable)).thenReturn(page);
		for (var resultado : List.of(service.listar(null, pageable), service.listar(curso.getId(), pageable))) {
			assertEquals(1, resultado.getTotalElements());
			assertEquals(pageable, resultado.getPageable());
			assertEquals("Disciplina", resultado.getContent().getFirst().nome());
			assertEquals("COD-01", resultado.getContent().getFirst().codigo());
			assertEquals(curso.getId(), resultado.getContent().getFirst().cursoId());
		}
		verify(repository).findAll(pageable);
		verify(repository).findAllByCursoId(curso.getId(), pageable);
		verifyNoInteractions(cursos, materiais);
	}

	@Test
	void transferenciaERecusadaAntesDeMudarCamposMesmoSemMateriais() {
		UUID id = UUID.randomUUID();
		var disciplina = new Disciplina("Nome original", "Descricao original", "COD-01", curso);
		when(repository.findById(id)).thenReturn(Optional.of(disciplina));
		assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
				() -> service.atualizar(id, new DisciplinaRequest("Nome alterado", null, "COD-02", UUID.randomUUID()))).getStatusCode());
		assertEquals("Nome original", disciplina.getNome());
		assertEquals("Descricao original", disciplina.getDescricao());
		assertEquals("COD-01", disciplina.getCodigo());
		assertSame(curso, disciplina.getCurso());
		verify(repository, never()).saveAndFlush(any());
		verifyNoInteractions(cursos, materiais);
	}

	@Test
	void dtoNormalizaCamposSemRestringirCodigoAFormatoNumerico() {
		var request = new DisciplinaRequest("  Disciplina  ", " ", " ab-001 ", curso.getId());
		assertEquals("Disciplina", request.nome());
		assertNull(request.descricao());
		assertEquals("AB-001", request.codigo());
		assertEquals("00123", new DisciplinaRequest("Disciplina", null, "00123", curso.getId()).codigo());
	}
}
