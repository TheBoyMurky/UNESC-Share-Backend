package br.com.murkyweb.unesc_share.config.seed;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.murkyweb.unesc_share.academico.curso.domain.Curso;
import br.com.murkyweb.unesc_share.academico.curso.repository.CursoRepository;
import br.com.murkyweb.unesc_share.academico.disciplina.domain.Disciplina;
import br.com.murkyweb.unesc_share.academico.disciplina.repository.DisciplinaRepository;
import br.com.murkyweb.unesc_share.academico.instituicao.domain.Instituicao;
import br.com.murkyweb.unesc_share.academico.instituicao.repository.InstituicaoRepository;
import br.com.murkyweb.unesc_share.categoria.domain.Categoria;
import br.com.murkyweb.unesc_share.categoria.repository.CategoriaRepository;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import tools.jackson.databind.json.JsonMapper;

class DadosIniciaisServiceUnitTests {

	private final InstituicaoRepository instituicoes = mock(InstituicaoRepository.class);
	private final CursoRepository cursos = mock(CursoRepository.class);
	private final DisciplinaRepository disciplinas = mock(DisciplinaRepository.class);
	private final CategoriaRepository categorias = mock(CategoriaRepository.class);
	private final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
	private final Instituicao instituicao = new Instituicao("UNESC", "UNESC");
	private final Curso curso = new Curso("Ciência da Computação",
			"Matriz Curricular 5 — dados iniciais conforme o PDF de referência.", instituicao);
	private DadosIniciaisService service;

	@BeforeEach
	void configurar() {
		ReflectionTestUtils.setField(instituicao, "id", UUID.randomUUID());
		ReflectionTestUtils.setField(curso, "id", UUID.randomUUID());
		when(instituicoes.findBySiglaIgnoreCase("UNESC")).thenReturn(Optional.of(instituicao));
		when(cursos.findByInstituicaoIdAndNomeIgnoreCase(instituicao.getId(), curso.getNome()))
				.thenReturn(Optional.of(curso));
		service = new DadosIniciaisService(instituicoes, cursos, disciplinas, categorias,
				new CatalogoMatriz6(JsonMapper.builder().build(), factory.getValidator()));
	}

	@AfterEach
	void fecharValidator() { factory.close(); }

	@Test
	void cria59DisciplinasSemEmentasEPreservaUuidsDosPais() {
		UUID instituicaoId = instituicao.getId();
		UUID cursoId = curso.getId();
		assertEquals(59, service.popular());
		var captor = ArgumentCaptor.forClass(Disciplina.class);
		verify(disciplinas, times(59)).save(captor.capture());
		assertTrue(captor.getAllValues().stream().allMatch(d -> d.getDescricao() == null
				&& d.getCodigo().matches("\\d{5}") && d.getCurso() == curso));
		assertEquals("Universidade do Extremo Sul Catarinense", instituicao.getNome());
		assertTrue(curso.getDescricao().contains("Matriz Curricular 6"));
		assertEquals(instituicaoId, instituicao.getId());
		assertEquals(cursoId, curso.getId());
		verify(instituicoes, never()).save(any());
		verify(cursos, never()).save(any());
		verify(categorias, times(9)).save(any());
	}

	@Test
	void bloqueiaMisturaComMatriz5SemExcluirOuAtualizarRegistros() {
		when(disciplinas.existsByCursoIdAndCodigoStartingWith(curso.getId(), "TEMP-CC-"))
				.thenReturn(true);
		assertThrows(IllegalStateException.class, service::popular);
		verify(disciplinas, never()).save(any());
		verify(disciplinas, never()).delete(any());
		verify(disciplinas, never()).deleteAll();
		verifyNoInteractions(categorias);
		assertEquals("UNESC", instituicao.getNome());
		assertTrue(curso.getDescricao().contains("Matriz Curricular 5"));
	}

	@Test
	void reconheceDisciplinaPorCodigoNaoPeloNome() {
		when(disciplinas.existsByCursoIdAndCodigoIgnoreCase(curso.getId(), "27705")).thenReturn(true);
		assertEquals(58, service.popular());
		var captor = ArgumentCaptor.forClass(Disciplina.class);
		verify(disciplinas, times(58)).save(captor.capture());
		assertFalse(captor.getAllValues().stream().anyMatch(d -> d.getCodigo().equals("27705")));
		verify(disciplinas, never()).findByCursoIdAndNomeIgnoreCase(any(), any());
	}

	@Test
	void preservaDenominacaoEDescricaoManuais() {
		instituicao.atualizar("Denominacao ajustada manualmente", "UNESC");
		curso.atualizar(curso.getNome(), "Descricao ajustada manualmente", instituicao);
		service.popular();
		assertEquals("Denominacao ajustada manualmente", instituicao.getNome());
		assertEquals("Descricao ajustada manualmente", curso.getDescricao());
	}

	@Test
	void segundaExecucaoNaoDuplicaDisciplinasOuCategorias() {
		var codigos = new HashSet<String>();
		var nomesCategorias = new HashSet<String>();
		when(disciplinas.existsByCursoIdAndCodigoIgnoreCase(eq(curso.getId()), anyString()))
				.thenAnswer(invocation -> codigos.contains(invocation.getArgument(1, String.class)));
		when(disciplinas.save(any(Disciplina.class))).thenAnswer(invocation -> {
			var disciplina = invocation.getArgument(0, Disciplina.class);
			codigos.add(disciplina.getCodigo());
			return disciplina;
		});
		when(categorias.existsByNomeIgnoreCase(anyString()))
				.thenAnswer(invocation -> nomesCategorias.contains(invocation.getArgument(0, String.class)));
		when(categorias.save(any(Categoria.class))).thenAnswer(invocation -> {
			var categoria = invocation.getArgument(0, Categoria.class);
			nomesCategorias.add(categoria.getNome());
			return categoria;
		});
		assertEquals(59, service.popular());
		assertEquals(0, service.popular());
		verify(disciplinas, times(59)).save(any());
		verify(categorias, times(9)).save(any());
	}
}
