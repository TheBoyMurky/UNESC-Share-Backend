package br.com.murkyweb.unesc_share.academico.instituicao;

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
import org.springframework.web.server.ResponseStatusException;

import br.com.murkyweb.unesc_share.academico.curso.repository.CursoRepository;
import br.com.murkyweb.unesc_share.academico.instituicao.domain.Instituicao;
import br.com.murkyweb.unesc_share.academico.instituicao.dto.InstituicaoRequest;
import br.com.murkyweb.unesc_share.academico.instituicao.repository.InstituicaoRepository;
import br.com.murkyweb.unesc_share.academico.instituicao.service.InstituicaoService;

class InstituicaoServiceTests {

	private InstituicaoRepository repository;
	private CursoRepository cursos;
	private InstituicaoService service;

	@BeforeEach
	void configurar() {
		repository = mock(InstituicaoRepository.class);
		cursos = mock(CursoRepository.class);
		service = new InstituicaoService(repository, cursos);
	}

	@Test
	void traduzConflitoDeConstraintNoCadastroSemExporDetalhesSql() {
		when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("SQL interno sensivel"));
		var exception = assertThrows(ResponseStatusException.class,
				() -> service.cadastrar(new InstituicaoRequest("Instituicao", "SIGLA")));
		assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
		assertEquals("Ja existe instituicao com esse nome ou sigla.", exception.getReason());
		assertNull(exception.getCause());
	}

	@Test
	void traduzConflitoDeConstraintNaEdicao() {
		UUID id = UUID.randomUUID();
		when(repository.findById(id)).thenReturn(Optional.of(new Instituicao("Original", "ORI")));
		when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("constraint"));
		var exception = assertThrows(ResponseStatusException.class,
				() -> service.atualizar(id, new InstituicaoRequest("Novo nome", "NOVA")));
		assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
	}

	@Test
	void traduzFkConcorrenteNaExclusaoPara409() {
		UUID id = UUID.randomUUID();
		var instituicao = new Instituicao("Instituicao", "SIGLA");
		when(repository.findById(id)).thenReturn(Optional.of(instituicao));
		when(cursos.existsByInstituicaoId(id)).thenReturn(false);
		doThrow(new DataIntegrityViolationException("fk_curso_instituicao")).when(repository).flush();
		var exception = assertThrows(ResponseStatusException.class, () -> service.excluir(id));
		assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
		assertEquals("Instituicao possui cursos vinculados e nao pode ser excluida.", exception.getReason());
		verify(repository).delete(instituicao);
		verify(repository).flush();
		verify(cursos, never()).delete(any());
	}

	@Test
	void bloqueiaExclusaoAntesDeRemoverQuandoHaCurso() {
		UUID id = UUID.randomUUID();
		when(repository.findById(id)).thenReturn(Optional.of(new Instituicao("Instituicao", "SIGLA")));
		when(cursos.existsByInstituicaoId(id)).thenReturn(true);
		assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, () -> service.excluir(id)).getStatusCode());
		verify(repository, never()).delete(any());
		verify(repository, never()).flush();
	}

	@Test
	void listagemPreservaPaginacaoEMapeiaParaDto() {
		var pageable = PageRequest.of(0, 20, Sort.by("nome", "id"));
		when(repository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(new Instituicao("Instituicao", "SIGLA")), pageable, 1));
		var page = service.listar(pageable);
		assertEquals(1, page.getTotalElements());
		assertEquals("Instituicao", page.getContent().getFirst().nome());
		assertEquals("SIGLA", page.getContent().getFirst().sigla());
		assertEquals(pageable, page.getPageable());
	}
}
