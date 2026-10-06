package br.com.murkyweb.unesc_share.config.seed;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import br.com.murkyweb.unesc_share.academico.curso.repository.CursoRepository;
import br.com.murkyweb.unesc_share.academico.disciplina.repository.DisciplinaRepository;
import br.com.murkyweb.unesc_share.academico.instituicao.repository.InstituicaoRepository;
import br.com.murkyweb.unesc_share.categoria.repository.CategoriaRepository;
import jakarta.persistence.EntityManager;

@ActiveProfiles("test")
@SpringBootTest(properties = "unesc-share.seed.enabled=false")
@Transactional
class DadosIniciaisServiceTests {

	@Autowired private DadosIniciaisService service;
	@Autowired private InstituicaoRepository instituicoes;
	@Autowired private CursoRepository cursos;
	@Autowired private DisciplinaRepository disciplinas;
	@Autowired private CategoriaRepository categorias;
	@Autowired private EntityManager entityManager;
	@Autowired private ApplicationContext context;

	@Test
	void populaCatalogoSemDuplicarNaSegundaExecucao() {
		service.popular();
		entityManager.flush();
		var instituicao = instituicoes.findBySiglaIgnoreCase("UNESC").orElseThrow();
		var curso = cursos.findByInstituicaoIdAndNomeIgnoreCase(instituicao.getId(), "Ciência da Computação")
				.orElseThrow();
		var catalogo = disciplinas.findAllByCursoIdOrderByNomeAsc(curso.getId());
		assertEquals(59, catalogo.size());
		assertTrue(catalogo.stream().allMatch(d -> d.getId() != null && d.getCodigo().matches("\\d{5}")));
		assertEquals(59, catalogo.stream().map(d -> d.getCodigo()).distinct().count());
		assertEquals("Universidade do Extremo Sul Catarinense", instituicao.getNome());
		assertTrue(disciplinas.existsByCursoIdAndCodigoIgnoreCase(curso.getId(), "27722"));
		assertTrue(disciplinas.existsByCursoIdAndCodigoIgnoreCase(curso.getId(), "27735"));
		assertTrue(disciplinas.existsByCursoIdAndCodigoIgnoreCase(curso.getId(), "29129"));
		assertFalse(disciplinas.existsByCursoIdAndCodigoStartingWith(curso.getId(), "TEMP-CC-"));
		assertTrue(categorias.existsByNomeIgnoreCase("Livros"));
		assertTrue(categorias.existsByNomeIgnoreCase("Outros materiais acadêmicos"));
		long totalInstituicoes = instituicoes.count();
		long totalCursos = cursos.count();
		long totalCategorias = categorias.count();
		long totalDisciplinas = disciplinas.count();
		assertEquals(0, service.popular());
		entityManager.flush();
		assertEquals(totalInstituicoes, instituicoes.count());
		assertEquals(totalCursos, cursos.count());
		assertEquals(totalCategorias, categorias.count());
		assertEquals(totalDisciplinas, disciplinas.count());
	}

	@Test
	void preservaCodigoOficialUuidEEdicoesManuaisAposNovaExecucao() {
		service.popular();
		var instituicao = instituicoes.findBySiglaIgnoreCase("UNESC").orElseThrow();
		var curso = cursos.findByInstituicaoIdAndNomeIgnoreCase(instituicao.getId(), "Ciência da Computação")
				.orElseThrow();
		var disciplina = disciplinas.findByCursoIdAndCodigoIgnoreCase(curso.getId(), "27694")
				.orElseThrow();
		UUID id = disciplina.getId();
		disciplina.atualizar("Nome ajustado manualmente", "Descricao adicionada manualmente", disciplina.getCodigo(), curso);
		entityManager.flush();
		entityManager.clear();
		assertEquals(0, service.popular());
		entityManager.flush();
		entityManager.clear();
		var preservada = disciplinas.findById(id).orElseThrow();
		assertEquals("27694", preservada.getCodigo());
		assertEquals("Nome ajustado manualmente", preservada.getNome());
		assertEquals("Descricao adicionada manualmente", preservada.getDescricao());
		assertEquals(59, disciplinas.findAllByCursoIdOrderByNomeAsc(curso.getId()).size());
	}

	@Test
	void runnerNaoExecutaNoPerfilTest() {
		assertTrue(context.getBeansOfType(DadosIniciaisRunner.class).isEmpty());
	}
}
