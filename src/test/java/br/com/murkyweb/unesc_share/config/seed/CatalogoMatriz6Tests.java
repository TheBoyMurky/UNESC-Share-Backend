package br.com.murkyweb.unesc_share.config.seed;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import tools.jackson.databind.json.JsonMapper;

class CatalogoMatriz6Tests {

	private final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
	private final CatalogoMatriz6 catalogo = new CatalogoMatriz6(JsonMapper.builder().build(), factory.getValidator());

	@AfterEach
	void fecharValidator() { factory.close(); }

	@Test
	void carrega59CodigosOficiaisUnicosSemCodigosTemporarios() {
		var disciplinas = catalogo.carregar();
		assertEquals(59, disciplinas.size());
		assertEquals(59, disciplinas.stream().map(CatalogoMatriz6.DisciplinaInicial::codigo).distinct().count());
		assertTrue(disciplinas.stream().allMatch(d -> d.codigo().matches("\\d{5}")));
		assertEquals("(NCI)-LABORATÓRIO FORMATIVO I: NOSSO LUGAR E O FUTURO", disciplinas.getFirst().nome());
	}

	@Test
	void confereDistribuicaoPorSemestreCreditoseHoras() {
		var disciplinas = catalogo.carregar();
		var quantidades = List.of(5L, 6L, 6L, 6L, 6L, 6L, 7L, 6L);
		var creditos = List.of(16, 18, 18, 18, 19, 18, 22, 21);
		for (int semestre = 1; semestre <= 8; semestre++) {
			int atual = semestre;
			var componentes = disciplinas.stream().filter(d -> Integer.valueOf(atual).equals(d.semestre())).toList();
			assertEquals(quantidades.get(semestre - 1), Long.valueOf(componentes.size()));
			assertEquals(creditos.get(semestre - 1), Integer.valueOf(componentes.stream()
					.mapToInt(CatalogoMatriz6.DisciplinaInicial::creditos).sum()));
		}
		assertEquals(3000, disciplinas.stream().mapToInt(CatalogoMatriz6.DisciplinaInicial::horas).sum());
		assertEquals(7, disciplinas.stream().filter(d -> d.codigo().equals("27731")).findFirst().orElseThrow().semestre());
		assertEquals(6, disciplinas.stream().filter(d -> d.codigo().equals("27733")).findFirst().orElseThrow().creditos());
	}

	@Test
	void distingueOptativasSemestraisDeEletivasSemInferirDados() {
		var disciplinas = catalogo.carregar();
		var eletivas = disciplinas.stream().filter(d -> d.semestre() == null).toList();
		assertEquals(11, eletivas.size());
		assertTrue(eletivas.stream().allMatch(d -> d.creditos() == null && d.horas() == 0));
		assertTrue(disciplinas.stream().anyMatch(d -> d.codigo().equals("27722") && d.semestre() == 6));
		assertTrue(disciplinas.stream().anyMatch(d -> d.codigo().equals("27735") && d.semestre() == 8));
		assertTrue(eletivas.stream().anyMatch(d -> d.codigo().equals("29129") && d.nome().equals("INCLUSÃO E LIBRAS")));
	}

	@Test
	void validacaoNativaRejeitaCodigoTemporarioEFaseInvalida() {
		var validator = factory.getValidator();
		assertFalse(validator.validate(new CatalogoMatriz6.DisciplinaInicial("TEMP-CC-001", "Nome", 1, 3, 60)).isEmpty());
		assertFalse(validator.validate(new CatalogoMatriz6.DisciplinaInicial("27694", "Nome", 9, 3, 60)).isEmpty());
		assertFalse(validator.validate(new CatalogoMatriz6.DisciplinaInicial("27694", "Nome", 1, 3, null)).isEmpty());
	}
}
