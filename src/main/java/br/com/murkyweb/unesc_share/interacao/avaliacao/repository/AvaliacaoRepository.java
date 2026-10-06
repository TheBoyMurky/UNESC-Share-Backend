package br.com.murkyweb.unesc_share.interacao.avaliacao.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.murkyweb.unesc_share.interacao.avaliacao.domain.Avaliacao;

public interface AvaliacaoRepository extends JpaRepository<Avaliacao, UUID> {

	Optional<Avaliacao> findByUsuarioIdAndMaterialId(UUID usuarioId, UUID materialId);

	boolean existsByUsuarioIdAndMaterialId(UUID usuarioId, UUID materialId);
}
