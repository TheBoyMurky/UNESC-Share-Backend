package br.com.murkyweb.unesc_share.config.seed;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Catalogo conferido contra docs/GRADE 6.pdf; nao contem ementas. */
@Component
@RequiredArgsConstructor
public class CatalogoMatriz6 {

	private static final String ARQUIVO = "dados-iniciais/unesc-ciencia-computacao-matriz6.json";
	private final ObjectMapper objectMapper;
	private final Validator validator;

	public List<DisciplinaInicial> carregar() {
		try (var input = new ClassPathResource(ARQUIVO).getInputStream()) {
			var disciplinas = List.of(objectMapper.readValue(input, DisciplinaInicial[].class));
			var codigos = new HashSet<String>();
			var nomes = new HashSet<String>();
			for (var disciplina : disciplinas) {
				if (!validator.validate(disciplina).isEmpty()
						|| !codigos.add(disciplina.codigo()) || !nomes.add(disciplina.nome())) {
					throw new IllegalStateException("Registro invalido ou duplicado no catalogo da Matriz 6.");
				}
				if (disciplina.semestre() == null) {
					if (disciplina.creditos() != null || disciplina.horas() != 0) {
						throw new IllegalStateException("Eletiva sem dados de semestre/creditos deve manter horas como listadas no PDF.");
					}
				} else if (disciplina.creditos() == null || disciplina.horas() != disciplina.creditos() * 20) {
					throw new IllegalStateException("Creditos/horas inconsistentes na Matriz 6.");
				}
			}
			var semestrais = disciplinas.stream().filter(d -> d.semestre() != null).toList();
			if (disciplinas.size() != 59 || semestrais.size() != 48
					|| semestrais.stream().mapToInt(DisciplinaInicial::creditos).sum() != 150
					|| semestrais.stream().mapToInt(DisciplinaInicial::horas).sum() != 3000) {
				throw new IllegalStateException("A Matriz 6 deve conter 48 componentes semestrais e 11 eletivas, 150 creditos e 3000 horas de disciplinas.");
			}
			return disciplinas;
		} catch (IOException | JacksonException exception) {
			throw new IllegalStateException("Nao foi possivel ler o catalogo inicial da Matriz 6.", exception);
		}
	}

	/** Semestre, creditos e horas sao referencia; nao alteram a entidade JPA nesta etapa. */
	public record DisciplinaInicial(
			@NotBlank @Pattern(regexp = "\\d{5}") String codigo,
			@NotBlank @Size(max = 150) String nome,
			@Min(1) @Max(8) Integer semestre,
			@Min(3) @Max(6) Integer creditos,
			@NotNull @Min(0) @Max(120) Integer horas) {
	}
}
