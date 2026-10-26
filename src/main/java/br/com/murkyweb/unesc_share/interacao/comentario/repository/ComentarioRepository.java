package br.com.murkyweb.unesc_share.interacao.comentario.repository;

import java.util.Optional;
import java.util.Collection;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import br.com.murkyweb.unesc_share.interacao.comentario.domain.Comentario;
import br.com.murkyweb.unesc_share.interacao.comentario.domain.StatusComentario;
import br.com.murkyweb.unesc_share.material.domain.StatusMaterial;

public interface ComentarioRepository extends JpaRepository<Comentario, UUID> {
	boolean existsByIdAndMaterialId(UUID id, UUID materialId);

	Page<Comentario> findAllByMaterialIdAndStatusIn(UUID materialId, Collection<StatusComentario> status, Pageable pageable);

	Page<Comentario> findAllByMaterialIdAndStatus(
			UUID materialId,
			StatusComentario status,
			Pageable pageable
	);

	Page<Comentario> findAllByMaterialIdAndMaterialStatusAndStatus(
			UUID materialId, StatusMaterial materialStatus, StatusComentario status, Pageable pageable);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from Comentario c where c.id = :id and c.material.id = :materialId")
	Optional<Comentario> buscarParaAtualizacao(@Param("id") UUID id, @Param("materialId") UUID materialId);
}
