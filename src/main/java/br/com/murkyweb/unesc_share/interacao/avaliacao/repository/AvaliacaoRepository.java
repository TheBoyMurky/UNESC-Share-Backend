package br.com.murkyweb.unesc_share.interacao.avaliacao.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.murkyweb.unesc_share.interacao.avaliacao.domain.Avaliacao;
import br.com.murkyweb.unesc_share.material.domain.StatusMaterial;
import jakarta.persistence.LockModeType;

public interface AvaliacaoRepository extends JpaRepository<Avaliacao, UUID> {

	Optional<Avaliacao> findByUsuarioIdAndMaterialId(UUID usuarioId, UUID materialId);

	boolean existsByUsuarioIdAndMaterialId(UUID usuarioId, UUID materialId);

	Optional<Avaliacao> findByUsuarioIdAndMaterialIdAndMaterialStatus(UUID usuarioId, UUID materialId, StatusMaterial status);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from Avaliacao a where a.usuario.id = :usuarioId and a.material.id = :materialId")
	Optional<Avaliacao> buscarPropriaParaAtualizacao(@Param("usuarioId") UUID usuarioId, @Param("materialId") UUID materialId);

	@Query("select sum(a.nota) as soma, count(a) as quantidade from Avaliacao a "
			+ "where a.material.id = :materialId and a.material.status = :status")
	AvaliacaoTotais resumir(@Param("materialId") UUID materialId, @Param("status") StatusMaterial status);
}
