package br.com.murkyweb.unesc_share.interacao.denuncia.repository;

import java.util.UUID;
import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.murkyweb.unesc_share.interacao.denuncia.domain.Denuncia;
import br.com.murkyweb.unesc_share.interacao.denuncia.domain.StatusDenuncia;
import jakarta.persistence.LockModeType;

public interface DenunciaRepository extends JpaRepository<Denuncia, UUID> {
	boolean existsByUsuarioIdAndMaterialIdAndStatusIn(UUID usuarioId, UUID materialId, Collection<StatusDenuncia> status);

	Optional<Denuncia> findByIdAndUsuarioId(UUID id, UUID usuarioId);

	Page<Denuncia> findAllByUsuarioId(UUID usuarioId, Pageable pageable);

	Page<Denuncia> findAllByStatus(StatusDenuncia status, Pageable pageable);

	Page<Denuncia> findAllByMaterialId(UUID materialId, Pageable pageable);

	@Query("select d.material.id from Denuncia d where d.id = :id")
	Optional<UUID> buscarMaterialId(@Param("id") UUID id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select d from Denuncia d where d.id = :id and d.material.id = :materialId")
	Optional<Denuncia> buscarParaAtualizacao(@Param("id") UUID id, @Param("materialId") UUID materialId);
}
