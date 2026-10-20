package br.com.murkyweb.unesc_share.academico.curso.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import br.com.murkyweb.unesc_share.academico.curso.domain.Curso;

public interface CursoRepository extends JpaRepository<Curso, UUID> {

	List<Curso> findAllByInstituicaoIdOrderByNomeAsc(UUID instituicaoId);

	Page<Curso> findAllByInstituicaoId(UUID instituicaoId, Pageable pageable);

	boolean existsByInstituicaoId(UUID instituicaoId);

	boolean existsByInstituicaoIdAndNomeIgnoreCase(UUID instituicaoId, String nome);

	boolean existsByInstituicaoIdAndNomeIgnoreCaseAndIdNot(UUID instituicaoId, String nome, UUID id);

	Optional<Curso> findByInstituicaoIdAndNomeIgnoreCase(UUID instituicaoId, String nome);
}
