package br.com.murkyweb.unesc_share.academico.disciplina.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.murkyweb.unesc_share.academico.disciplina.domain.Disciplina;

public interface DisciplinaRepository extends JpaRepository<Disciplina, UUID> {

	List<Disciplina> findAllByCursoIdOrderByNomeAsc(UUID cursoId);

	boolean existsByCursoIdAndCodigoIgnoreCase(UUID cursoId, String codigo);
}
