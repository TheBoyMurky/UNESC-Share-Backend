package br.com.murkyweb.unesc_share.config.seed;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.murkyweb.unesc_share.academico.curso.domain.Curso;
import br.com.murkyweb.unesc_share.academico.curso.repository.CursoRepository;
import br.com.murkyweb.unesc_share.academico.disciplina.domain.Disciplina;
import br.com.murkyweb.unesc_share.academico.disciplina.repository.DisciplinaRepository;
import br.com.murkyweb.unesc_share.academico.instituicao.domain.Instituicao;
import br.com.murkyweb.unesc_share.academico.instituicao.repository.InstituicaoRepository;
import br.com.murkyweb.unesc_share.categoria.domain.Categoria;
import br.com.murkyweb.unesc_share.categoria.repository.CategoriaRepository;
import lombok.RequiredArgsConstructor;

/** Carga idempotente da Matriz 6; a limpeza da Matriz 5 e uma operacao manual separada. */
@Service
@RequiredArgsConstructor
public class DadosIniciaisService {

	private static final String NOME_INSTITUICAO = "Universidade do Extremo Sul Catarinense";
	private static final String NOME_CURSO = "Ciência da Computação";
	private static final String DESCRICAO_ANTIGA = "Matriz Curricular 5 — dados iniciais conforme o PDF de referência.";
	private static final String DESCRICAO_CURSO = "Matriz Curricular 6 — Bacharelado (N), conforme docs/GRADE 6.pdf; "
			+ "8 semestres, 150 créditos e 3.200 horas totais (200 de atividades complementares).";
	private static final List<String> CATEGORIAS = List.of(
			"Livros", "Apostilas", "Resumos", "Listas de exercícios", "Provas",
			"Apresentações", "Cursos", "Artigos", "Outros materiais acadêmicos");

	private final InstituicaoRepository instituicaoRepository;
	private final CursoRepository cursoRepository;
	private final DisciplinaRepository disciplinaRepository;
	private final CategoriaRepository categoriaRepository;
	private final CatalogoMatriz6 catalogo;

	@Transactional
	public int popular() {
		var disciplinas = catalogo.carregar();
		var instituicao = instituicaoRepository.findBySiglaIgnoreCase("UNESC")
				.orElseGet(() -> instituicaoRepository.save(new Instituicao(NOME_INSTITUICAO, "UNESC")));
		var curso = cursoRepository.findByInstituicaoIdAndNomeIgnoreCase(instituicao.getId(), NOME_CURSO)
				.orElseGet(() -> cursoRepository.save(new Curso(NOME_CURSO, DESCRICAO_CURSO, instituicao)));
		if (disciplinaRepository.existsByCursoIdAndCodigoStartingWith(curso.getId(), "TEMP-CC-")) {
			throw new IllegalStateException("Ha disciplinas da Matriz 5 neste curso. Execute a limpeza manual "
					+ "documentada em docs/ATUALIZACAO_MATRIZ_6.md antes de carregar a Matriz 6.");
		}
		// Atualizar somente placeholders reconhecidos; preservar alteracoes manuais e UUIDs.
		if (instituicao.getNome().equalsIgnoreCase("UNESC")) {
			instituicao.atualizar(NOME_INSTITUICAO, instituicao.getSigla());
		}
		if (curso.getDescricao() == null || DESCRICAO_ANTIGA.equals(curso.getDescricao())) {
			curso.atualizar(curso.getNome(), DESCRICAO_CURSO, instituicao);
		}

		int criadas = 0;
		for (var dados : disciplinas) {
			if (!disciplinaRepository.existsByCursoIdAndCodigoIgnoreCase(curso.getId(), dados.codigo())) {
				// O novo PDF nao traz ementas: descricao permanece nula, sem copiar a Matriz 5.
				disciplinaRepository.save(new Disciplina(dados.nome(), null, dados.codigo(), curso));
				criadas++;
			}
		}
		for (String nome : CATEGORIAS) {
			if (!categoriaRepository.existsByNomeIgnoreCase(nome)) {
				categoriaRepository.save(new Categoria(nome, null));
			}
		}
		return criadas;
	}
}
