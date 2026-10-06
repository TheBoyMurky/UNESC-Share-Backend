package br.com.murkyweb.unesc_share.academico.curso.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.murkyweb.unesc_share.academico.curso.domain.Curso;

public interface CursoRepository extends JpaRepository<Curso, UUID> {

	List<Curso> findAllByInstituicaoIdOrderByNomeAsc(UUID instituicaoId);

	boolean existsByInstituicaoIdAndNomeIgnoreCase(UUID instituicaoId, String nome);

	Optional<Curso> findByInstituicaoIdAndNomeIgnoreCase(UUID instituicaoId, String nome);
}
