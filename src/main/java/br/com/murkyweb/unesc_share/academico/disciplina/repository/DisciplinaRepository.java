package br.com.murkyweb.unesc_share.academico.disciplina.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import br.com.murkyweb.unesc_share.academico.disciplina.domain.Disciplina;

public interface DisciplinaRepository extends JpaRepository<Disciplina, UUID> {

	List<Disciplina> findAllByCursoIdOrderByNomeAsc(UUID cursoId);

	Page<Disciplina> findAllByCursoId(UUID cursoId, Pageable pageable);

	boolean existsByCursoId(UUID cursoId);

	boolean existsByCursoIdAndCodigoIgnoreCase(UUID cursoId, String codigo);

	boolean existsByCursoIdAndCodigoIgnoreCaseAndIdNot(UUID cursoId, String codigo, UUID id);

	boolean existsByCursoIdAndCodigoStartingWith(UUID cursoId, String prefixo);

	Optional<Disciplina> findByCursoIdAndCodigoIgnoreCase(UUID cursoId, String codigo);

	Optional<Disciplina> findByCursoIdAndNomeIgnoreCase(UUID cursoId, String nome);
}
